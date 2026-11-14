package io.github.diegopq.hacktv.data

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvContract.PreviewPrograms

/** A title an app features on the TV's home screen, with the app's own link to it. */
data class FeaturedItem(
    val id: String,
    val packageName: String,
    val title: String,
    /** Landscape artwork when the app has it, otherwise its poster. */
    val imageUri: String?,
    val intentUri: String?,
)

/**
 * The rows each app publishes for the TV's home screen ("preview channels"): Netflix, Prime Video,
 * Disney+, Apple TV, Mercado Play, Runtime and others keep them up to date even though Google TV no longer
 * shows them. They hold what each app is promoting, with a direct link to each title, but no dates.
 * Readable with READ_TV_LISTINGS, the same permission as Continue watching.
 */
object AppFeatured {

    /** The TV's own Live TV and HDMI shortcuts, already on Home as Inputs. */
    private val SKIPPED_PACKAGES = setOf("com.vt.tv.tvcenter")
    private const val MAX_ROW = 20

    /** Landscape aspect ratios: 16:9, 3:2 and 4:3. */
    private val LANDSCAPE = setOf(
        TvContract.PreviewPrograms.ASPECT_RATIO_16_9,
        TvContract.PreviewPrograms.ASPECT_RATIO_3_2,
        TvContract.PreviewPrograms.ASPECT_RATIO_4_3,
    )

    /** Every featured title of the installed apps, in each app's own order. */
    fun load(context: Context, installed: Set<String>): List<FeaturedItem> {
        if (!RecentlyPlayed.canReadWatchNext(context)) return emptyList()
        val projection = arrayOf(
            PreviewPrograms._ID,
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            PreviewPrograms.COLUMN_TITLE,
            PreviewPrograms.COLUMN_POSTER_ART_URI,
            PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            PreviewPrograms.COLUMN_THUMBNAIL_URI,
            PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
            PreviewPrograms.COLUMN_INTENT_URI,
            PreviewPrograms.COLUMN_BROWSABLE,
            PreviewPrograms.COLUMN_WEIGHT,
        )
        val items = ArrayList<Pair<FeaturedItem, Int>>()
        runCatching {
            context.contentResolver.query(PreviewPrograms.CONTENT_URI, projection, null, null, null)?.use { c ->
                fun str(i: Int) = if (c.isNull(i)) null else c.getString(i)?.takeIf { it.isNotBlank() }
                fun int(i: Int) = if (c.isNull(i)) -1 else c.getInt(i)
                while (c.moveToNext()) {
                    val pkg = str(1) ?: continue
                    if (pkg in SKIPPED_PACKAGES || pkg !in installed) continue
                    if (int(8) == 0) continue
                    val title = str(2) ?: continue
                    val poster = str(3)?.takeIf { int(4) in LANDSCAPE }
                    val thumb = str(5)?.takeIf { int(6) in LANDSCAPE }
                    items += FeaturedItem(
                        id = "pp:${c.getLong(0)}",
                        packageName = pkg,
                        title = title,
                        imageUri = poster ?: thumb ?: str(3) ?: str(5),
                        intentUri = str(7),
                    ) to int(9)
                }
            }
        }
        // Each app's order: by the weight the app gave (higher first), then as published.
        return items.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Pair<FeaturedItem, Int>>> { it.value.second }.thenBy { it.index })
            .map { it.value.first }
            .distinctBy { it.packageName to key(it.title) }
    }

    /**
     * The row: a few titles from every app in turn, so no single app (Apple TV publishes hundreds) fills it.
     * Anything already in Recently played or New in your apps is left out.
     */
    fun row(all: List<FeaturedItem>, appOrder: List<String>, exclude: Set<String>): List<FeaturedItem> {
        val byApp = all.filter { key(it.title) !in exclude }.groupBy { it.packageName }
            .mapValues { it.value.toMutableList() }
        val order = appOrder.filter { it in byApp } + byApp.keys.filter { it !in appOrder }
        val out = ArrayList<FeaturedItem>()
        while (out.size < MAX_ROW && byApp.values.any { it.isNotEmpty() }) {
            for (pkg in order) {
                val list = byApp.getValue(pkg)
                if (list.isNotEmpty() && out.size < MAX_ROW) out += list.removeAt(0)
            }
        }
        return out
    }

    /**
     * The app's link for a title it features, to open a banner recommendation right on the title instead of
     * the app's home. Prime Video names seasons ("La lista terminal - Temporada 2"), so those match too.
     */
    fun linkFor(all: List<FeaturedItem>, packageName: String, title: String): String? {
        val wanted = key(title)
        return all.firstOrNull { it.packageName == packageName && key(it.title) == wanted }?.intentUri
    }

    /** A title without accents, punctuation or a trailing season ("- Temporada 2", "- Season 2"). */
    fun key(title: String): String =
        RecentlyPlayed.normalize(title.replace(Regex("\\s*[-–:]\\s*(temporada|season|saison|staffel)\\s*\\d+\\s*$", RegexOption.IGNORE_CASE), ""))
}
