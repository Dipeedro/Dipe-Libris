package com.example.core.data.local.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CoverStorageManager {
  suspend fun importCoverFromUri(sourceUri: Uri): String?

  suspend fun deleteCoverIfManaged(coverUri: String?)
}

class InternalCoverStorageManager(private val context: Context) : CoverStorageManager {

  private val coversDir: File
    get() = File(context.filesDir, "covers")

  override suspend fun importCoverFromUri(sourceUri: Uri): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
          return@withContext null
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
          resolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
          } ?: return@withContext null

        val targetDir = coversDir.apply { mkdirs() }
        val outFile = File(targetDir, "cover_${UUID.randomUUID()}.jpg")
        try {
          val scaled = downscaleIfNeeded(decoded, MAX_DIMENSION_PX)
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
          outFile.absolutePath
        } catch (t: Throwable) {
          outFile.delete()
          throw t
        }
      }.getOrNull()
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
    const val MAX_DIMENSION_PX = 1080
    const val JPEG_QUALITY = 88
  }
}
