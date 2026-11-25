package com.example.core.data.local.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Detects invalid responses and Google Books "image not available" PNG/JPEG placeholders
 * (which Google serves with HTTP 200 and dimensions > 48px, bypassing tiny-stub heuristics).
 */
object CoverPlaceholderDetector {

  private const val MIN_VALID_BYTES = 256L
  private const val GOOGLE_HD_PLACEHOLDER_MAX_BYTES = 11_000L
  private const val MIN_VALID_DIMENSION_PX = 49

  fun isGoogleBooksHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return "books.google." in lower || "googleusercontent.com" in lower
  }

  fun isGoogleHdProbeUrl(url: String?): Boolean {
    if (!isGoogleBooksHost(url)) return false
    val lower = url!!.lowercase()
    return "fife=w" in lower || "zoom=0" in lower || "/frontcovers/" in lower
  }

  fun isValidImageContentType(contentType: String?): Boolean {
    val clean = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return true
    return clean.startsWith("image/")
  }

  fun isGooglePlaceholderBytes(
    url: String?,
    contentType: String?,
    bytes: ByteArray,
  ): Boolean {
    if (!isValidImageContentType(contentType)) return true
    if (bytes.size < MIN_VALID_BYTES) return true
    if (isGoogleHdProbeUrl(url) && bytes.size <= GOOGLE_HD_PLACEHOLDER_MAX_BYTES) return true

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth < MIN_VALID_DIMENSION_PX || bounds.outHeight < MIN_VALID_DIMENSION_PX) {
      return true
    }

    if (!isGoogleBooksHost(url)) return false

    val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDim = 96)
    val opts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return true
    return try {
      isKnownGooglePlaceholderBitmap(bmp)
    } finally {
      bmp.recycle()
    }
  }

  fun isGooglePlaceholderFile(
    url: String?,
    contentType: String?,
    file: File,
  ): Boolean {
    if (!isValidImageContentType(contentType)) return true
    if (!file.exists()) return true
    val length = file.length()
    if (length < MIN_VALID_BYTES) return true
    if (isGoogleHdProbeUrl(url) && length <= GOOGLE_HD_PLACEHOLDER_MAX_BYTES) return true

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth < MIN_VALID_DIMENSION_PX || bounds.outHeight < MIN_VALID_DIMENSION_PX) {
      return true
    }

    if (!isGoogleBooksHost(url)) return false

    val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDim = 96)
    val opts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return true
    return try {
      isKnownGooglePlaceholderBitmap(bmp)
    } finally {
      bmp.recycle()
    }
  }

  fun isKnownGooglePlaceholderDrawable(drawable: Drawable?): Boolean {
    val bmp = (drawable as? BitmapDrawable)?.bitmap ?: return false
    return isKnownGooglePlaceholderBitmap(bmp)
  }

  /**
   * Matches Google Books' official "image not available" card geometry and palette:
   * - Almost 100% achromatic (grayscale, chroma <= 14)
   * - Dominant light-gray / off-white background (>= 72% with luminance >= 205)
   * - Clean outer border (>= 88% light background) and clean top/bottom bands (>= 90%)
   * - Almost no deep-black ink (<= 6% luminance < 65), distinguishing it from bold white-cover typography
   * - Small centered band of medium-gray text/border (1%..35% luminance in 75..204)
   */
  fun isKnownGooglePlaceholderBitmap(bitmap: Bitmap): Boolean {
    if (bitmap.isRecycled) return false
    val w = bitmap.width
    val h = bitmap.height
    if (w < MIN_VALID_DIMENSION_PX || h < MIN_VALID_DIMENSION_PX) return true

    val isHardware =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
    var tempSoftwareCopy: Bitmap? = null
    val readableBitmap =
      if (isHardware) {
        tempSoftwareCopy =
          runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return false
        tempSoftwareCopy
      } else {
        bitmap
      }

    return try {
      inspectSoftwareBitmapPixels(readableBitmap)
    } catch (_: Throwable) {
      false
    } finally {
      tempSoftwareCopy?.let { copy ->
        if (!copy.isRecycled) copy.recycle()
      }
    }
  }

  internal fun inspectSoftwareBitmapPixels(bitmap: Bitmap): Boolean {
    if (bitmap.isRecycled) return false
    val w = bitmap.width
    val h = bitmap.height
    if (w < MIN_VALID_DIMENSION_PX || h < MIN_VALID_DIMENSION_PX) return true

    val stepX = max(1, w / 48)
    val stepY = max(1, h / 72)

    var total = 0
    var achromatic = 0
    var lightBg = 0
    var veryDark = 0

    var borderTotal = 0
    var borderLight = 0

    var topBottomTotal = 0
    var topBottomLight = 0

    var centerTotal = 0
    var centerMediumGray = 0

    val borderMinX = (w * 0.14f).toInt()
    val borderMaxX = (w * 0.86f).toInt()
    val borderMinY = (h * 0.14f).toInt()
    val borderMaxY = (h * 0.86f).toInt()

    val topCutoffY = (h * 0.22f).toInt()
    val bottomCutoffY = (h * 0.78f).toInt()

    val centerMinX = (w * 0.12f).toInt()
    val centerMaxX = (w * 0.88f).toInt()
    val centerMinY = (h * 0.25f).toInt()
    val centerMaxY = (h * 0.75f).toInt()

    var y = 0
    while (y < h) {
      val isTopBottom = y < topCutoffY || y > bottomCutoffY
      val isBorderY = y < borderMinY || y > borderMaxY
      val isCenterY = y in centerMinY..centerMaxY
      var x = 0
      while (x < w) {
        val px = bitmap.getPixel(x, y)
        val alpha = Color.alpha(px)
        val r = Color.red(px)
        val g = Color.green(px)
        val b = Color.blue(px)
        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        // Treat transparent corners in rounded placeholder PNGs as light background
        val lum = if (alpha < 24) 245 else (r * 299 + g * 587 + b * 114) / 1000
        val isAchromatic = alpha < 24 || chroma <= 14
        val isLight = isAchromatic && lum >= 205
        val isMedGray = isAchromatic && lum in 75..204

        total++
        if (isAchromatic) achromatic++
        if (isLight) lightBg++
        if (lum < 65 && alpha >= 24) veryDark++

        if (isBorderY || x < borderMinX || x > borderMaxX) {
          borderTotal++
          if (isLight) borderLight++
        }
        if (isTopBottom) {
          topBottomTotal++
          if (isLight) topBottomLight++
        }
        if (isCenterY && x in centerMinX..centerMaxX) {
          centerTotal++
          if (isMedGray) centerMediumGray++
        }
        x += stepX
      }
      y += stepY
    }

    if (total == 0) return true

    val achromaticRatio = achromatic.toFloat() / total
    val lightRatio = lightBg.toFloat() / total
    val veryDarkRatio = veryDark.toFloat() / total
    val borderLightRatio = if (borderTotal > 0) borderLight.toFloat() / borderTotal else 1f
    val topBottomLightRatio = if (topBottomTotal > 0) topBottomLight.toFloat() / topBottomTotal else 1f
    val centerMedGrayRatio = if (centerTotal > 0) centerMediumGray.toFloat() / centerTotal else 0f

    return achromaticRatio >= 0.96f &&
      lightRatio >= 0.72f &&
      borderLightRatio >= 0.88f &&
      topBottomLightRatio >= 0.90f &&
      veryDarkRatio <= 0.06f &&
      centerMedGrayRatio in 0.01f..0.35f
  }

  private fun computeSampleSize(width: Int, height: Int, targetMaxDim: Int): Int {
    var sample = 1
    while ((width / sample) > targetMaxDim * 2 || (height / sample) > targetMaxDim * 2) {
      sample *= 2
    }
    return sample
  }
}
