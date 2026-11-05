package io.github.diegopq.hacktv.keys

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.diegopq.hacktv.BuildConfig
import io.github.diegopq.hacktv.MainActivity

/**
 * Makes the remote's shortcut buttons work without the stock launcher: the gear, the app list,
 * YouTube, Prime Video and Disney+. Every other key passes through untouched.
 *
 * Turn it on in Settings > Accessibility, or over adb:
 *   settings put secure enabled_accessibility_services io.github.diegopq.hacktv/.keys.RemoteKeyService
 *   settings put secure accessibility_enabled 1
 * (append to the existing value with ":" if another service is already on).
 */
class RemoteKeyService : AccessibilityService() {

    private val map = RemoteKeys.DEFAULT_MAP

    override fun onServiceConnected() {
        super.onServiceConnected()
        RemoteKeys.markWanted(this, true)
        if (BuildConfig.DEBUG) Log.d(TAG, "connected, flags=${serviceInfo?.flags} caps=${serviceInfo?.capabilities}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (BuildConfig.DEBUG) Log.d(TAG, "key ${event.keyCode} action=${event.action} scan=${event.scanCode}")
        val action = map[event.keyCode] ?: return false
        // Generic game controller buttons share keycodes with some remote buttons (KEYCODE_BUTTON_3 is
        // YouTube here). Controllers have sticks, remotes don't.
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

    /** Turned off by the user in the accessibility settings: don't turn it back on by ourselves. */
    override fun onUnbind(intent: Intent?): Boolean {
        RemoteKeys.markWanted(this, false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "RemoteKeyService"
    }
}

private fun KeyEvent.isFromController(): Boolean {
    val sources = device?.sources ?: return false
    return sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
}
