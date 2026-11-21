package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.bibliographic.network.BibliographicHttpClients
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer
import java.io.File

class DipeLibrisApplication : Application(), ImageLoaderFactory {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = DefaultAppContainer(this)
  }

  override fun newImageLoader(): ImageLoader =
    ImageLoader.Builder(this)
      .okHttpClient { BibliographicHttpClients.imageOkHttpClient }
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.20)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(File(cacheDir, "cover_image_cache"))
          .maxSizeBytes(50L * 1024L * 1024L)
          .build()
      }
      .respectCacheHeaders(false)
      .crossfade(true)
      .build()
}
