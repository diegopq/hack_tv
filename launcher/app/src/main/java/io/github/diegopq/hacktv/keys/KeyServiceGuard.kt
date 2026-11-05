package io.github.diegopq.hacktv.keys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the remote-button service on. Some firmware (this Hisense, in standby) wipes the accessibility
 * settings, which left the gear and App list buttons dead after the TV woke up. The launcher watches the
 * setting and the screen turning on, and puts the service back when the user wants it.
 * Runs in the launcher's main process; needs WRITE_SECURE_SETTINGS to do anything.
 */
object KeyServiceGuard {

    private const val TAG = "KeyServiceGuard"
    private const val MIN_INTERVAL_MS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private var lastRepair = 0L
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val check = Runnable { repair(app) }

        // The setting itself: react within a couple of seconds of anything clearing it.
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(check)
                handler.postDelayed(check, 1_500)
            }
        }
        val resolver = app.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer)

        // Waking up: firmware may clear the setting while it resumes, so check again a little later too.
        val screenOn = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                handler.postDelayed(check, 2_000)
                handler.postDelayed({ lastRepair = 0L; repair(app) }, 10_000)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(app, screenOn, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        repair(app)
    }

    private fun repair(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRepair < MIN_INTERVAL_MS) return
        val wasOn = RemoteKeys.isEnabled(context)
        RemoteKeys.repairIfNeeded(context)
        if (!wasOn && RemoteKeys.isEnabled(context)) {
            lastRepair = now
            Log.i(TAG, "Remote-button service was off; turned it back on")
        }
    }
}
