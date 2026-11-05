package io.github.diegopq.hacktv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app that can be opened from the launcher. */
data class LauncherApp(
    val packageName: String,
    val component: ComponentName,
    val label: String,
    /** The app's TV banner (16:9), when it ships one. */
    val banner: ImageBitmap?,
    /** The app's icon, used only when there is no banner. */
    val icon: ImageBitmap?,
    /** True when the app has a TV (Leanback) launcher entry. */
    val isTvApp: Boolean,
)

/** Streaming apps, by package name, in the order they go first on Home. */
val FAVORITE_PACKAGES = listOf(
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.amazon.amazonvideo.livingroom",
    "com.disney.disneyplus",
    "com.wbd.stream",
    "com.spotify.tv.android",
    "com.plexapp.android",
    "com.android.vending",
)

/** Packages that never show as tiles: this launcher and system apps that already have a shortcut. */
private val HIDDEN_PACKAGES = setOf(
    "io.github.diegopq.hacktv",
    "com.android.tv.settings",
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
)

object AppsRepository {

    suspend fun load(context: Context): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val seen = HashSet<String>()
        val apps = ArrayList<LauncherApp>()
        val sources = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER to true,
            Intent.CATEGORY_LAUNCHER to false,
        )
        for ((category, isTv) in sources) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                if (pkg in HIDDEN_PACKAGES || !seen.add(pkg)) continue
                val banner = runCatching { activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm) }
                    .getOrNull()?.toImageBitmap(BANNER_W, BANNER_H)
                val icon = if (banner == null) {
                    runCatching { info.loadIcon(pm) }.getOrNull()?.toImageBitmap(ICON_SIZE, ICON_SIZE)
                } else null
                apps += LauncherApp(
                    packageName = pkg,
                    component = ComponentName(pkg, activity.name),
                    label = info.loadLabel(pm).toString(),
                    banner = banner,
                    icon = icon,
                    isTvApp = isTv,
                )
            }
        }
        apps.sortedBy { it.label.lowercase() }
    }

    /** Favorites first, in [FAVORITE_PACKAGES] order, then the other TV apps alphabetically. */
    fun homeOrder(apps: List<LauncherApp>): List<LauncherApp> {
        val byPackage = apps.associateBy { it.packageName }
        val favorites = FAVORITE_PACKAGES.mapNotNull { byPackage[it] }
        val rest = apps.filter { it.isTvApp && it.packageName !in FAVORITE_PACKAGES }
        return favorites + rest
    }

    fun launchIntent(context: Context, app: LauncherApp): Intent {
        val pm = context.packageManager
        val intent = (if (app.isTvApp) pm.getLeanbackLaunchIntentForPackage(app.packageName) else null)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setComponent(app.component)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private const val BANNER_W = 320
    private const val BANNER_H = 180
    private const val ICON_SIZE = 128
}

/** Draws a drawable into a bitmap no larger than the given size, keeping banners sharp but small in memory. */
private fun Drawable.toImageBitmap(maxW: Int, maxH: Int): ImageBitmap? {
    val w = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(maxW * 2) ?: maxW
    val h = intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(maxH * 2) ?: maxH
    return runCatching { toBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull()
}
