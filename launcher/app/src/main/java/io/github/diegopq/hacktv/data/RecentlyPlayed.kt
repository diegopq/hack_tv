package io.github.diegopq.hacktv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvContract.WatchNextPrograms
import java.text.Normalizer

/** A card of the Recently played row: something the user started in another app. */
data class RecentItem(
    val id: String,
    val packageName: String,
    /** The show or movie. */
    val title: String,
    /** The episode ("S2 E5", "Rix Road"), or what the app's player shows under the title. */
    val season: String? = null,
    val episode: String? = null,
    val subtitle: String? = null,
    /** Artwork: an http(s), content or file URI. */
    val imageUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastPlayed: Long,
    /** Where the app wants a click to go (Continue watching only); otherwise the app opens. */
    val intentUri: String? = null,
    val fromApp: Boolean,
    /** Continue watching's kind (continue, next episode, new); -1 for the launcher's own history. */
    val watchNextType: Int = -1,
) {
    /** The app says a new season or episode of something the user watches is out. */
    val isNewFromApp: Boolean get() = watchNextType == WatchNextPrograms.WATCH_NEXT_TYPE_NEW

    /** How far along it is, from 0 to 1, or null when the app doesn't say. */
    val progress: Float?
        get() = if (durationMs > 0 && positionMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    /** Minutes left, or null when unknown or almost finished. */
    val minutesLeft: Int?
        get() = if (durationMs > 0 && positionMs > 0) ((durationMs - positionMs) / 60_000L).toInt().takeIf { it > 0 } else null
}

object RecentlyPlayed {

    const val MAX_ITEMS = 15
    // More than the row shows, so cards the user removed leave room for older ones.
    private const val MAX_WATCH_NEXT = 40

    /** Lets the launcher read the Continue watching list that apps publish for the TV. */
    const val LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"

    fun canReadWatchNext(context: Context): Boolean =
        context.checkSelfPermission(LISTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * The Continue watching list that apps such as YouTube and Prime Video publish to the TV.
     * Android lets a launcher read the rows an app marks as searchable (most of them) once it has
     * READ_TV_LISTINGS; without it the list is empty.
     */
    fun loadWatchNext(context: Context): List<RecentItem> {
        if (!canReadWatchNext(context)) return emptyList()
        val projection = arrayOf(
            WatchNextPrograms._ID,
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            WatchNextPrograms.COLUMN_TITLE,
            WatchNextPrograms.COLUMN_EPISODE_TITLE,
            WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            WatchNextPrograms.COLUMN_POSTER_ART_URI,
            WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            WatchNextPrograms.COLUMN_DURATION_MILLIS,
            WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            WatchNextPrograms.COLUMN_INTENT_URI,
            WatchNextPrograms.COLUMN_BROWSABLE,
        )
        val items = ArrayList<RecentItem>()
        runCatching {
            context.contentResolver.query(WatchNextPrograms.CONTENT_URI, projection, null, null, null)?.use { c ->
                fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)?.takeIf { it.isNotBlank() }
                fun long(i: Int) = if (c.isNull(i)) 0L else c.getLong(i)
                while (c.moveToNext()) {
                    // A watchlist entry was saved, not played; and an app can hide a row by making it unbrowsable.
                    if (c.getInt(6) == WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST) continue
                    if (!c.isNull(13) && c.getInt(13) == 0) continue
                    val title = str(2) ?: continue
                    items += RecentItem(
                        id = "wn:${c.getLong(0)}",
                        packageName = str(1).orEmpty(),
                        title = title,
                        season = str(4),
                        episode = str(5),
                        subtitle = str(3),
                        imageUri = str(7) ?: str(8),
                        positionMs = long(9),
                        durationMs = long(10),
                        lastPlayed = long(11),
                        intentUri = str(12),
                        fromApp = true,
                        watchNextType = c.getInt(6),
                    )
                }
            }
        }
        return items.sortedByDescending { it.lastPlayed }.take(MAX_WATCH_NEXT)
    }

    /**
     * One row from both sources, newest first. The launcher's own history leaves out anything already in
     * the apps' Continue watching list, so the same show never shows twice. When it played something from
     * that list more recently than the app says (Disney+ keeps its old date for a while), the card moves up
     * to when it was really played.
     */
    fun merge(watchNext: List<RecentItem>, history: List<RecentItem>): List<RecentItem> {
        val historyKeys = history.map { it to keysOf(it) }
        val matched = HashSet<RecentItem>()
        val published = watchNext.map { item ->
            val theirs = keysOf(item)
            val played = historyKeys.filter { (_, mine) -> sameContent(mine, theirs) }.map { it.first }
            matched += played
            val latest = played.maxOfOrNull { it.lastPlayed } ?: 0L
            if (latest > item.lastPlayed) item.copy(lastPlayed = latest) else item
        }
        val own = history.filter { it !in matched }
        return (published + own).sortedByDescending { it.lastPlayed }
    }

    /** The names an item goes by: its title, and its episode or subtitle, normalized. */
    private fun keysOf(item: RecentItem): List<String> =
        listOfNotNull(item.title, item.subtitle).map(::normalize).filter { it.isNotEmpty() }

    /**
     * Two items are the same content when any of their names match, or when one name appears as whole
     * words inside the other: an app's player may say "Andor: S2 E5 Rix Road" for the "Andor" row.
     */
    private fun sameContent(a: List<String>, b: List<String>): Boolean = a.any { x ->
        b.any { y ->
            x == y || run {
                val (short, long) = if (x.length <= y.length) x to y else y to x
                short.length >= 4 && " $long ".contains(" $short ")
            }
        }
    }

    /** Lowercase, without accents or punctuation, so "Shōgun: Ep. 7" and "shogun ep 7" match. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** An app's own link to a title (an intent: or web URI), when something on the TV can open it. */
    fun linkIntent(context: Context, uri: String?): Intent? =
        uri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.takeIf { it.resolveActivity(context.packageManager) != null }

    /** What a click opens: the app's own link for Continue watching rows, otherwise the app itself. */
    fun openIntent(context: Context, item: RecentItem, app: LauncherApp?): Intent? {
        item.intentUri?.let { uri ->
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.takeIf { it.resolveActivity(context.packageManager) != null }
                ?.let { return it }
        }
        if (app != null) return AppsRepository.launchIntent(context, app)
        return context.packageManager.getLaunchIntentForPackage(item.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
