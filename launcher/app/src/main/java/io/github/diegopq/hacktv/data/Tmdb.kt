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
 * the TV has installed (and only those), in the TV's language and country.
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
    private const val MIX_SIZE = 8
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    val isConfigured: Boolean get() = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** The cached set when it's fresh enough for [installedPackages], or null. */
    fun cached(context: Context, installedPackages: Set<String>, allowStale: Boolean = false): RecommendationSet? {
        val file = cacheFile(context)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val sameQuery = json.optString("query") == queryKey(installedPackages)
            val fresh = System.currentTimeMillis() - json.optLong("time") < MAX_AGE_MS
            if (!sameQuery || (!fresh && !allowStale)) null else decode(json.getJSONObject("sets"))
        }.getOrNull()
    }

    /** Downloads a new set and caches it. Returns null when the network or the key fails. */
    suspend fun refresh(context: Context, installedPackages: Set<String>): RecommendationSet? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val language = languageTag()
            val region = region()
            val services = servicesFor(installedPackages)
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

            val byKey = lists.toMutableMap()
            byKey[SampleContent.MIX] = mix(services.mapNotNull { lists[it.key] })
            val set = RecommendationSet(byKey, fromTmdb = true)
            cacheFile(context).writeText(
                JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("query", queryKey(installedPackages))
                    .put("sets", encode(set))
                    .toString(),
            )
            set
        } catch (e: Exception) {
            Log.w(TAG, "TMDB refresh failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Services whose app is installed; the four big ones when none is (so the banner is never empty). */
    private fun servicesFor(installedPackages: Set<String>): List<Provider> {
        val installed = PROVIDERS.values.filter { it.packageName in installedPackages }
        return installed.ifEmpty { DEFAULT_PROVIDERS.mapNotNull { PROVIDERS[it] } }
    }

    private fun discover(
        type: String,
        provider: Provider,
        language: String,
        region: String,
        genreNames: Map<Int, String>,
    ): List<Recommendation> {
        // Popular titles from the last three years: plain popularity returns decades-old catalog shows.
        val since = LocalDate.now().minusYears(3).toString()
        val dateParam = if (type == "tv") "first_air_date.gte" else "primary_release_date.gte"
        val json = get(
            "/discover/$type",
            mapOf(
                "watch_region" to region,
                "with_watch_providers" to provider.tmdbIds.joinToString("|"),
                "with_watch_monetization_types" to "flatrate|free|ads",
                "language" to language,
                "sort_by" to "popularity.desc",
                "vote_count.gte" to "100",
                "include_adult" to "false",
                dateParam to since,
            ),
        )
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

    /** Takes one title from each service in turn. */
    private fun mix(lists: List<List<Recommendation>>): List<Recommendation> {
        val out = ArrayList<Recommendation>()
        var round = 0
        while (out.size < MIX_SIZE && lists.any { it.size > round }) {
            lists.forEach { list -> list.getOrNull(round)?.let { if (out.none { o -> o.id == it.id }) out += it } }
            round++
        }
        return out.take(MIX_SIZE)
    }

    private fun languageTag(): String {
        val locale = Locale.getDefault()
        return if (locale.country.isNotBlank()) "${locale.language}-${locale.country}" else locale.language
    }

    private fun region(): String =
        BuildConfig.TMDB_REGION.ifBlank { Locale.getDefault().country }.ifBlank { "MX" }.uppercase()

    /** The cache is only valid for the same language, country and installed services. */
    private fun queryKey(installedPackages: Set<String>): String =
        "v3|${languageTag()}|${region()}|" + servicesFor(installedPackages).joinToString(",") { it.key }

    private fun cacheFile(context: Context) = File(context.cacheDir, "tmdb-recommendations.json")

    private fun encode(set: RecommendationSet): JSONObject = JSONObject().apply {
        set.byKey.forEach { (key, list) ->
            put(key, JSONArray().apply {
                list.forEach { r ->
                    put(
                        JSONObject()
                            .put("id", r.id).put("provider", r.provider).put("title", r.title)
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
