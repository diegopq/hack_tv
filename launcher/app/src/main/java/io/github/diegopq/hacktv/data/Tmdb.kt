package io.github.diegopq.hacktv.data

import android.content.Context
import android.util.Log
import io.github.diegopq.hacktv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

/**
 * Recommendations from TMDB: popular, recent movies and series on each streaming service
 * the TV has installed (and only those), in the TV's language and country. Services are found
 * by matching installed apps' names against TMDB's list of services for the country.
 *
 * Results are cached on disk and refreshed every few hours, so the TV makes about a dozen
 * small requests at most a few times a day. This product uses the TMDB API but is not
 * endorsed or certified by TMDB.
 */
object Tmdb {

    private const val TAG = "Tmdb"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p/w1280"
    private const val PER_SERVICE = 10
    private const val MIN_PER_KIND = 3
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    val isConfigured: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** The cached set when it's fresh enough for the installed [apps], or null. */
    fun cached(context: Context, apps: List<LauncherApp>, allowStale: Boolean = false): RecommendationSet? {
        val file = cacheFile(context)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val sameQuery = json.optString("query") == queryKey(apps)
            val fresh = System.currentTimeMillis() - json.optLong("time") < MAX_AGE_MS
            if (!sameQuery || (!fresh && !allowStale)) null else decode(json.getJSONObject("sets"))
        }.getOrNull()
    }

    /** Downloads a new set and caches it. Returns null when the network or the key fails. */
    suspend fun refresh(context: Context, apps: List<LauncherApp>): RecommendationSet? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val language = languageTag()
            val region = region()
            val services = servicesFor(apps, serviceCatalog(region))
            val genres = coroutineScope {
                val tv = async { genreNames("tv", language) }
                val movie = async { genreNames("movie", language) }
                tv.await() to movie.await()
            }
            val lists = coroutineScope {
                services.map { provider ->
                    async {
                        val tv = discover("tv", provider, language, region, genres.first)
                        val movies = discover("movie", provider, language, region, genres.second)
                        provider.key to interleave(tv, movies).distinctBy { it.id }.take(PER_SERVICE)
                    }
                }.awaitAll()
            }.filter { it.second.isNotEmpty() }.toMap()
            if (lists.isEmpty()) return@withContext null

            val set = RecommendationSet(lists, fromTmdb = true)
            cacheFile(context).writeText(
                JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("query", queryKey(apps))
                    .put("sets", encode(set))
                    .toString(),
            )
            set
        } catch (e: Exception) {
            Log.w(TAG, "TMDB refresh failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * The services to recommend from: the known ones whose app is installed, plus any installed app whose
     * name matches a service in TMDB's list for the country ("Mercado Play", "Runtime", "Pluto TV"...).
     * The known list stays for apps whose name differs from TMDB's ("Prime Video" is "Amazon Prime
     * Video" there). With nothing installed, the four big ones, so the banner is never empty.
     */
    private fun servicesFor(apps: List<LauncherApp>, catalog: Map<Int, String>): List<Provider> {
        val packages = apps.map { it.packageName }.toSet()
        val known = PROVIDERS.values.filter { it.packageName in packages }
        val takenIds = known.flatMap { it.tmdbIds }.toMutableSet()
        val takenPackages = known.map { it.packageName }.toSet()
        val matched = apps.filter { it.packageName !in takenPackages }.mapNotNull { app ->
            val entry = catalog.entries.firstOrNull { (id, name) -> id !in takenIds && sameService(app.label, name) }
                ?: return@mapNotNull null
            takenIds += entry.key
            Provider(
                key = "tmdb:${entry.key}",
                name = app.label,
                packageName = app.packageName,
                color = 0xFF3A3F47,
                tmdbIds = listOf(entry.key),
            )
        }
        return (known + matched).ifEmpty { DEFAULT_PROVIDERS.mapNotNull { PROVIDERS[it] } }
    }

    /** Every streaming service TMDB lists for the country, by id: "Mercado Play", "Runtime"... */
    private fun serviceCatalog(region: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        for (type in listOf("tv", "movie")) {
            val results = get("/watch/providers/$type", mapOf("watch_region" to region)).optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val p = results.getJSONObject(i)
                val name = p.optString("provider_name")
                // Channels sold inside another app ("MUBI Amazon Channel") aren't apps of their own.
                if (name.isNotBlank() && !name.contains("channel", ignoreCase = true)) out[p.getInt("provider_id")] = name
            }
        }
        return out
    }

    /** "Disney+" and "Disney Plus", "Tubi" and "Tubi TV", "ViX" and "VIX " are the same service. */
    private fun sameService(appLabel: String, serviceName: String): Boolean {
        val a = normalize(appLabel)
        val b = normalize(serviceName)
        if (a.length < 3 || b.length < 3) return false
        return a == b || (a.removeSuffix("tv").length >= 3 && a.removeSuffix("tv") == b.removeSuffix("tv"))
    }

    private fun normalize(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("+", "plus")
            .replace(Regex("[^a-z0-9]"), "")

    private fun discover(
        type: String,
        provider: Provider,
        language: String,
        region: String,
        genreNames: Map<Int, String>,
    ): List<Recommendation> {
        // Popular titles from the last three years: plain popularity returns decades-old catalog shows.
        val recent = discoverPage(type, provider, language, region, genreNames, recentOnly = true)
        if (recent.size >= MIN_PER_KIND) return recent
        // Small or classic catalogs (Runtime, Mercado Play) have few recent, much-voted titles:
        // fill up with their popular titles of any year.
        val any = discoverPage(type, provider, language, region, genreNames, recentOnly = false)
        return (recent + any).distinctBy { it.id }
    }

    private fun discoverPage(
        type: String,
        provider: Provider,
        language: String,
        region: String,
        genreNames: Map<Int, String>,
        recentOnly: Boolean,
    ): List<Recommendation> {
        val params = mutableMapOf(
            "watch_region" to region,
            "with_watch_providers" to provider.tmdbIds.joinToString("|"),
            "with_watch_monetization_types" to "flatrate|free|ads",
            "language" to language,
            "sort_by" to "popularity.desc",
            "vote_count.gte" to if (recentOnly) "100" else "20",
            "include_adult" to "false",
        )
        if (recentOnly) {
            val dateParam = if (type == "tv") "first_air_date.gte" else "primary_release_date.gte"
            params[dateParam] = LocalDate.now().minusYears(3).toString()
        }
        val json = get("/discover/$type", params)
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val item = results.getJSONObject(i)
            val backdrop = item.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            val overview = item.optString("overview").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = item.optString(if (type == "tv") "name" else "title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val date = item.optString(if (type == "tv") "first_air_date" else "release_date")
            val genreIds = item.optJSONArray("genre_ids") ?: JSONArray()
            Recommendation(
                id = "$type:${item.getInt("id")}",
                provider = provider.key,
                providerName = provider.name,
                providerPackage = provider.packageName,
                title = title,
                year = date.take(4).toIntOrNull(),
                genres = (0 until genreIds.length()).mapNotNull { genreNames[genreIds.getInt(it)] }.take(2).joinToString(", "),
                description = overview,
                imageUrl = IMAGES + backdrop,
                isMovie = type == "movie",
            )
        }
    }

    private fun genreNames(type: String, language: String): Map<Int, String> {
        val genres = get("/genre/$type/list", mapOf("language" to language)).optJSONArray("genres") ?: return emptyMap()
        return (0 until genres.length()).associate { i ->
            val g = genres.getJSONObject(i)
            g.getInt("id") to g.getString("name")
        }
    }

    private fun get(path: String, params: Map<String, String>): JSONObject {
        val query = (params + ("api_key" to BuildConfig.TMDB_API_KEY)).entries
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = URL("$API$path?$query").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            val code = connection.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code on $path")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Series and movies alternating, so a service's list isn't all one kind. */
    private fun interleave(a: List<Recommendation>, b: List<Recommendation>): List<Recommendation> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    private fun languageTag(): String {
        val locale = Locale.getDefault()
        return if (locale.country.isNotBlank()) "${locale.language}-${locale.country}" else locale.language
    }

    /**
     * The country whose streaming catalogs to use. The TV's language often names another country
     * (a TV in Mexico set to es-US), so the time zone decides: America/Mexico_City is MX. TMDB_REGION
     * in .env still overrides it for builds that need a fixed country.
     */
    internal fun region(): String {
        BuildConfig.TMDB_REGION.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        val fromTimeZone = runCatching { android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().id) }
            .getOrNull()?.takeIf { it.length == 2 }   // "001" means no country (UTC, Etc/...)
        return (fromTimeZone ?: Locale.getDefault().country.ifBlank { "MX" }).uppercase()
    }

    /** The cache is only valid for the same language, country and installed apps. */
    private fun queryKey(apps: List<LauncherApp>): String =
        "v5|${languageTag()}|${region()}|" + apps.map { it.packageName }.sorted().joinToString(",").hashCode()

    private fun cacheFile(context: Context) = File(context.cacheDir, "tmdb-recommendations.json")

    private fun encode(set: RecommendationSet): JSONObject = JSONObject().apply {
        set.byKey.forEach { (key, list) ->
            put(key, JSONArray().apply {
                list.forEach { r ->
                    put(
                        JSONObject()
                            .put("id", r.id).put("provider", r.provider).put("providerName", r.providerName)
                            .put("providerPackage", r.providerPackage).put("title", r.title)
                            .put("year", r.year ?: 0).put("genres", r.genres).put("description", r.description)
                            .put("image", r.imageUrl).put("movie", r.isMovie),
                    )
                }
            })
        }
    }

    private fun decode(json: JSONObject): RecommendationSet {
        val byKey = json.keys().asSequence().associateWith { key ->
            val array = json.getJSONArray(key)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Recommendation(
                    id = o.getString("id"),
                    provider = o.getString("provider"),
                    providerName = o.getString("providerName"),
                    providerPackage = o.getString("providerPackage"),
                    title = o.getString("title"),
                    year = o.optInt("year").takeIf { it > 0 },
                    genres = o.optString("genres"),
                    description = o.optString("description"),
                    imageUrl = o.getString("image"),
                    isMovie = o.optBoolean("movie"),
                )
            }
        }
        return RecommendationSet(byKey, fromTmdb = true)
    }
}
