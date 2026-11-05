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
    /** The most vivid color of its banner or icon (ARGB), for Home's ambient glow. */
    val accent: Int? = null,
    /** The app's own description, when it ships one. */
    val description: String? = null,
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
                val bannerBitmap = runCatching { activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm) }
                    .getOrNull()?.renderBitmap(BANNER_W, BANNER_H)
                val iconBitmap = if (bannerBitmap == null) {
                    runCatching { info.loadIcon(pm) }.getOrNull()?.renderBitmap(ICON_SIZE, ICON_SIZE)
                } else null
                val banner = bannerBitmap?.asImageBitmap()
                val icon = iconBitmap?.asImageBitmap()
                val accent = (bannerBitmap ?: iconBitmap)?.let(::accentColor)
                apps += LauncherApp(
                    packageName = pkg,
                    component = ComponentName(pkg, activity.name),
                    label = info.loadLabel(pm).toString(),
                    banner = banner,
                    icon = icon,
                    isTvApp = isTv,
                    accent = accent,
                    description = runCatching { activity.applicationInfo.loadDescription(pm)?.toString() }
                        .getOrNull()?.takeIf { it.isNotBlank() },
                )
            }
        }
        apps.sortedBy { it.label.lowercase() }
    }

    /**
     * My apps on Home: the user's order when there is one, then any app installed since, in the default
     * order (favorites first, then the other TV apps alphabetically). Hidden apps are left out.
     */
    fun homeOrder(apps: List<LauncherApp>, layout: HomeLayout = HomeLayout()): List<LauncherApp> {
        val visible = apps.filter { it.packageName !in layout.hidden }
        val byPackage = visible.associateBy { it.packageName }
        val favorites = FAVORITE_PACKAGES.mapNotNull { byPackage[it] }
        val default = favorites + visible.filter { it.isTvApp && it.packageName !in FAVORITE_PACKAGES }
        if (layout.order.isEmpty()) return default
        val ordered = layout.order.mapNotNull { byPackage[it] }
        return ordered + default.filter { it.packageName !in layout.order }
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
private fun Drawable.renderBitmap(maxW: Int, maxH: Int): Bitmap? {
    val w = intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(maxW * 2) ?: maxW
    val h = intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(maxH * 2) ?: maxH
    return runCatching { toBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
}

/**
 * The most vivid color in a bitmap: Netflix's red, Disney+'s blue. Samples a 16x9 thumbnail and keeps the
 * pixel with the most saturation and brightness, skipping transparent, near-white and near-black ones.
 */
private fun accentColor(bitmap: Bitmap): Int? {
    val small = Bitmap.createScaledBitmap(bitmap, 16, 9, true)
    val hsv = FloatArray(3)
    var best: Int? = null
    var bestScore = 0f
    for (y in 0 until small.height) for (x in 0 until small.width) {
        val c = small.getPixel(x, y)
        if (android.graphics.Color.alpha(c) < 128) continue
        android.graphics.Color.colorToHSV(c, hsv)
        val score = hsv[1] * hsv[2]
        if (hsv[2] > 0.15f && score > bestScore) { bestScore = score; best = c }
    }
    if (small !== bitmap) small.recycle()
    return best?.takeIf { bestScore > 0.12f }
}
