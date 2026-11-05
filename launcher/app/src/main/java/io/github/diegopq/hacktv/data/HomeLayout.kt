package io.github.diegopq.hacktv.data

import android.content.Context

/**
 * How the user arranged My apps: the order they chose and the apps they hid from Home.
 * An empty order means the default one (favorites first, then the other TV apps).
 */
data class HomeLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/** Kept in the launcher's own preferences, so it survives reboots and launcher updates. */
object HomeLayoutStore {

    private const val PREFS = "home_layout"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"

    fun load(context: Context): HomeLayout {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val order = prefs.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        val hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        return HomeLayout(order, hidden)
    }

    fun save(context: Context, layout: HomeLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, layout.order.joinToString(","))
            .putStringSet(KEY_HIDDEN, layout.hidden)
            .apply()
    }
}
