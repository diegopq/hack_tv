package io.github.diegopq.hacktv.data

/**
 * "New in your apps": what the apps announce themselves (Prime Video and Apple TV mark a new season of
 * something the user watches as "new" in Continue watching), then the new seasons TMDB finds for the
 * user's series, without the ones the apps already announced.
 */
object AppAnnouncements {

    fun merge(watchNext: List<RecentItem>, fromTmdb: List<NewSeason>, removed: Set<String>): List<NewSeason> {
        val announced = watchNext.filter { it.isNewFromApp }.sortedByDescending { it.lastPlayed }.mapNotNull { item ->
            val season = item.season?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val episode = item.episode?.trim()?.toIntOrNull()
            NewSeason(
                id = "app:${item.packageName}|${RecentlyPlayed.normalize(item.title)}|s$season" + (episode?.let { "e$it" } ?: ""),
                title = item.title,
                season = season,
                airDate = "",
                upcoming = false,
                imageUrl = item.imageUri,
                packageName = item.packageName,
                intentUri = item.intentUri,
                episode = episode?.takeIf { it > 1 },
                announcedAt = item.lastPlayed,
            )
        }
        val announcedTitles = announced.map { RecentlyPlayed.normalize(it.title) }.toSet()
        val others = fromTmdb.filter { RecentlyPlayed.normalize(it.title) !in announcedTitles }
        return (announced + others).filter { it.id !in removed }
    }
}
