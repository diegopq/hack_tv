package io.github.diegopq.hacktv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import androidx.annotation.StringRes
import io.github.diegopq.hacktv.R

/** A physical input of the TV, such as an HDMI port. */
data class TvInputItem(
    val id: String,
    /** What's connected ("Xbox"), or the port's name when nothing says. */
    val label: String,
    /** The port itself ("HDMI 4"). */
    val portLabel: String,
    /** The device name the user chose in the launcher, if any. */
    val chosenName: String? = null,
) {
    /** True when [label] names a device rather than the port. */
    val isNamed: Boolean get() = label != portLabel
}

/** Devices to pick from when naming an input: brand names as they are, generic ones translated. */
enum class InputDevice(val brand: String?, @StringRes val labelRes: Int = 0, val isConsole: Boolean = false) {
    XBOX("Xbox", isConsole = true),
    PLAYSTATION("PlayStation", isConsole = true),
    SWITCH("Nintendo Switch", isConsole = true),
    APPLE_TV("Apple TV"),
    CHROMECAST("Chromecast"),
    FIRE_TV("Fire TV"),
    SET_TOP(null, R.string.device_set_top),
    PC("PC"),
    SOUNDBAR(null, R.string.device_soundbar);

    companion object {
        /** Consoles get a controller icon on their input. */
        fun isConsoleName(name: String?) = name != null && entries.any { it.isConsole && it.brand.equals(name, ignoreCase = true) } ||
            name != null && Regex("xbox|playstation|ps[345]|nintendo|switch", RegexOption.IGNORE_CASE).containsMatchIn(name)
    }
}

object Inputs {

    private const val PREFS = "input_names"

    /**
     * HDMI ports reported by the TV, named after what's connected when something knows: the name chosen in the
     * launcher, then the name given in the TV's settings, then the name the device announces over HDMI-CEC
     * (the TV lists it as an input inside the port), and the port's own name otherwise.
     */
    fun load(context: Context): List<TvInputItem> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val all = runCatching { manager.tvInputList }.getOrDefault(emptyList())
        val chosen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return all
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput && it.parentId == null && !it.isHidden(context) }
            .map { port ->
                val portLabel = port.loadLabel(context).toString()
                val custom = port.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() && it != portLabel }
                val announced = all.firstOrNull { it.parentId == port.id }?.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                val name = chosen.getString(port.id, null)?.takeIf { it.isNotBlank() }
                TvInputItem(port.id, name ?: custom ?: announced ?: portLabel, portLabel, chosenName = name)
            }
            .sortedBy { it.portLabel }
    }

    /** Saves the device the user says is connected to an input, or forgets it with null. */
    fun setName(context: Context, inputId: String, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (name == null) remove(inputId) else putString(inputId, name)
        }.apply()
    }

    /** Switches to an input through the TV's Live TV app, the same way the stock launcher does. */
    fun switchIntent(input: TvInputItem): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
