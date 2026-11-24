package com.example.core.data.local.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CoverStorageManager {
  suspend fun importCoverFromUri(sourceUri: Uri): String?

  suspend fun downloadAndImportCover(imageUrl: String): String? = null

  suspend fun downloadBestAvailableCover(imageUrls: List<String>): String? =
    imageUrls.firstNotNullOfOrNull { downloadAndImportCover(it) }

  suspend fun downloadBestAvailableCover(
    imageUrls: List<String>,
    minPreferredWidth: Int,
  ): String? = downloadBestAvailableCover(imageUrls)

  suspend fun getLocalCoverDimensions(coverUri: String?): Pair<Int, Int>? = null

  suspend fun deleteCoverIfManaged(coverUri: String?)
}

class InternalCoverStorageManager(
  private val context: Context,
  private val okHttpClient: okhttp3.OkHttpClient =
    com.example.core.bibliographic.network.BibliographicHttpClients.okHttpClient,
) : CoverStorageManager {

  private val coversDir: File
    get() = File(context.filesDir, "covers")

  override suspend fun importCoverFromUri(sourceUri: Uri): String? =
    withContext(Dispatchers.IO) {
      var tempSourceFile: File? = null
      try {
        val resolver = context.contentResolver
        val tempFile = File.createTempFile("cover_import_", ".tmp", context.cacheDir)
        tempSourceFile = tempFile

        val copiedBytes =
          resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
          } ?: return@withContext null

        if (copiedBytes <= 0L || !tempFile.exists() || tempFile.length() <= 0L) {
          return@withContext null
        }

        decodeAndSaveTempFile(tempFile, minWidth = 1, minHeight = 1)?.first
      } catch (_: Throwable) {
        null
      } finally {
        tempSourceFile?.delete()
      }
    }

  override suspend fun downloadAndImportCover(imageUrl: String): String? =
    downloadAndImportWithDimensions(imageUrl)?.first

  override suspend fun downloadBestAvailableCover(imageUrls: List<String>): String? =
    downloadBestAvailableCover(imageUrls, minPreferredWidth = DEFAULT_PREFERRED_MIN_WIDTH_PX)

  override suspend fun downloadBestAvailableCover(
    imageUrls: List<String>,
    minPreferredWidth: Int,
  ): String? =
    withContext(Dispatchers.IO) {
      var bestFallbackPath: String? = null
      var bestFallbackWidth = 0

      for (url in imageUrls.distinct()) {
        val result = downloadAndImportWithDimensions(url) ?: continue
        val (path, width) = result
        if (width >= minPreferredWidth) {
          if (bestFallbackPath != null && bestFallbackPath != path) {
            deleteCoverIfManaged(bestFallbackPath)
          }
          return@withContext path
        }
        if (width > bestFallbackWidth) {
          if (bestFallbackPath != null && bestFallbackPath != path) {
            deleteCoverIfManaged(bestFallbackPath)
          }
          bestFallbackPath = path
          bestFallbackWidth = width
        } else {
          deleteCoverIfManaged(path)
        }
      }
      bestFallbackPath
    }

  override suspend fun getLocalCoverDimensions(coverUri: String?): Pair<Int, Int>? =
    withContext(Dispatchers.IO) {
      if (coverUri.isNullOrBlank()) return@withContext null
      val file =
        when {
          coverUri.startsWith("file://") ->
            File(Uri.parse(coverUri).path ?: return@withContext null)
          coverUri.startsWith("/") -> File(coverUri)
          else -> return@withContext null
        }
      if (!file.exists() || !file.isFile) return@withContext null
      runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
          bounds.outWidth to bounds.outHeight
        } else {
          null
        }
      }.getOrNull()
    }

  private suspend fun downloadAndImportWithDimensions(imageUrl: String): Pair<String, Int>? =
    withContext(Dispatchers.IO) {
      val cleanUrl = imageUrl.trim()
      if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) {
        return@withContext null
      }
      var tempSourceFile: File? = null
      try {
        val request =
          okhttp3.Request.Builder()
            .url(cleanUrl)
            .header("Accept", "image/*")
            .get()
            .build()
        val tempFile = File.createTempFile("cover_dl_", ".tmp", context.cacheDir)
        tempSourceFile = tempFile
        okHttpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          val body = response.body ?: return@withContext null
          body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
          }
        }
        val length = tempFile.length()
        if (!tempFile.exists() || length < 256L) {
          return@withContext null
        }
        // Reject tiny placeholder responses (~9KB "image not available" PNG) on Google Books HD endpoints
        val isGoogleHdProbe =
          cleanUrl.contains("books.google.com") &&
            (cleanUrl.contains("fife=w") || cleanUrl.contains("zoom=0") || cleanUrl.contains("frontcovers"))
        if (isGoogleHdProbe && length in 1L..11_000L) {
          return@withContext null
        }
        decodeAndSaveTempFile(tempFile, minWidth = 50, minHeight = 60)
      } catch (_: Throwable) {
        null
      } finally {
        tempSourceFile?.delete()
      }
    }

  private fun decodeAndSaveTempFile(
    tempFile: File,
    minWidth: Int,
    minHeight: Int,
  ): Pair<String, Int>? {
    val decoded = decodeScaledBitmap(tempFile) ?: return null
    val actualWidth = decoded.width
    val actualHeight = decoded.height
    if (actualWidth < minWidth || actualHeight < minHeight) {
      decoded.recycle()
      return null
    }
    val targetDir = coversDir.apply { mkdirs() }
    val outFile = File(targetDir, "cover_${UUID.randomUUID()}.jpg")
    return try {
      val scaled = downscaleIfNeeded(decoded, MAX_DIMENSION_PX)
      val savedWidth = scaled.width
      try {
        FileOutputStream(outFile).use { out ->
          scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
      } finally {
        if (scaled !== decoded) {
          scaled.recycle()
        }
        decoded.recycle()
      }
      if (outFile.exists() && outFile.length() > 0L) outFile.absolutePath to savedWidth else null
    } catch (t: Throwable) {
      outFile.delete()
      throw t
    }
  }

  private fun decodeScaledBitmap(sourceFile: File): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val decodedP =
        runCatching {
            val source = ImageDecoder.createSource(sourceFile)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
              val width = info.size.width
              val height = info.size.height
              val maxSide = maxOf(width, height)
              if (maxSide > MAX_DIMENSION_PX && width > 0 && height > 0) {
                val ratio = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                decoder.setTargetSize(
                  (width * ratio).toInt().coerceAtLeast(1),
                  (height * ratio).toInt().coerceAtLeast(1),
                )
              }
            }
          }
          .getOrNull()
      if (decodedP != null) return decodedP
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null
    }
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
  }

  override suspend fun deleteCoverIfManaged(coverUri: String?) {
    if (coverUri.isNullOrBlank()) return
    withContext(Dispatchers.IO) {
      runCatching {
        val candidate =
          if (coverUri.startsWith("file://")) File(Uri.parse(coverUri).path ?: return@withContext)
          else File(coverUri)
        val canonicalCovers = coversDir.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        if (
          canonicalCandidate.parentFile == canonicalCovers &&
            canonicalCandidate.exists() &&
            canonicalCandidate.isFile
        ) {
          canonicalCandidate.delete()
        }
      }
    }
  }

  private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sampleSize = 1
    while ((width / sampleSize) > maxDim * 2 || (height / sampleSize) > maxDim * 2) {
      sampleSize *= 2
    }
    return sampleSize
  }

  private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDim) return bitmap
    val ratio = maxDim.toFloat() / maxSide.toFloat()
    val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
  }

  private companion object {
    const val MAX_DIMENSION_PX = 1440
    const val JPEG_QUALITY = 90
    const val DEFAULT_PREFERRED_MIN_WIDTH_PX = 300
  }
}
