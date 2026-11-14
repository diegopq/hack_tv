package io.github.diegopq.hacktv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Everything the "More info" page shows about a movie or a series, from TMDB. */
data class TitleDetails(
    val title: String,
    val isMovie: Boolean,
    val tagline: String?,
    val overview: String?,
    val year: Int?,
    val genres: List<String>,
    /** Minutes, for movies. */
    val runtime: Int?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    /** The age rating for the TV's country ("B15"), or the US one ("TV-14") when the country has none. */
    val certification: String?,
    /** TMDB's users' score, 0 to 10. */
    val score: Double?,
    /** The title's logo, a transparent PNG, in the TV's language when there is one. */
    val logoUrl: String?,
    /** Who created the series, or directed the movie. */
    val makers: List<String>,
    val cast: List<CastMember>,
    val seasons: List<SeasonInfo>,
    /** A YouTube video id: the trailer in the TV's language when there is one, in English otherwise. */
    val trailerKey: String?,
)

data class CastMember(val name: String, val character: String?, val photoUrl: String?)

data class SeasonInfo(val number: Int, val name: String, val episodes: Int?, val year: Int?, val posterUrl: String?)

object TitleDetailsRepository {

    private const val TAG = "TitleDetails"
    private const val LOGOS = "https://image.tmdb.org/t/p/w500"
    private const val PHOTOS = "https://image.tmdb.org/t/p/w185"
    private const val MAX_CAST = 12

    /** The last few pages opened, so going back and forth costs nothing. */
    private val cache = object : LinkedHashMap<String, TitleDetails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitleDetails>?) = size > 20
    }

    /**
     * The details of a recommendation, by its id ("tv:213375", "movie:1571955"). One request: TMDB adds the
     * cast, videos, logos and age ratings to the same answer. Null when the network or the key fails.
     */
    suspend fun load(id: String): TitleDetails? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[id] }?.let { return@withContext it }
        val type = id.substringBefore(':')
        val tmdbId = id.substringAfter(':').toIntOrNull() ?: return@withContext null
        if (!Tmdb.isConfigured || type !in setOf("tv", "movie")) return@withContext null
        try {
            val language = Tmdb.languageTag()
            val lang = language.substringBefore('-')
            val ratings = if (type == "tv") "content_ratings" else "release_dates"
            val json = Tmdb.get(
                "/$type/$tmdbId",
                mapOf(
                    "language" to language,
                    "append_to_response" to "credits,videos,images,$ratings",
                    "include_video_language" to "$lang,en,null",
                    "include_image_language" to "$lang,en,null",
                ),
            )
            parse(json, type == "movie", lang).also { details -> synchronized(cache) { cache[id] = details } }
        } catch (e: Exception) {
            Log.w(TAG, "Details failed for $id: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun parse(json: JSONObject, isMovie: Boolean, lang: String): TitleDetails {
        val region = Tmdb.region()
        val genres = json.optJSONArray("genres").objects().map { g ->
            (if (lang == "es") Tmdb.SPANISH_TV_GENRES[g.optInt("id")] else null) ?: g.optString("name")
        }.filter { it.isNotBlank() }
        val date = json.optString(if (isMovie) "release_date" else "first_air_date")
        val credits = json.optJSONObject("credits")
        val makers = if (isMovie) {
            credits?.optJSONArray("crew").objects().filter { it.optString("job") == "Director" }.map { it.optString("name") }
        } else {
            json.optJSONArray("created_by").objects().map { it.optString("name") }
        }.filter { it.isNotBlank() }.distinct().take(2)
        val cast = credits?.optJSONArray("cast").objects().take(MAX_CAST).map { c ->
            CastMember(
                name = c.optString("name"),
                character = c.text("character"),
                photoUrl = c.text("profile_path")?.let { PHOTOS + it },
            )
        }
        val seasons = if (isMovie) emptyList() else json.optJSONArray("seasons").objects()
            .filter { it.optInt("season_number") > 0 }
            .map { s ->
                SeasonInfo(
                    number = s.optInt("season_number"),
                    name = s.optString("name"),
                    episodes = s.optInt("episode_count").takeIf { it > 0 },
                    year = s.text("air_date")?.take(4)?.toIntOrNull(),
                    posterUrl = s.text("poster_path")?.let { PHOTOS + it },
                )
            }
        return TitleDetails(
            title = json.text(if (isMovie) "title" else "name").orEmpty(),
            isMovie = isMovie,
            tagline = json.text("tagline"),
            overview = json.text("overview"),
            year = date.take(4).toIntOrNull(),
            genres = genres,
            runtime = if (isMovie) json.optInt("runtime").takeIf { it > 0 }
            else json.optJSONArray("episode_run_time").let { if (it != null && it.length() > 0) it.optInt(0).takeIf { m -> m > 0 } else null },
            seasonCount = json.optInt("number_of_seasons").takeIf { it > 0 },
            episodeCount = json.optInt("number_of_episodes").takeIf { it > 0 },
            certification = certification(json, isMovie, region),
            score = json.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 && json.optInt("vote_count") >= 10 },
            logoUrl = logo(json.optJSONObject("images"), lang)?.let { LOGOS + it },
            makers = makers,
            cast = cast,
            seasons = seasons,
            trailerKey = trailer(json.optJSONObject("videos"), lang),
        )
    }

    /** The country's age rating, the US one otherwise. */
    private fun certification(json: JSONObject, isMovie: Boolean, region: String): String? {
        fun ratingFor(country: String): String? = if (isMovie) {
            json.optJSONObject("release_dates")?.optJSONArray("results").objects()
                .firstOrNull { it.optString("iso_3166_1") == country }
                ?.optJSONArray("release_dates").objects().firstNotNullOfOrNull { it.text("certification") }
        } else {
            json.optJSONObject("content_ratings")?.optJSONArray("results").objects()
                .firstOrNull { it.optString("iso_3166_1") == country }?.text("rating")
        }
        return ratingFor(region) ?: ratingFor("US")
    }

    /** A logo in the TV's language, then in English, then one with no text language. */
    private fun logo(images: JSONObject?, lang: String): String? {
        val logos = images?.optJSONArray("logos").objects()
        return listOf(lang, "en", "").firstNotNullOfOrNull { wanted ->
            logos.firstOrNull { (it.text("iso_639_1") ?: "") == wanted }?.text("file_path")
        }
    }

    /** A YouTube trailer in the TV's language, then in English; a teaser when there's no trailer. */
    private fun trailer(videos: JSONObject?, lang: String): String? {
        val youtube = videos?.optJSONArray("results").objects().filter { it.optString("site") == "YouTube" }
        for (type in listOf("Trailer", "Teaser")) {
            for (language in listOf(lang, "en")) {
                youtube.firstOrNull { it.optString("type") == type && it.optString("iso_639_1") == language }
                    ?.text("key")?.let { return it }
            }
        }
        return null
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.text(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
}
