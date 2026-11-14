package io.github.diegopq.hacktv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Cards the user removed from Recently played, and when. The launcher can't delete another app's Continue
 * watching rows (Android only lets each app change its own), so it hides them instead: a removed card stays
 * hidden until it is played again, that is, until the app or the launcher's own history shows a later play.
 */
object RecentDismissals {

    private const val PREFS = "recent_dismissed"
    private const val KEY_ITEMS = "items"
    private const val MAX_ENTRIES = 200
    private const val MAX_AGE_MS = 365L * 24 * 60 * 60 * 1000

    private val _removed = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Content key to the time it was removed. */
    val removed: StateFlow<Map<String, Long>> = _removed.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = prefs(context).getString(KEY_ITEMS, null) ?: return
        _removed.value = runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun remove(context: Context, item: RecentItem) {
        load(context)
        val now = System.currentTimeMillis()
        val kept = (_removed.value + (keyOf(item) to now))
            .filterValues { now - it < MAX_AGE_MS }
            .entries.sortedByDescending { it.value }.take(MAX_ENTRIES)
            .associate { it.key to it.value }
        _removed.value = kept
        prefs(context).edit().putString(KEY_ITEMS, JSONObject(kept).toString()).apply()
    }

    /** False for a removed card that hasn't been played since. */
    fun isShown(item: RecentItem, removed: Map<String, Long>): Boolean {
        val at = removed[keyOf(item)] ?: return true
        return item.lastPlayed > at
    }

    /** The same show from the same app, whichever source the card came from. */
    private fun keyOf(item: RecentItem) = "${item.packageName}|${RecentlyPlayed.normalize(item.title)}"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
