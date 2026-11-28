package com.example.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.core.designsystem.DipeCoverPaletteEntry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Immutable
enum class BackArtworkMotif {
  HORIZON_BANDS,
  CENTER_MEDALLION,
  DIAGONAL_SPLIT,
  EDITORIAL_FRAME,
  ASYMMETRIC_FIELD,
}

@Immutable
enum class SpatialQuadrant {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  CENTER,
}

@Immutable
data class BookCoverMood(
  val isDark: Boolean,
  val isLight: Boolean,
  val warmth: Float,
  val saturationLevel: Float,
  val contrastLevel: Float,
)

@Immutable
data class BookCoverSpatialProfile(
  val topBand: Color,
  val midBand: Color,
  val bottomBand: Color,
  val leftColor: Color,
  val rightColor: Color,
  val centerColor: Color,
  val outerColor: Color,
  val topVsBottomContrast: Float,
  val leftVsRightContrast: Float,
  val centerProminence: Float,
  val heaviestQuadrant: SpatialQuadrant,
)

@Immutable
data class BookCoverPalette(
  val dominant: Color,
  val secondary: Color,
  val accent: Color,
  val supportingNeutral: Color,
  val edgePalette: BookCoverEdgePalette,
)

@Immutable
data class BookCoverVisualAnalysis(
  val palette: BookCoverPalette,
  val spatial: BookCoverSpatialProfile,
  val mood: BookCoverMood,
  val motif: BackArtworkMotif,
  val compositionSeed: Int,
)

internal object BookCoverAnalyzer {
  private data class CachedEntry(
    val signature: String,
    val analysis: BookCoverVisualAnalysis,
  )

  private const val CACHE_CAPACITY = 96
  private val cache = LruCache<String, CachedEntry>(CACHE_CAPACITY)
  private val lock = Any()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val analysisDispatcher = Dispatchers.Default.limitedParallelism(2)
  private val concurrencyGate = Semaphore(permits = 2)

  fun getCached(
    cacheKey: String?,
    signature: String? = null,
  ): BookCoverVisualAnalysis? {
    if (cacheKey == null) return null
    val entry = synchronized(lock) { cache.get(cacheKey) } ?: return null
    if (signature != null && entry.signature != signature) {
      return null
    }
    return entry.analysis
  }

  fun invalidate(cacheKey: String?) {
    if (cacheKey == null) return
    synchronized(lock) { cache.remove(cacheKey) }
  }

  fun clearCache() {
    synchronized(lock) { cache.evictAll() }
  }

  fun signatureForDrawable(drawable: Drawable?): String? {
    if (drawable == null) return null
    return if (drawable is BitmapDrawable) {
      val bmp = drawable.bitmap ?: return null
      signatureForBitmap(bmp)
    } else {
      "drawable:${drawable.intrinsicWidth}x${drawable.intrinsicHeight}:${System.identityHashCode(drawable)}"
    }
  }

  fun signatureForBitmap(bitmap: Bitmap?): String? {
    if (bitmap == null || bitmap.isRecycled) return null
    return "bmp:${bitmap.width}x${bitmap.height}:${System.identityHashCode(bitmap)}"
  }

  suspend fun analyzeDrawableBounded(
    cacheKey: String?,
    drawable: Drawable?,
    prioritize: Boolean,
    lazyStaggerMs: Long = 0L,
  ): BookCoverVisualAnalysis? {
    if (drawable == null) return null
    val signature = signatureForDrawable(drawable)
    getCached(cacheKey, signature)?.let { return it }

    if (!prioritize && lazyStaggerMs > 0L) {
      delay(lazyStaggerMs.coerceIn(0L, 280L))
      getCached(cacheKey, signature)?.let { return it }
    }

    return withContext(analysisDispatcher) {
      concurrencyGate.withPermit {
        getCached(cacheKey, signature)?.let { return@withPermit it }
        val result = extractFromDrawableSync(cacheKey = cacheKey, drawable = drawable)
        result
      }
    }
  }

  fun extractFromDrawableSync(
    cacheKey: String?,
    drawable: Drawable?,
  ): BookCoverVisualAnalysis? {
    if (drawable == null) return null
    val signature = signatureForDrawable(drawable) ?: return null
    getCached(cacheKey, signature)?.let { return it }

    val analysis =
      if (drawable is BitmapDrawable) {
        val bmp = drawable.bitmap ?: return null
        analyzeBitmapSafely(bmp)
      } else {
        analyzeGenericDrawable(drawable)
      }

    if (analysis != null && cacheKey != null) {
      synchronized(lock) {
        cache.put(cacheKey, CachedEntry(signature = signature, analysis = analysis))
      }
    }
    return analysis
  }

  private fun analyzeBitmapSafely(bitmap: Bitmap): BookCoverVisualAnalysis? {
    if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) return null
    val isHardware =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
    var tempCopy: Bitmap? = null
    val readable =
      if (isHardware) {
        tempCopy =
          runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
        tempCopy
      } else {
        bitmap
      }
    return try {
      analyzeSoftwareBitmap(readable)
    } catch (_: Throwable) {
      null
    } finally {
      tempCopy?.let { if (!it.isRecycled) it.recycle() }
    }
  }

  private fun analyzeGenericDrawable(drawable: Drawable): BookCoverVisualAnalysis? {
    val targetW = 16
    val targetH = 24
    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    return try {
      val canvas = AndroidCanvas(bmp)
      val prevBounds = drawable.copyBounds()
      drawable.setBounds(0, 0, targetW, targetH)
      drawable.draw(canvas)
      drawable.bounds = prevBounds
      analyzeSoftwareBitmap(bmp)
    } catch (_: Throwable) {
      null
    } finally {
      if (!bmp.isRecycled) bmp.recycle()
    }
  }

  private data class SamplePoint(
    val r: Float,
    val g: Float,
    val b: Float,
    val xf: Float,
    val yf: Float,
    val lum: Float,
    val sat: Float,
    val hueDeg: Float,
    val isPerimeter: Boolean,
  )

  private data class ColorBucket(
    var rSum: Float = 0f,
    var gSum: Float = 0f,
    var bSum: Float = 0f,
    var weightSum: Float = 0f,
    var satSum: Float = 0f,
    var count: Int = 0,
  ) {
    fun add(
      r: Float,
      g: Float,
      b: Float,
      sat: Float,
      weight: Float,
    ) {
      rSum += r * weight
      gSum += g * weight
      bSum += b * weight
      satSum += sat * weight
      weightSum += weight
      count++
    }

    fun averageColor(): Color {
      val denom = weightSum.coerceAtLeast(1e-4f)
      return Color(
        red = (rSum / denom).coerceIn(0f, 1f),
        green = (gSum / denom).coerceIn(0f, 1f),
        blue = (bSum / denom).coerceIn(0f, 1f),
        alpha = 1f,
      )
    }

    fun averageSat(): Float = (satSum / weightSum.coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
  }

  internal fun analyzeSoftwareBitmap(bitmap: Bitmap): BookCoverVisualAnalysis? {
    if (bitmap.isRecycled) return null
    val w = bitmap.width
    val h = bitmap.height
    if (w < 2 || h < 2) return null

    val extractedEdgePalette = CoverEdgeColorExtractor.extractFromSoftwareBitmap(bitmap)

    val cols = 8
    val rows = 10
    val rawSamples = ArrayList<SamplePoint>(cols * rows)
    val hsv = FloatArray(3)

    for (ri in 0 until rows) {
      val yf = 0.06f + (0.88f * (ri / (rows - 1f)))
      val py = (yf * (h - 1)).toInt().coerceIn(0, h - 1)
      for (ci in 0 until cols) {
        val xf = 0.06f + (0.88f * (ci / (cols - 1f)))
        val px = (xf * (w - 1)).toInt().coerceIn(0, w - 1)
        val argb = bitmap.getPixel(px, py)
        if (AndroidColor.alpha(argb) < 128) continue
        val rInt = AndroidColor.red(argb)
        val gInt = AndroidColor.green(argb)
        val bInt = AndroidColor.blue(argb)
        AndroidColor.RGBToHSV(rInt, gInt, bInt, hsv)
        val rf = rInt / 255f
        val gf = gInt / 255f
        val bf = bInt / 255f
        val lum = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
        val isPerimeter = ri == 0 || ri == rows - 1 || ci == 0 || ci == cols - 1
        rawSamples.add(
          SamplePoint(
            r = rf,
            g = gf,
            b = bf,
            xf = xf,
            yf = yf,
            lum = lum,
            sat = hsv[1],
            hueDeg = hsv[0],
            isPerimeter = isPerimeter,
          )
        )
      }
    }

    if (rawSamples.isEmpty()) return null

    // Detect thin bright white or pitch-black scan margins around an otherwise richer inner cover
    var perimCount = 0
    var perimWhiteCount = 0
    var perimBlackCount = 0
    var innerLumSum = 0f
    var innerSatSum = 0f
    var innerCount = 0
    for (s in rawSamples) {
      if (s.isPerimeter) {
        perimCount++
        if (s.lum > 0.90f && s.sat < 0.08f) perimWhiteCount++
        if (s.lum < 0.04f && s.sat < 0.12f) perimBlackCount++
      } else {
        innerCount++
        innerLumSum += s.lum
        innerSatSum += s.sat
      }
    }
    val innerMeanLum = if (innerCount > 0) innerLumSum / innerCount else 0.5f
    val innerMeanSat = if (innerCount > 0) innerSatSum / innerCount else 0f
    val stripWhiteBorder =
      perimCount > 0 &&
        innerCount > 0 &&
        perimWhiteCount >= (perimCount * 0.65f) &&
        (innerMeanLum < 0.78f || innerMeanSat > 0.14f)
    val stripBlackBorder =
      perimCount > 0 &&
        innerCount > 0 &&
        perimBlackCount >= (perimCount * 0.65f) &&
        (innerMeanLum > 0.22f || innerMeanSat > 0.16f)

    val activeSamples =
      if (stripWhiteBorder || stripBlackBorder) {
        rawSamples.filterNot { s ->
          s.isPerimeter &&
            ((stripWhiteBorder && s.lum > 0.88f && s.sat < 0.10f) ||
              (stripBlackBorder && s.lum < 0.05f && s.sat < 0.14f))
        }.ifEmpty { rawSamples }
      } else {
        rawSamples
      }

    // 41 perceptual buckets: 0..4 achromatic luminance tiers, 5..40 = 12 hue x 3 lightness bands
    val buckets = Array(41) { ColorBucket() }
    var lumSum = 0f
    var satSum = 0f
    var warmthSum = 0f
    var warmthWeight = 0f

    for (s in activeSamples) {
      lumSum += s.lum
      satSum += s.sat
      val rbWarm = s.r - s.b
      val wWarm = 0.35f + s.sat * 0.65f
      warmthSum += rbWarm * wWarm
      warmthWeight += wWarm

      val bucketIdx =
        if (s.sat < 0.14f) {
          when {
            s.lum < 0.16f -> 0
            s.lum < 0.36f -> 1
            s.lum < 0.60f -> 2
            s.lum < 0.82f -> 3
            else -> 4
          }
        } else {
          val hueIdx = ((s.hueDeg / 30f).toInt()).coerceIn(0, 11)
          val valIdx =
            when {
              s.lum < 0.28f -> 0
              s.lum < 0.64f -> 1
              else -> 2
            }
          5 + hueIdx * 3 + valIdx
        }

      // Slightly boost center/mid-range chromatic presence without erasing background dominance
      val inCenter = s.xf in 0.22f..0.78f && s.yf in 0.20f..0.80f
      val weight = 1f + (if (inCenter) 0.18f else 0f) + (s.sat * 0.22f)
      buckets[bucketIdx].add(s.r, s.g, s.b, s.sat, weight)
    }

    val nonEmpty = buckets.filter { it.count > 0 }.sortedByDescending { it.weightSum }
    val dominantBucket = nonEmpty.first()
    val rawDominant = dominantBucket.averageColor()

    // Merge near-identical color clusters when choosing secondary and accent
    val secondaryBucket =
      nonEmpty
        .drop(1)
        .firstOrNull { perceptualDistance(it.averageColor(), rawDominant) >= 0.17f }
        ?: nonEmpty.drop(1).firstOrNull { perceptualDistance(it.averageColor(), rawDominant) >= 0.10f }

    val rawSecondary =
      secondaryBucket?.averageColor() ?: deriveTonalCompanion(rawDominant)

    val totalWeight = activeSamples.size.toFloat().coerceAtLeast(1f)
    val accentBucket =
      nonEmpty
        .filter { b ->
          val c = b.averageColor()
          perceptualDistance(c, rawDominant) >= 0.20f &&
            perceptualDistance(c, rawSecondary) >= 0.18f
        }
        .maxByOrNull { b ->
          val c = b.averageColor()
          val dDom = perceptualDistance(c, rawDominant)
          val dSec = perceptualDistance(c, rawSecondary)
          val prominence = sqrt((b.weightSum / totalWeight).coerceIn(0f, 1f))
          (b.averageSat() * 0.62f) + (min(dDom, dSec) * 0.45f) + (prominence * 0.28f)
        }
        ?: nonEmpty
          .filter { b ->
            val c = b.averageColor()
            perceptualDistance(c, rawDominant) >= 0.12f &&
              perceptualDistance(c, rawSecondary) >= 0.10f
          }
          .maxByOrNull { it.averageSat() }

    val rawAccent =
      accentBucket?.averageColor() ?: deriveHarmonizedAccent(rawDominant, rawSecondary)

    val meanLum = (lumSum / activeSamples.size).coerceIn(0f, 1f)
    val meanSat = (satSum / activeSamples.size).coerceIn(0f, 1f)
    val warmth = (warmthSum / warmthWeight.coerceAtLeast(1e-4f)).coerceIn(-1f, 1f)

    var lumDevSum = 0f
    for (s in activeSamples) {
      val d = s.lum - meanLum
      lumDevSum += d * d
    }
    val lumStd = sqrt(lumDevSum / activeSamples.size)
    val maxClusterDist =
      if (nonEmpty.size >= 2) {
        val topColors = nonEmpty.take(4).map { it.averageColor() }
        var maxDist = 0f
        for (i in topColors.indices) {
          for (j in i + 1 until topColors.size) {
            maxDist = max(maxDist, perceptualDistance(topColors[i], topColors[j]))
          }
        }
        maxDist
      } else {
        0f
      }
    val contrastLevel = ((lumStd * 2.1f) * 0.55f + maxClusterDist * 0.45f).coerceIn(0f, 1f)

    val dominant = tameEditorialColor(rawDominant, maxSaturation = 0.72f)
    val secondary = tameEditorialColor(rawSecondary, maxSaturation = 0.72f)
    val accent = tameEditorialColor(rawAccent, maxSaturation = 0.78f)
    val supportingNeutral = computeSupportingNeutral(dominant, warmth, isDark = meanLum < 0.34f)

    // Spatial aggregation
    val topBand = averageSamples(activeSamples) { it.yf <= 0.34f } ?: dominant
    val midBand = averageSamples(activeSamples) { it.yf > 0.34f && it.yf <= 0.66f } ?: dominant
    val bottomBand = averageSamples(activeSamples) { it.yf > 0.66f } ?: dominant
    val edgePalette =
      extractedEdgePalette ?: harmonizeCoverEdgePalette(top = topBand, mid = midBand, bottom = bottomBand)
    val leftColor = averageSamples(activeSamples) { it.xf < 0.50f } ?: dominant
    val rightColor = averageSamples(activeSamples) { it.xf >= 0.50f } ?: dominant
    val centerColor =
      averageSamples(activeSamples) { it.xf in 0.25f..0.75f && it.yf in 0.22f..0.78f } ?: midBand
    val outerColor =
      averageSamples(activeSamples) { !(it.xf in 0.25f..0.75f && it.yf in 0.22f..0.78f) } ?: dominant

    val topVsBottomContrast = perceptualDistance(topBand, bottomBand)
    val leftVsRightContrast = perceptualDistance(leftColor, rightColor)
    val centerProminence = perceptualDistance(centerColor, outerColor)

    val qTopLeft = averageSamples(activeSamples) { it.xf < 0.50f && it.yf < 0.50f } ?: dominant
    val qTopRight = averageSamples(activeSamples) { it.xf >= 0.50f && it.yf < 0.50f } ?: dominant
    val qBotLeft = averageSamples(activeSamples) { it.xf < 0.50f && it.yf >= 0.50f } ?: dominant
    val qBotRight = averageSamples(activeSamples) { it.xf >= 0.50f && it.yf >= 0.50f } ?: dominant

    val heaviestQuadrant =
      resolveHeaviestQuadrant(
        meanColor = Color(
          red = activeSamples.map { it.r }.average().toFloat(),
          green = activeSamples.map { it.g }.average().toFloat(),
          blue = activeSamples.map { it.b }.average().toFloat(),
        ),
        qTopLeft = qTopLeft,
        qTopRight = qTopRight,
        qBotLeft = qBotLeft,
        qBotRight = qBotRight,
        centerColor = centerColor,
        centerProminence = centerProminence,
      )

    val mood =
      BookCoverMood(
        isDark = meanLum < 0.34f,
        isLight = meanLum > 0.68f,
        warmth = warmth,
        saturationLevel = meanSat,
        contrastLevel = contrastLevel,
      )

    val spatial =
      BookCoverSpatialProfile(
        topBand = tameEditorialColor(topBand, 0.70f),
        midBand = tameEditorialColor(midBand, 0.70f),
        bottomBand = tameEditorialColor(bottomBand, 0.70f),
        leftColor = tameEditorialColor(leftColor, 0.70f),
        rightColor = tameEditorialColor(rightColor, 0.70f),
        centerColor = tameEditorialColor(centerColor, 0.74f),
        outerColor = tameEditorialColor(outerColor, 0.70f),
        topVsBottomContrast = topVsBottomContrast,
        leftVsRightContrast = leftVsRightContrast,
        centerProminence = centerProminence,
        heaviestQuadrant = heaviestQuadrant,
      )

    val motif =
      selectMotif(
        topVsBottomContrast = topVsBottomContrast,
        leftVsRightContrast = leftVsRightContrast,
        centerProminence = centerProminence,
        mood = mood,
      )

    val seed =
      computeCompositionSeed(
        dominant = dominant,
        secondary = secondary,
        accent = accent,
        quadrant = heaviestQuadrant,
        motif = motif,
      )

    return BookCoverVisualAnalysis(
      palette =
        BookCoverPalette(
          dominant = dominant,
          secondary = secondary,
          accent = accent,
          supportingNeutral = supportingNeutral,
          edgePalette = edgePalette,
        ),
      spatial = spatial,
      mood = mood,
      motif = motif,
      compositionSeed = seed,
    )
  }

  /**
   * Produces a deterministic cover-inspired analysis for typographic cloth-bound covers that do
   * not load an external cover bitmap.
   */
  fun analyzeTypographicCover(
    paletteEntry: DipeCoverPaletteEntry,
    title: String,
    author: String,
  ): BookCoverVisualAnalysis {
    val base = paletteEntry.clothColor
    val isDark = base.luminance() < 0.36f
    val isLight = base.luminance() > 0.64f
    val secondary =
      if (isDark) lerp(base, Color(0xFFE6DEC9), 0.22f)
      else lerp(base, Color(0xFF2B241D), 0.18f)
    val accent =
      if (isDark) lerp(paletteEntry.onCloth, base, 0.28f)
      else lerp(base, Color(0xFF9C432D), 0.38f)
    val supportingNeutral =
      if (isDark) Color(0xFFEFE9DC) else Color(0xFF221D18)

    val edgeTop = lerp(base, Color.White, 0.08f)
    val edgeMid = base
    val edgeBot = lerp(base, Color.Black, 0.10f)
    val edgePalette = BookCoverEdgePalette(top = edgeTop, mid = edgeMid, bottom = edgeBot)

    val hash = ((title.hashCode() * 31) + author.hashCode()) and Int.MAX_VALUE
    val motif =
      when (hash % 4) {
        0 -> BackArtworkMotif.EDITORIAL_FRAME
        1 -> BackArtworkMotif.CENTER_MEDALLION
        2 -> BackArtworkMotif.DIAGONAL_SPLIT
        else -> BackArtworkMotif.ASYMMETRIC_FIELD
      }
    val quadrant =
      when ((hash / 7) % 5) {
        0 -> SpatialQuadrant.TOP_RIGHT
        1 -> SpatialQuadrant.BOTTOM_RIGHT
        2 -> SpatialQuadrant.BOTTOM_LEFT
        3 -> SpatialQuadrant.CENTER
        else -> SpatialQuadrant.TOP_LEFT
      }
    val warmth = (base.red - base.blue).coerceIn(-1f, 1f)
    val mood =
      BookCoverMood(
        isDark = isDark,
        isLight = isLight,
        warmth = warmth,
        saturationLevel = 0.28f,
        contrastLevel = 0.22f,
      )
    val spatial =
      BookCoverSpatialProfile(
        topBand = edgeTop,
        midBand = base,
        bottomBand = edgeBot,
        leftColor = lerp(base, secondary, 0.16f),
        rightColor = base,
        centerColor = secondary,
        outerColor = base,
        topVsBottomContrast = 0.10f,
        leftVsRightContrast = 0.10f,
        centerProminence = 0.18f,
        heaviestQuadrant = quadrant,
      )

    return BookCoverVisualAnalysis(
      palette =
        BookCoverPalette(
          dominant = base,
          secondary = secondary,
          accent = accent,
          supportingNeutral = supportingNeutral,
          edgePalette = edgePalette,
        ),
      spatial = spatial,
      mood = mood,
      motif = motif,
      compositionSeed = hash,
    )
  }

  private fun averageSamples(
    samples: List<SamplePoint>,
    predicate: (SamplePoint) -> Boolean,
  ): Color? {
    var rSum = 0f
    var gSum = 0f
    var bSum = 0f
    var count = 0
    for (s in samples) {
      if (predicate(s)) {
        rSum += s.r
        gSum += s.g
        bSum += s.b
        count++
      }
    }
    if (count == 0) return null
    return Color(
      red = (rSum / count).coerceIn(0f, 1f),
      green = (gSum / count).coerceIn(0f, 1f),
      blue = (bSum / count).coerceIn(0f, 1f),
      alpha = 1f,
    )
  }

  private fun resolveHeaviestQuadrant(
    meanColor: Color,
    qTopLeft: Color,
    qTopRight: Color,
    qBotLeft: Color,
    qBotRight: Color,
    centerColor: Color,
    centerProminence: Float,
  ): SpatialQuadrant {
    if (centerProminence >= 0.22f) return SpatialQuadrant.CENTER
    val candidates =
      listOf(
        SpatialQuadrant.TOP_LEFT to perceptualDistance(qTopLeft, meanColor),
        SpatialQuadrant.TOP_RIGHT to perceptualDistance(qTopRight, meanColor),
        SpatialQuadrant.BOTTOM_LEFT to perceptualDistance(qBotLeft, meanColor),
        SpatialQuadrant.BOTTOM_RIGHT to perceptualDistance(qBotRight, meanColor),
      )
    return candidates.maxByOrNull { it.second }?.first ?: SpatialQuadrant.BOTTOM_RIGHT
  }

  private fun selectMotif(
    topVsBottomContrast: Float,
    leftVsRightContrast: Float,
    centerProminence: Float,
    mood: BookCoverMood,
  ): BackArtworkMotif {
    val maxDirectional = max(topVsBottomContrast, leftVsRightContrast)
    return when {
      centerProminence >= maxDirectional * 1.06f && centerProminence >= 0.13f ->
        BackArtworkMotif.CENTER_MEDALLION
      topVsBottomContrast >= leftVsRightContrast * 1.12f && topVsBottomContrast >= 0.11f ->
        BackArtworkMotif.HORIZON_BANDS
      leftVsRightContrast >= 0.11f ->
        BackArtworkMotif.DIAGONAL_SPLIT
      mood.contrastLevel < 0.14f || mood.saturationLevel < 0.15f ->
        BackArtworkMotif.EDITORIAL_FRAME
      else ->
        BackArtworkMotif.ASYMMETRIC_FIELD
    }
  }

  private fun computeCompositionSeed(
    dominant: Color,
    secondary: Color,
    accent: Color,
    quadrant: SpatialQuadrant,
    motif: BackArtworkMotif,
  ): Int {
    fun qByte(c: Color): Int {
      val r = (c.red * 15f).toInt().coerceIn(0, 15)
      val g = (c.green * 15f).toInt().coerceIn(0, 15)
      val b = (c.blue * 15f).toInt().coerceIn(0, 15)
      return (r shl 8) or (g shl 4) or b
    }
    var h = 17
    h = h * 31 + qByte(dominant)
    h = h * 31 + qByte(secondary)
    h = h * 31 + qByte(accent)
    h = h * 31 + quadrant.ordinal
    h = h * 31 + motif.ordinal
    return h and Int.MAX_VALUE
  }

  private fun deriveTonalCompanion(base: Color): Color {
    val lum = base.luminance()
    return if (lum < 0.42f) {
      lerp(base, Color(0xFFE5DCCB), 0.28f)
    } else {
      lerp(base, Color(0xFF28221D), 0.24f)
    }
  }

  private fun deriveHarmonizedAccent(
    dominant: Color,
    secondary: Color,
  ): Color {
    val avgLum = (dominant.luminance() + secondary.luminance()) * 0.5f
    val warmTint = if (dominant.red >= dominant.blue) Color(0xFFB35D3E) else Color(0xFFC49245)
    return if (avgLum < 0.35f) {
      lerp(secondary, warmTint, 0.45f)
    } else {
      lerp(dominant, warmTint, 0.38f)
    }
  }

  private fun computeSupportingNeutral(
    dominant: Color,
    warmth: Float,
    isDark: Boolean,
  ): Color {
    val warmPaper = Color(0xFFEBE3D3)
    val coolPaper = Color(0xFFE4E7EA)
    val warmInk = Color(0xFF221D18)
    val coolInk = Color(0xFF1A2026)
    val neutral =
      if (isDark) {
        if (warmth >= -0.04f) warmPaper else coolPaper
      } else {
        if (warmth >= -0.04f) warmInk else coolInk
      }
    return lerp(neutral, dominant, 0.10f)
  }

  internal fun tameEditorialColor(
    color: Color,
    maxSaturation: Float = 0.72f,
  ): Color {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
      (color.red * 255f).toInt().coerceIn(0, 255),
      (color.green * 255f).toInt().coerceIn(0, 255),
      (color.blue * 255f).toInt().coerceIn(0, 255),
      hsv,
    )
    if (hsv[1] > maxSaturation) {
      hsv[1] = maxSaturation + (hsv[1] - maxSaturation) * 0.30f
    }
    // Slight editorial warmth dampening on ultra-bright channels so back cover never screams neon
    val argb = AndroidColor.HSVToColor(hsv)
    return Color(
      red = AndroidColor.red(argb) / 255f,
      green = AndroidColor.green(argb) / 255f,
      blue = AndroidColor.blue(argb) / 255f,
      alpha = 1f,
    )
  }

  internal fun perceptualDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt(0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db).coerceIn(0f, 1f)
  }
}
