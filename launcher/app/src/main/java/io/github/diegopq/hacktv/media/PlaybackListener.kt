package io.github.diegopq.hacktv.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import io.github.diegopq.hacktv.data.RecentItem
import io.github.diegopq.hacktv.data.RecentlyPlayed

/**
 * Android only shares the TV's player sessions (what each app is playing) with a notification listener the
 * user allowed. This one never reads notifications: it only follows the player sessions, to keep
 * [PlayHistory].
 */
class PlaybackListener : NotificationListenerService() {

    private var tracker: PlaybackTracker? = null

    override fun onListenerConnected() {
        tracker?.stop()
        tracker = PlaybackTracker(applicationContext).also { it.start() }
    }

    override fun onListenerDisconnected() {
        tracker?.stop()
        tracker = null
    }

    override fun onDestroy() {
        tracker?.stop()
        tracker = null
        super.onDestroy()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, PlaybackListener::class.java)

        fun isAllowed(context: Context): Boolean = runCatching {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
        }.getOrDefault(false)
    }
}

/**
 * Follows every app's player session. Something counts as played once it has been playing for
 * [MIN_PLAY_MS], so trailers that start by themselves on an app's home screen don't fill the history.
 */
private class PlaybackTracker(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = PlaybackListener.component(context)
    private val watched = HashMap<MediaSession.Token, Watched>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onSessionsChanged(controllers.orEmpty())
    }

    fun start() {
        PlayHistory.load(context)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            onSessionsChanged(manager.getActiveSessions(component))
        }
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        watched.values.forEach { it.release() }
        watched.clear()
    }

    private fun onSessionsChanged(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach { token -> watched.remove(token)?.let { it.save(); it.release() } }
        for (controller in controllers) {
            if (controller.sessionToken in watched || controller.packageName == context.packageName) continue
            // Recently played is only movies and series: YouTube and music players aren't followed.
            if (!RecentlyPlayed.isMoviesOrSeriesApp(controller.packageName)) continue
            val session = Watched(controller)
            watched[controller.sessionToken] = session
            controller.registerCallback(session, handler)
            session.update()
        }
    }

    private inner class Watched(val controller: MediaController) : MediaController.Callback() {
        private var current: RecentItem? = null
        private var art: Bitmap? = null
        private var committed = false
        private var pending = false

        private val commit = Runnable {
            pending = false
            val item = snapshot() ?: return@Runnable
            if (item.id != current?.id || !isPlaying()) return@Runnable
            committed = true
            PlayHistory.record(context, item, art)
            handler.postDelayed(tick, TICK_MS)
        }

        /**
         * While something plays, its position is saved once a minute (in case the app closes without pausing)
         * and it moves back to the front, so resuming something old makes it recent again.
         */
        private val tick = object : Runnable {
            override fun run() {
                if (!committed || !isPlaying()) return
                snapshot()?.takeIf { it.id == current?.id }?.let { PlayHistory.record(context, it, null) }
                handler.postDelayed(this, TICK_MS)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() {
            save()
            release()
            watched.remove(controller.sessionToken)
        }

        fun update() {
            val item = snapshot() ?: return
            if (item.id != current?.id) {
                save()                      // the last position of what was playing before
                handler.removeCallbacks(commit)
                handler.removeCallbacks(tick)
                pending = false
                committed = false
                art = artOf(controller.metadata)
            }
            current = item
            if (isPlaying()) {
                if (committed) {
                    handler.removeCallbacks(tick)
                    handler.postDelayed(tick, TICK_MS)
                } else if (!pending) {
                    pending = true
                    handler.postDelayed(commit, MIN_PLAY_MS)
                }
            } else {
                handler.removeCallbacks(commit)
                pending = false
                save()
            }
        }

        fun save() {
            if (!committed) return
            val item = snapshot()?.takeIf { it.id == current?.id } ?: current ?: return
            PlayHistory.updatePosition(context, item.id, item.positionMs, item.durationMs)
        }

        fun release() {
            handler.removeCallbacks(commit)
            handler.removeCallbacks(tick)
            runCatching { controller.unregisterCallback(this) }
        }

        private fun isPlaying() = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        /** What the session is playing now, or null when it says nothing useful (no title). */
        private fun snapshot(): RecentItem? {
            val metadata = controller.metadata ?: return null
            val title = (metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_TITLE))?.toString()?.trim()
            if (title.isNullOrEmpty()) return null
            val subtitle = (metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_ARTIST))?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0)
            val position = positionOf(controller.playbackState).let { if (duration > 0) it.coerceAtMost(duration) else it }
            val pkg = controller.packageName
            return RecentItem(
                id = "h:$pkg|${RecentlyPlayed.normalize(title)}",
                packageName = pkg,
                title = title,
                subtitle = subtitle,
                imageUri = artUriOf(metadata),
                positionMs = position,
                durationMs = duration,
                lastPlayed = System.currentTimeMillis(),
                fromApp = false,
            )
        }
    }

    private fun positionOf(state: PlaybackState?): Long {
        state ?: return 0
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        return position.coerceAtLeast(0)
    }

    private fun artOf(metadata: MediaMetadata?): Bitmap? = metadata?.let {
        it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    }

    /** Only web addresses: another app's content:// links stop working once its player closes. */
    private fun artUriOf(metadata: MediaMetadata): String? = listOf(
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
    ).firstNotNullOfOrNull { key -> metadata.getString(key)?.takeIf { it.startsWith("http") } }

    companion object {
        const val MIN_PLAY_MS = 45_000L
        const val TICK_MS = 60_000L
    }
}
