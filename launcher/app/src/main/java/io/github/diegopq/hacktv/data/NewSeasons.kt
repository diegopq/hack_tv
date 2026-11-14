package io.github.diegopq.hacktv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** A season of a series the user watched that is newer than the last one they saw. */
data class NewSeason(
    /** "tv:<TMDB id>:s<season>": the same season is never shown again once removed. */
    val id: String,
    val title: String,
    val season: Int,
    /** When it premiered, or premieres (yyyy-MM-dd). */
    val airDate: String,
    /** True when it hasn't premiered yet. */
    val upcoming: Boolean,
    val imageUrl: String?,
    /** The app the user watched the series in, opened by OK. */
    val packageName: String,
)

/**
 * New seasons of the series the user watches. The launcher remembers every series that shows up in
 * the apps' Continue watching list (apps drop old rows, so this list outlives them), finds each one on
 * TMDB once, and checks its seasons twice a day: a season after the last one watched that premiered in
 * the last year, or that premieres in the next three months, gets a card in "New in your apps".
 */
object NewSeasons {

    private const val TAG = "NewSeasons"
    private const val PREFS = "new_seasons"
    private const val KEY_SHOWS = "shows"
    private const val KEY_DISMISSED = "dismissed"
    private const val CACHE = "new-seasons.json"
    private const val MAX_SHOWS = 60
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
    private const val RETRY_NOT_FOUND_MS = 7 * 24 * 60 * 60 * 1000L
    private const val AIRED_WITHIN_DAYS = 365L
    private const val UPCOMING_WITHIN_DAYS = 90L

    /** A series the user watched: in which app, and the highest season they reached. */
    private data class Show(
        val title: String,
        val packageName: String,
        val seenSeason: Int,
        val lastSeen: Long,
        /** TMDB's id; null before the first search, -1 when TMDB had no exact match. */
        val tmdbId: Int? = null,
        val searchedAt: Long = 0,
    ) {
        val key get() = "$packageName|${RecentlyPlayed.normalize(title)}"
    }

    private val _items = MutableStateFlow<List<NewSeason>>(emptyList())
    val items: StateFlow<List<NewSeason>> = _items.asStateFlow()
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    /** The last result and the removed cards, from disk, once. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _dismissed.value = prefs(context).getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet()
        runCatching { decodeItems(JSONObject(cacheFile(context).readText()).getJSONArray("items")) }
            .getOrNull()?.let { _items.value = it }
    }

    /** Hides a card for good: that season of that series. */
    fun dismiss(context: Context, id: String) {
        val updated = _dismissed.value + id
        _dismissed.value = updated
        prefs(context).edit().putStringSet(KEY_DISMISSED, updated).apply()
    }

    /**
     * Adds the series in [watchNext] to the remembered ones, or raises the season reached. Returns true when
     * that changed anything, so the seasons are checked again right away instead of at the next refresh.
     */
    fun track(context: Context, watchNext: List<RecentItem>): Boolean {
        val shows = loadShows(context).associateBy { it.key }.toMutableMap()
        var changed = false
        for (item in watchNext) {
            val season = item.season?.trim()?.toIntOrNull() ?: continue   // movies have no season
            val incoming = Show(item.title, item.packageName, season, item.lastPlayed)
            val known = shows[incoming.key]
            when {
                known == null -> { shows[incoming.key] = incoming; changed = true }
                season > known.seenSeason -> { shows[incoming.key] = known.copy(seenSeason = season, lastSeen = item.lastPlayed); changed = true }
                item.lastPlayed > known.lastSeen -> shows[incoming.key] = known.copy(lastSeen = item.lastPlayed)
            }
        }
        if (changed) saveShows(context, shows.values.toList())
        return changed
    }

    /** Checks the seasons on TMDB when the last check is older than half a day, or when [force] is set. */
    suspend fun refresh(context: Context, force: Boolean) = withContext(Dispatchers.IO) {
        if (!Tmdb.isConfigured) return@withContext
        mutex.withLock {
            val cache = cacheFile(context)
            val age = runCatching { System.currentTimeMillis() - JSONObject(cache.readText()).getLong("time") }.getOrNull()
            if (!force && age != null && age < MAX_AGE_MS) return@withLock
            try {
                val language = Tmdb.languageTag()
                val today = LocalDate.now()
                val shows = loadShows(context).sortedByDescending { it.lastSeen }.take(MAX_SHOWS)
                val updated = ArrayList<Show>()
                val found = ArrayList<NewSeason>()
                for (show in shows) {
                    val resolved = resolve(show, language)
                    updated += resolved
                    val id = resolved.tmdbId?.takeIf { it > 0 } ?: continue
                    newSeasonOf(id, resolved, language, today)?.let { found += it }
                }
                saveShows(context, updated + loadShows(context).filter { s -> updated.none { it.key == s.key } })
                val ordered = found.filter { !it.upcoming }.sortedByDescending { it.airDate } +
                    found.filter { it.upcoming }.sortedBy { it.airDate }
                _items.value = ordered
                cache.writeText(JSONObject().put("time", System.currentTimeMillis()).put("items", encodeItems(ordered)).toString())
            } catch (e: Exception) {
                Log.w(TAG, "New seasons check failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Finds the series on TMDB by the name the app gave it. Only an exact match counts (after removing
     * accents and punctuation), against the translated or the original name, so a similar title never
     * borrows another series' seasons. A miss is tried again a week later.
     */
    private fun resolve(show: Show, language: String): Show {
        val now = System.currentTimeMillis()
        if (show.tmdbId != null && (show.tmdbId > 0 || now - show.searchedAt < RETRY_NOT_FOUND_MS)) return show
        val results = Tmdb.get("/search/tv", mapOf("query" to show.title, "language" to language)).optJSONArray("results")
            ?: return show.copy(tmdbId = -1, searchedAt = now)
        val wanted = RecentlyPlayed.normalize(show.title)
        val match = (0 until minOf(results.length(), 5)).map { results.getJSONObject(it) }.firstOrNull { r ->
            RecentlyPlayed.normalize(r.optString("name")) == wanted ||
                RecentlyPlayed.normalize(r.optString("original_name")) == wanted
        }
        return show.copy(tmdbId = match?.getInt("id") ?: -1, searchedAt = now)
    }

    /** The newest season after the one watched: premiered in the last year, or premiering soon. */
    private fun newSeasonOf(id: Int, show: Show, language: String, today: LocalDate): NewSeason? {
        val details = Tmdb.get("/tv/$id", mapOf("language" to language))
        val name = details.optString("name").ifBlank { show.title }
        val image = details.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { Tmdb.IMAGES + it }
        val seasons = details.optJSONArray("seasons") ?: JSONArray()
        val aired = (0 until seasons.length()).map { seasons.getJSONObject(it) }
            .filter { it.optInt("season_number") > show.seenSeason }
            .mapNotNull { s -> s.dateOrNull("air_date")?.let { s.optInt("season_number") to it } }
            .filter { (_, date) -> !date.isAfter(today) && !date.isBefore(today.minusDays(AIRED_WITHIN_DAYS)) }
            .maxByOrNull { it.first }
        if (aired != null) {
            return NewSeason("tv:$id:s${aired.first}", name, aired.first, aired.second.toString(), upcoming = false, image, show.packageName)
        }
        val next = details.optJSONObject("next_episode_to_air") ?: return null
        val season = next.optInt("season_number")
        val date = next.dateOrNull("air_date") ?: return null
        val premiere = next.optInt("episode_number") == 1 && season > show.seenSeason
        if (!premiere || date.isBefore(today) || date.isAfter(today.plusDays(UPCOMING_WITHIN_DAYS))) return null
        return NewSeason("tv:$id:s$season", name, season, date.toString(), upcoming = true, image, show.packageName)
    }

    private fun JSONObject.dateOrNull(key: String): LocalDate? =
        optString(key).takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun loadShows(context: Context): List<Show> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_SHOWS, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Show(
                title = o.getString("title"),
                packageName = o.getString("pkg"),
                seenSeason = o.getInt("season"),
                lastSeen = o.getLong("seen"),
                tmdbId = if (o.has("tmdb")) o.getInt("tmdb") else null,
                searchedAt = o.optLong("searched"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveShows(context: Context, shows: List<Show>) {
        val array = JSONArray()
        shows.sortedByDescending { it.lastSeen }.take(MAX_SHOWS * 2).forEach { s ->
            array.put(JSONObject().apply {
                put("title", s.title); put("pkg", s.packageName); put("season", s.seenSeason); put("seen", s.lastSeen)
                s.tmdbId?.let { put("tmdb", it) }
                put("searched", s.searchedAt)
            })
        }
        prefs(context).edit().putString(KEY_SHOWS, array.toString()).apply()
    }

    private fun encodeItems(items: List<NewSeason>) = JSONArray().apply {
        items.forEach { n ->
            put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("season", n.season); put("date", n.airDate)
                put("upcoming", n.upcoming); put("pkg", n.packageName); n.imageUrl?.let { put("image", it) }
            })
        }
    }

    private fun decodeItems(array: JSONArray) = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        NewSeason(
            id = o.getString("id"),
            title = o.getString("title"),
            season = o.getInt("season"),
            airDate = o.getString("date"),
            upcoming = o.getBoolean("upcoming"),
            imageUrl = o.optString("image").takeIf { it.isNotEmpty() },
            packageName = o.getString("pkg"),
        )
    }

    private fun cacheFile(context: Context) = File(context.cacheDir, CACHE)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
