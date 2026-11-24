package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult
import com.example.core.bibliographic.network.BibliographicHttpClients
import com.example.core.data.local.media.CoverPlaceholderDetector
import com.example.core.di.AppContainer
import com.example.core.di.DefaultAppContainer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DipeLibrisApplication : Application(), ImageLoaderFactory {
  lateinit var container: AppContainer
    private set

  private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    container = DefaultAppContainer(this)
    cleanupScope.launch {
      runCatching {
        val legacyCache = File(cacheDir, "cover_image_cache")
        if (legacyCache.exists()) {
          legacyCache.deleteRecursively()
        }
      }
    }
  }

  override fun newImageLoader(): ImageLoader =
    ImageLoader.Builder(this)
      .okHttpClient { BibliographicHttpClients.imageOkHttpClient }
      .components {
        add(
          Interceptor { chain ->
            val result: ImageResult = chain.proceed(chain.request)
            val dataUrl = chain.request.data as? String
            if (
              result is SuccessResult &&
                CoverPlaceholderDetector.isGoogleBooksHost(dataUrl) &&
                CoverPlaceholderDetector.isKnownGooglePlaceholderDrawable(result.drawable)
            ) {
              ErrorResult(
                drawable = null,
                request = chain.request,
                throwable = IllegalStateException("Rejected Google Books placeholder cover"),
              )
            } else {
              result
            }
          }
        )
      }
      .memoryCache {
        MemoryCache.Builder(this)
          .maxSizePercent(0.22)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(File(cacheDir, "cover_image_cache_v2"))
          .maxSizeBytes(50L * 1024L * 1024L)
          .build()
      }
      .respectCacheHeaders(true)
      .crossfade(120)
      .build()
}
