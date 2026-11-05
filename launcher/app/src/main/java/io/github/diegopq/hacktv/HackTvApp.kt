package io.github.diegopq.hacktv

import android.app.Application
import io.github.diegopq.hacktv.keys.KeyServiceGuard
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import coil3.disk.DiskCache

class HackTvApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Only in the launcher's own process, not in the ":keys" process that runs the key service.
        if (getProcessName() == packageName) KeyServiceGuard.start(this)
    }


    /** Small caches: the TV has little RAM, and the banner only needs a handful of images at a time. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.10).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
