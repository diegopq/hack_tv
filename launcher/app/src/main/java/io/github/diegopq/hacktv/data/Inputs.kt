package io.github.diegopq.hacktv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** A physical input of the TV, such as an HDMI port. */
data class TvInputItem(val id: String, val label: String)

object Inputs {

    /** HDMI ports reported by the TV, with the names the user gave them in the TV's settings. */
    fun load(context: Context): List<TvInputItem> {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching { manager.tvInputList }.getOrDefault(emptyList())
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput && it.parentId == null && !it.isHidden(context) }
            .map { info ->
                val custom = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
                TvInputItem(info.id, custom ?: info.loadLabel(context).toString())
            }
            .sortedBy { it.label }
    }

    /** Switches to an input through the TV's Live TV app, the same way the stock launcher does. */
    fun switchIntent(input: TvInputItem): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
