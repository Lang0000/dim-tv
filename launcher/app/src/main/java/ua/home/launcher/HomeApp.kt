package ua.home.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class HomeApp : Application(), ImageLoaderFactory {
    // Keep image memory small: the TV has 2 GB RAM shared with everything else.
    // 5% of the 192 MB heap limit is ~10 MB, about 20 cards; evicted cards come back from the disk cache.
    override fun newImageLoader() = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.05).build() }
        .allowRgb565(true)
        .crossfade(250)
        .build()
}
