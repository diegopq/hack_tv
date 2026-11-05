package io.github.diegopq.hacktv.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.diegopq.hacktv.R

/** A shortcut to a page of the TV's own settings. The first action the TV answers is used. */
enum class SystemShortcut(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @DrawableRes val icon: Int,
    val actions: List<String>,
) {
    SETTINGS(R.string.sc_settings, R.string.sc_settings_sub, R.drawable.ic_sliders, listOf(Settings.ACTION_SETTINGS)),
    NETWORK(R.string.sc_network, R.string.sc_network_sub, R.drawable.ic_wifi,
        listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)),
    PICTURE(R.string.sc_picture, R.string.sc_picture_sub, R.drawable.ic_display,
        listOf(Settings.ACTION_DISPLAY_SETTINGS)),
    SOUND(R.string.sc_sound, R.string.sc_sound_sub, R.drawable.ic_sound, listOf(Settings.ACTION_SOUND_SETTINGS)),
    REMOTES(R.string.sc_remotes, R.string.sc_remotes_sub, R.drawable.ic_remote,
        listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
    APPS(R.string.sc_apps, R.string.sc_apps_sub, R.drawable.ic_apps, listOf(Settings.ACTION_APPLICATION_SETTINGS)),
    STORAGE(R.string.sc_storage, R.string.sc_storage_sub, R.drawable.ic_storage,
        listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
    LANGUAGE(R.string.sc_language, R.string.sc_language_sub, R.drawable.ic_globe, listOf(Settings.ACTION_LOCALE_SETTINGS));

    /** The intent for this shortcut, falling back to the main settings page when the TV has no such page. */
    fun intent(context: Context): Intent {
        val pm = context.packageManager
        val action = actions.firstOrNull { Intent(it).resolveActivity(pm) != null } ?: Settings.ACTION_SETTINGS
        return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        /** The shortcuts shown in the System row on Home. */
        val HOME_ROW = listOf(SETTINGS, NETWORK, PICTURE, SOUND, REMOTES, LANGUAGE)
    }
}
