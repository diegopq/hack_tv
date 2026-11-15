package io.github.diegopq.hacktv.keys

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.diegopq.hacktv.BuildConfig
import io.github.diegopq.hacktv.MainActivity

/**
 * Makes the remote's shortcut buttons work without the stock launcher: the gear, the app list,
 * YouTube, Prime Video and Disney+. Every other key passes through untouched. It also shows the volume
 * as a number next to the TV's own bar.
 *
 * Turn it on in Settings > Accessibility, or over adb:
 *   settings put secure enabled_accessibility_services io.github.diegopq.hacktv/.keys.RemoteKeyService
 *   settings put secure accessibility_enabled 1
 * (append to the existing value with ":" if another service is already on).
 */
class RemoteKeyService : AccessibilityService() {

    private val map = RemoteKeys.DEFAULT_MAP

    private var audio: AudioManager? = null
    private var overlay: VolumeOverlay? = null

    /**
     * The TV keeps its own volume bar (its volume buttons never reach this service, except mute), so the
     * launcher only adds the number next to it: whenever the media volume changes, from the remote or the
     * soundbar's own report over HDMI-CEC, it shows the level as a percentage for a moment.
     */
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val audio = audio ?: return
            val stream = intent?.getIntExtra(EXTRA_STREAM_TYPE, AudioManager.STREAM_MUSIC) ?: AudioManager.STREAM_MUSIC
            if (stream != AudioManager.STREAM_MUSIC) return
            val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            if (volume == lastVolume && muted == lastMuted) return   // the soundbar repeating the same level
            lastVolume = volume
            lastMuted = muted
            overlay?.show(toPercent(volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), muted)
        }
    }
    private var lastVolume = -1
    private var lastMuted = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "connected, flags=${serviceInfo?.flags} caps=${serviceInfo?.capabilities}")
        audio = getSystemService(AudioManager::class.java)?.also {
            lastVolume = it.getStreamVolume(AudioManager.STREAM_MUSIC)
            lastMuted = it.isStreamMute(AudioManager.STREAM_MUSIC)
        }
        overlay = VolumeOverlay(this)
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        ContextCompat.registerReceiver(this, volumeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(volumeReceiver) }
        overlay?.release()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (BuildConfig.DEBUG) Log.d(TAG, "key ${event.keyCode} action=${event.action} scan=${event.scanCode} repeat=${event.repeatCount} device=${event.deviceId}:${event.device?.name} t=${event.eventTime}")
        val action = map[event.keyCode] ?: return false
        // Generic game controller buttons share keycodes with some remote buttons (KEYCODE_BUTTON_3 is
        // YouTube here). Only real controllers report both gamepad and joystick: the Hisense remote
        // ("SmartRC Consumer Control") reports joystick alone.
        if (event.isFromController()) return false
        // Act on the first press, without waiting for the button to be released; swallow the rest.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) perform(action)
        return true
    }

    private fun perform(action: KeyAction) {
        val intent = when (action) {
            is KeyAction.OpenApp -> RemoteKeys.openAppIntent(this, action.packageName)
            is KeyAction.OpenPage -> Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PAGE, action.page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing opens $action")
        }
    }

    private fun toPercent(volume: Int, max: Int) = if (max <= 0) 0 else (volume * 100 + max / 2) / max

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "RemoteKeyService"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}

private fun KeyEvent.isFromController(): Boolean {
    val sources = device?.sources ?: return false
    return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD &&
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
}
