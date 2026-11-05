package io.github.diegopq.hacktv.keys

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** What a remote button does when the launcher's key service handles it. */
sealed interface KeyAction {
    /** Opens an app, or its Play Store page when it isn't installed. */
    data class OpenApp(val packageName: String) : KeyAction

    /** Opens a page of this launcher: "home", "apps" or "settings". */
    data class OpenPage(val page: String) : KeyAction
}

object RemoteKeys {

    /**
     * The Hisense remote's buttons that only the stock Google TV launcher or Hisense services answered.
     * The Netflix button (265) is left alone: the TV handles it natively.
     */
    val DEFAULT_MAP: Map<Int, KeyAction> = mapOf(
        83 to KeyAction.OpenPage("settings"),                           // gear (KEYCODE_NOTIFICATION)
        284 to KeyAction.OpenPage("apps"),                              // app list (KEYCODE_ALL_APPS)
        190 to KeyAction.OpenApp("com.google.android.youtube.tv"),      // YouTube (KEYCODE_BUTTON_3)
        295 to KeyAction.OpenApp("com.amazon.amazonvideo.livingroom"),  // Prime Video
        607 to KeyAction.OpenApp("com.disney.disneyplus"),              // Disney+
    )

    private const val PREFS = "remote_keys"
    private const val KEY_WANTED = "wanted"

    fun component(context: Context) = ComponentName(context, RemoteKeyService::class.java)

    /** True when the key service is turned on in the TV's accessibility settings. */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** The service was on and the user hasn't turned it off: it should come back after a restart or update. */
    fun markWanted(context: Context, wanted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTED, wanted).apply()
    }

    /**
     * Turns the service back on when Android dropped it (some firmware does after an app update) and the user
     * had it on. Needs WRITE_SECURE_SETTINGS, which only adb can grant; without it this does nothing.
     */
    fun repairIfNeeded(context: Context) {
        val wanted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WANTED, false)
        if (!wanted || isEnabled(context)) return
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val updated = (current.split(':').filter { it.isNotBlank() } + component(context).flattenToString()).joinToString(":")
        runCatching {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
    }

    fun openAppIntent(context: Context, packageName: String): Intent {
        val pm = context.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        return (launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}
