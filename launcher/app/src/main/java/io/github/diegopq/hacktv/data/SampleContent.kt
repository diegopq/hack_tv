package io.github.diegopq.hacktv.data

import java.util.Locale

/** A streaming service a recommendation can come from, with its TMDB watch-provider id. */
data class Provider(
    val key: String,
    val name: String,
    val packageName: String,
    val color: Long,
    val tmdbId: Int,
)

/** The services the banner knows, in the order the mixed list takes them. */
val PROVIDERS = listOf(
    Provider("netflix", "Netflix", "com.netflix.ninja", 0xFFE50914, 8),
    Provider("prime", "Prime Video", "com.amazon.amazonvideo.livingroom", 0xFF00A8E1, 119),
    Provider("disney", "Disney+", "com.disney.disneyplus", 0xFF1C3FA8, 337),
    Provider("max", "HBO Max", "com.wbd.stream", 0xFF0021F3, 1899),
    Provider("paramount", "Paramount+", "com.cbs.ott", 0xFF0064FF, 531),
    Provider("vix", "ViX", "com.univision.prendetv", 0xFFFF5A00, 457),
    Provider("crunchyroll", "Crunchyroll", "com.crunchyroll.crunchyroid", 0xFFF47521, 283),
).associateBy { it.key }

/** The four services the banner falls back to when none of the known apps is installed. */
val DEFAULT_PROVIDERS = listOf("netflix", "prime", "disney", "max")

/** A movie or series for the recommendations banner, already in the TV's language. */
data class Recommendation(
    val id: String,
    val provider: String,
    val title: String,
    val year: Int?,
    val genres: String,
    val description: String,
    val imageUrl: String,
    val isMovie: Boolean,
)

/** Recommendations for each service key, plus [SampleContent.MIX] for a mix of all of them. */
data class RecommendationSet(val byKey: Map<String, List<Recommendation>>, val fromTmdb: Boolean) {
    /** One service's list, or the mix when that service has none. */
    fun forKey(key: String): List<Recommendation> =
        byKey[key]?.takeIf { it.isNotEmpty() } ?: byKey[SampleContent.MIX].orEmpty()
}

/** A sample entry with its text in Spanish and English. */
private class SampleShow(
    val id: String,
    val provider: String,
    val year: Int,
    val imageUrl: String,
    val title: Pair<String, String>,
    val genres: Pair<String, String>,
    val description: Pair<String, String>,
) {
    fun localized(spanish: Boolean) = Recommendation(
        id = id,
        provider = provider,
        title = if (spanish) title.first else title.second,
        year = year,
        genres = if (spanish) genres.first else genres.second,
        description = if (spanish) description.first else description.second,
        imageUrl = imageUrl,
        isMovie = false,
    )
}

/**
 * Sample recommendations until TMDB is connected. Images load from TVmaze (CC BY-SA).
 * The real data will come from TMDB, filtered by the installed apps and the TV's region.
 */
object SampleContent {

    private fun img(id: String) = "https://static.tvmaze.com/uploads/images/original_untouched/$id.jpg"

    private val all = listOf(
        SampleShow("stranger", "netflix", 2016, img("410/1025588"),
            "Stranger Things" to "Stranger Things",
            "Drama, Terror, Ciencia ficción" to "Drama, Horror, Sci-Fi",
            "En Hawkins, un grupo de amigos enfrenta fuerzas sobrenaturales y experimentos secretos tras la desaparición de un niño." to
                "In Hawkins, a group of friends faces supernatural forces and secret experiments after a boy disappears."),
        SampleShow("wednesday", "netflix", 2022, img("423/1059627"),
            "Merlina" to "Wednesday",
            "Comedia, Misterio" to "Comedy, Mystery",
            "Merlina Addams llega a la Academia Nevermore y queda envuelta en una serie de asesinatos que sacude al pueblo." to
                "Wednesday Addams arrives at Nevermore Academy and gets caught up in a string of murders that shakes the town."),
        SampleShow("squid", "netflix", 2021, img("551/1377828"),
            "El juego del calamar" to "Squid Game",
            "Drama, Suspenso" to "Drama, Thriller",
            "Cientos de personas endeudadas aceptan competir en juegos infantiles por un premio millonario, con consecuencias mortales." to
                "Hundreds of people in debt agree to compete in children's games for a huge cash prize, with deadly consequences."),
        SampleShow("boys", "prime", 2019, img("265/664986"),
            "The Boys" to "The Boys",
            "Acción, Fantasía" to "Action, Fantasy",
            "Un grupo de vigilantes se propone exponer a los superhéroes corruptos que abusan de sus poderes." to
                "A group of vigilantes sets out to expose corrupt superheroes who abuse their powers."),
        SampleShow("reacher", "prime", 2022, img("631/1577970"),
            "Reacher" to "Reacher",
            "Acción, Suspenso" to "Action, Thriller",
            "Jack Reacher, exinvestigador de la policía militar, llega a un pueblo y queda atrapado en una conspiración." to
                "Jack Reacher, a former military police investigator, arrives in a small town and gets caught up in a conspiracy."),
        SampleShow("mando", "disney", 2019, img("245/612595"),
            "The Mandalorian" to "The Mandalorian",
            "Acción, Aventura" to "Action, Adventure",
            "Un cazarrecompensas solitario recorre los confines de la galaxia, lejos de la autoridad de la Nueva República." to
                "A lone bounty hunter travels the outer reaches of the galaxy, far from the authority of the New Republic."),
        SampleShow("andor", "disney", 2022, img("412/1030745"),
            "Andor" to "Andor",
            "Ciencia ficción, Espionaje" to "Sci-Fi, Espionage",
            "La historia de Cassian Andor y de los años que encendieron la chispa de la Rebelión." to
                "The story of Cassian Andor and the years that sparked the Rebellion."),
        SampleShow("bear", "disney", 2022, img("500/1251717"),
            "The Bear" to "The Bear",
            "Drama, Comedia" to "Drama, Comedy",
            "Un chef de alta cocina regresa a Chicago para hacerse cargo del pequeño restaurante de su familia." to
                "A fine-dining chef returns to Chicago to run his family's small sandwich shop."),
        SampleShow("lastofus", "max", 2023, img("442/1106984"),
            "The Last of Us" to "The Last of Us",
            "Drama, Acción" to "Drama, Action",
            "Veinte años después de una pandemia, Joel debe sacar a Ellie de una zona de cuarentena y cruzar un país devastado." to
                "Twenty years after a pandemic, Joel must smuggle Ellie out of a quarantine zone and across a devastated country."),
        SampleShow("hotd", "max", 2022, img("488/1221139"),
            "La casa del dragón" to "House of the Dragon",
            "Drama, Fantasía" to "Drama, Fantasy",
            "La casa Targaryen se divide en una guerra por el Trono de Hierro, 200 años antes de Game of Thrones." to
                "House Targaryen splits in a war for the Iron Throne, 200 years before Game of Thrones."),
        SampleShow("lotus", "max", 2021, img("429/1072776"),
            "The White Lotus" to "The White Lotus",
            "Drama, Comedia" to "Drama, Comedy",
            "Una semana en un resort de lujo saca a la luz los secretos de huéspedes y empleados." to
                "A week at a luxury resort brings out the secrets of guests and staff."),
    ).associateBy { it.id }

    private val mix = listOf("stranger", "lastofus", "boys", "mando", "wednesday", "hotd")

    /** The key for a mix of services. */
    const val MIX = "mix"

    /** The built-in samples, in the TV's language. */
    fun set(): RecommendationSet {
        val spanish = Locale.getDefault().language == "es"
        val byKey = all.values.groupBy({ it.provider }, { it.localized(spanish) }).toMutableMap()
        byKey[MIX] = mix.mapNotNull { all[it]?.localized(spanish) }
        return RecommendationSet(byKey, fromTmdb = false)
    }

    /** The service an app belongs to, or null for apps without a catalog (YouTube, Spotify…). */
    fun providerKeyFor(packageName: String): String? =
        PROVIDERS.values.firstOrNull { it.packageName == packageName }?.key
}
