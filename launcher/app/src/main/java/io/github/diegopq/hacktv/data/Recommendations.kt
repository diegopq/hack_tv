package io.github.diegopq.hacktv.data


/**
 * A streaming service a recommendation can come from, with its TMDB watch-provider ids. Some services
 * have a different id per region (Prime Video is 119 in Mexico and 9 in the US), so all of them are listed.
 */
data class Provider(
    val key: String,
    val name: String,
    val packageName: String,
    val color: Long,
    val tmdbIds: List<Int>,
)

/** The services the banner knows, in the order the mixed list takes them. */
val PROVIDERS = listOf(
    Provider("netflix", "Netflix", "com.netflix.ninja", 0xFFE50914, listOf(8)),
    Provider("prime", "Prime Video", "com.amazon.amazonvideo.livingroom", 0xFF00A8E1, listOf(119, 9)),
    Provider("disney", "Disney+", "com.disney.disneyplus", 0xFF1C3FA8, listOf(337)),
    Provider("max", "HBO Max", "com.wbd.stream", 0xFF0021F3, listOf(1899)),
    Provider("paramount", "Paramount+", "com.cbs.ott", 0xFF0064FF, listOf(531, 2303, 2616)),
    Provider("vix", "ViX", "com.univision.prendetv", 0xFFFF5A00, listOf(457)),
    Provider("crunchyroll", "Crunchyroll", "com.crunchyroll.crunchyroid", 0xFFF47521, listOf(283)),
    Provider("appletv", "Apple TV", "com.apple.atve.androidtv.appletv", 0xFF2C2C2E, listOf(350)),
).associateBy { it.key }

/** The badge color for a service; services matched by name get a neutral one. */
fun providerColor(key: String): Long = PROVIDERS[key]?.color ?: 0xFF3A3F47

/** The four services the banner falls back to when none of the known apps is installed. */
val DEFAULT_PROVIDERS = listOf("netflix", "prime", "disney", "max")

/** A movie or series for the recommendations banner, already in the TV's language. */
data class Recommendation(
    val id: String,
    /** The service's key: a known one ("netflix") or "tmdb:<id>" for one matched by the app's name. */
    val provider: String,
    /** The service's name as shown on the banner ("Disponible en …"). */
    val providerName: String,
    /** The package of the installed app that plays it, opened by "Ver en …". */
    val providerPackage: String,
    val title: String,
    val year: Int?,
    val genres: String,
    val description: String,
    val imageUrl: String,
    val isMovie: Boolean,
)

/** Recommendations for each installed service (by service key). Empty until TMDB answers. */
data class RecommendationSet(val byKey: Map<String, List<Recommendation>>, val fromTmdb: Boolean) {

    companion object {
        /** No recommendations: no TMDB key, or nothing downloaded yet. Home shows the focused app instead. */
        val NONE = RecommendationSet(emptyMap(), fromTmdb = false)
    }

    /** Every title, once, from all the services. */
    val pool: List<Recommendation>
        get() = byKey.values.flatten().distinctBy { it.id }

    /**
     * A random selection for the banner: shuffled, at most [size] titles, and never two from the
     * same service in a row when there's more than one service, so one app doesn't take over.
     */
    fun shuffledPick(size: Int = 10): List<Recommendation> {
        val byService = pool.shuffled().groupBy { it.provider }.mapValues { it.value.toMutableList() }
        val order = byService.keys.shuffled()
        val out = ArrayList<Recommendation>()
        while (out.size < size && byService.values.any { it.isNotEmpty() }) {
            for (key in order) {
                val list = byService.getValue(key)
                if (list.isNotEmpty() && out.size < size) out += list.removeAt(0)
            }
        }
        return out
    }
}
