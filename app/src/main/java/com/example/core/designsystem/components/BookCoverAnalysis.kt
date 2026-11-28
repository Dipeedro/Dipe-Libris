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
enum class BackArtworkDesignFamily {
  GEOMETRIC,
  ORGANIC,
  SYMBOLIC,
  ATMOSPHERIC,
  BOTANICAL,
  CELESTIAL,
  ABSTRACT,
  PATTERNED,
  MONOCHROMATIC,
  ILLUSTRATIVE,
}

@Immutable
enum class BackBorderTreatment {
  NONE,
  INNER_HAIRLINE,
  DOUBLE_RULE,
  CORNER_BRACKETS,
}

@Immutable
enum class BackTypographyStyle {
  EDITORIAL_SERIF,
  GEOMETRIC_SANS,
  MINIMAL_QUIET,
  DECORATIVE_BADGE,
}

@Immutable
enum class BackTextureFinish {
  SMOOTH_SATIN,
  SUBTLE_PAPER,
  TACTILE_COTTON,
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
data class BookCoverCompositionMetrics(
  val visualDensity: Float = 0.25f,
  val symmetryScore: Float = 0.50f,
  val diagonalBias: Float = 0.10f,
  val upperQuietness: Float = 0.65f,
  val hueDiversity: Float = 0.35f,
  val botanicalAffinity: Float = 0f,
  val celestialAffinity: Float = 0f,
) {
  companion object {
    val Default = BookCoverCompositionMetrics()
  }
}

@Immutable
data class BackArtworkCompositionSpec(
  val family: BackArtworkDesignFamily,
  val variantIndex: Int,
  val scaleFactor: Float,
  val elementCount: Int,
  val mirrorHorizontal: Boolean,
  val orientationDeg: Float,
  val layerDepthMode: Int,
  val borderTreatment: BackBorderTreatment,
  val typographyStyle: BackTypographyStyle,
  val textureFinish: BackTextureFinish,
  val textureOpacity: Float,
  val negativeSpaceOnTop: Boolean,
  val decorativeAccentMode: Int,
  val rankedFamilies: List<BackArtworkDesignFamily>,
  val designSeed: Int,
)

@Immutable
data class BookCoverPalette(
  val dominant: Color,
  val secondary: Color,
  val accent: Color,
  val supportingNeutral: Color,
  val edgePalette: BookCoverEdgePalette,
  val lightSupport: Color = supportingNeutral,
  val darkContrast: Color = Color(0xFF221D18),
)

@Immutable
data class BookCoverVisualAnalysis(
  val palette: BookCoverPalette,
  val spatial: BookCoverSpatialProfile,
  val mood: BookCoverMood,
  val motif: BackArtworkMotif,
  val compositionSeed: Int,
  val metrics: BookCoverCompositionMetrics = BookCoverCompositionMetrics.Default,
  val defaultComposition: BackArtworkCompositionSpec =
    BookCoverAnalyzer.buildDefaultComposition(
      motif = motif,
      mood = mood,
      spatial = spatial,
      metrics = metrics,
      dominant = palette.dominant,
      seed = compositionSeed,
    ),
)

internal object BookCoverAnalyzer {
  private data class CachedEntry(
    val signature: String,
    val analysis: BookCoverVisualAnalysis,
  )

  private const val CACHE_CAPACITY = 96
  private const val COMPOSITION_CACHE_CAPACITY = 160
  private val cache = LruCache<String, CachedEntry>(CACHE_CAPACITY)
  private val compositionCache = LruCache<String, BackArtworkCompositionSpec>(COMPOSITION_CACHE_CAPACITY)
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
    synchronized(lock) {
      cache.remove(cacheKey)
      compositionCache.evictAll()
    }
  }

  fun clearCache() {
    synchronized(lock) {
      cache.evictAll()
      compositionCache.evictAll()
    }
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
        extractFromDrawableSync(cacheKey = cacheKey, drawable = drawable)
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

  /**
   * Resolves a deterministic, cached [BackArtworkCompositionSpec] for the analyzed cover.
   * When [stableBookKey] is supplied, selects across the top-ranked fitting design families
   * and sub-variants so adjacent editions don't repeat identically while staying 100% deterministic.
   */
  fun resolveCompositionSpec(
    analysis: BookCoverVisualAnalysis,
    stableBookKey: String? = null,
  ): BackArtworkCompositionSpec {
    if (stableBookKey.isNullOrBlank()) return analysis.defaultComposition
    val compKey = "${analysis.compositionSeed}|$stableBookKey"
    synchronized(lock) { compositionCache.get(compKey) }?.let { return it }

    val mixedSeed = mixSeeds(analysis.compositionSeed, stableHash(stableBookKey))
    val candidates = analysis.defaultComposition.rankedFamilies
    // Strongly favor top-ranked fitting family (~55%) while allowing top-2..4 fitting families for variety
    val family =
      if (candidates.size >= 2) {
        val roll = (mixedSeed ushr 2) % 100
        when {
          roll < 52 -> candidates[0]
          roll < 76 -> candidates[1]
          roll < 90 && candidates.size >= 3 -> candidates[2]
          else -> candidates[(candidates.size - 1).coerceAtMost(3)]
        }
      } else {
        analysis.defaultComposition.family
      }
    val spec =
      instantiateSpecForFamily(
        family = family,
        rankedFamilies = candidates,
        mood = analysis.mood,
        spatial = analysis.spatial,
        metrics = analysis.metrics,
        dominant = analysis.palette.dominant,
        seed = mixedSeed,
      )
    synchronized(lock) {
      compositionCache.put(compKey, spec)
    }
    return spec
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
    val targetW = 20
    val targetH = 30
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
    val row: Int,
    val col: Int,
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
    val isChromatic: Boolean = false,
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

    val cols = 10
    val rows = 12
    val grid = Array<SamplePoint?>(rows * cols) { null }
    val rawSamples = ArrayList<SamplePoint>(cols * rows)
    val hsv = FloatArray(3)

    for (ri in 0 until rows) {
      val yf = 0.05f + (0.90f * (ri / (rows - 1f)))
      val py = (yf * (h - 1)).toInt().coerceIn(0, h - 1)
      for (ci in 0 until cols) {
        val xf = 0.05f + (0.90f * (ci / (cols - 1f)))
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
        val point =
          SamplePoint(
            r = rf,
            g = gf,
            b = bf,
            xf = xf,
            yf = yf,
            row = ri,
            col = ci,
            lum = lum,
            sat = hsv[1],
            hueDeg = hsv[0],
            isPerimeter = isPerimeter,
          )
        rawSamples.add(point)
        grid[ri * cols + ci] = point
      }
    }

    if (rawSamples.isEmpty()) return null

    // Strip thin bright white or pitch-black scanner borders around inner artwork
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
        (innerMeanLum < 0.78f || innerMeanSat > 0.12f)
    val stripBlackBorder =
      perimCount > 0 &&
        innerCount > 0 &&
        perimBlackCount >= (perimCount * 0.65f) &&
        (innerMeanLum > 0.22f || innerMeanSat > 0.14f)

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

    // 41 perceptual buckets: 0..4 achromatic luminance tiers (<0.085 sat preserves delicate pastels!), 5..40 = 12 hue x 3 lightness
    val buckets = Array(41) { idx -> ColorBucket(isChromatic = idx >= 5) }
    var lumSum = 0f
    var satSum = 0f
    var warmthSum = 0f
    var warmthWeight = 0f
    var greenFoliageHits = 0
    var nocturnalStarHits = 0
    var goldSparkHits = 0

    for (s in activeSamples) {
      lumSum += s.lum
      satSum += s.sat
      val rbWarm = s.r - s.b
      val wWarm = 0.35f + s.sat * 0.65f
      warmthSum += rbWarm * wWarm
      warmthWeight += wWarm

      if (s.sat >= 0.12f && s.hueDeg in 72f..170f && s.lum in 0.14f..0.76f) {
        greenFoliageHits++
      }
      if (s.hueDeg in 195f..265f && s.lum < 0.32f && s.sat >= 0.16f) {
        nocturnalStarHits++
      }
      if (s.hueDeg in 32f..58f && s.lum > 0.56f && s.sat >= 0.28f) {
        goldSparkHits++
      }

      val bucketIdx =
        if (s.sat < 0.085f) {
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

      val inCenter = s.xf in 0.20f..0.80f && s.yf in 0.20f..0.80f
      val weight = 1f + (if (inCenter) 0.18f else 0f) + (s.sat * 0.32f)
      buckets[bucketIdx].add(s.r, s.g, s.b, s.sat, weight)
    }

    val nonEmpty = buckets.filter { it.count > 0 }.sortedByDescending { it.weightSum }
    val rawTopBucket = nonEmpty.first()
    val topColor = rawTopBucket.averageColor()
    // If near-white paper text borders slightly outnumber a genuine pastel/colored ground (>= 22% weight), let the ground color lead
    val chromaticFallback =
      if (!rawTopBucket.isChromatic && (topColor.luminance() > 0.85f || topColor.luminance() < 0.05f)) {
        nonEmpty.firstOrNull { b ->
          b.isChromatic && b.weightSum >= rawTopBucket.weightSum * 0.48f
        }
      } else {
        null
      }
    val dominantBucket = chromaticFallback ?: rawTopBucket
    val rawDominant = dominantBucket.averageColor()

    val secondaryBucket =
      nonEmpty
        .filter { it !== dominantBucket }
        .firstOrNull { perceptualDistance(it.averageColor(), rawDominant) >= 0.16f }
        ?: nonEmpty.filter { it !== dominantBucket }.firstOrNull {
          perceptualDistance(it.averageColor(), rawDominant) >= 0.09f
        }

    val rawSecondary =
      secondaryBucket?.averageColor() ?: deriveTonalCompanion(rawDominant)

    val totalWeight = activeSamples.size.toFloat().coerceAtLeast(1f)
    val accentBucket =
      nonEmpty
        .filter { b ->
          val c = b.averageColor()
          perceptualDistance(c, rawDominant) >= 0.18f &&
            perceptualDistance(c, rawSecondary) >= 0.16f
        }
        .maxByOrNull { b ->
          val c = b.averageColor()
          val dDom = perceptualDistance(c, rawDominant)
          val dSec = perceptualDistance(c, rawSecondary)
          val prominence = sqrt((b.weightSum / totalWeight).coerceIn(0f, 1f))
          (b.averageSat() * 0.72f) + (min(dDom, dSec) * 0.42f) + (prominence * 0.24f)
        }
        ?: nonEmpty
          .filter { b ->
            val c = b.averageColor()
            perceptualDistance(c, rawDominant) >= 0.11f &&
              perceptualDistance(c, rawSecondary) >= 0.09f
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

    // Preserve cover vibrancy and pastel clarity; only tame extreme neon > 0.83 saturation
    val dominant = tameEditorialColor(rawDominant, maxSaturation = 0.83f)
    val secondary = tameEditorialColor(rawSecondary, maxSaturation = 0.83f)
    val accent = tameEditorialColor(rawAccent, maxSaturation = 0.86f)
    val lightSupport = deriveLightSupportColor(dominant, secondary, warmth)
    val darkContrast = deriveDarkContrastColor(dominant, secondary, warmth)
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
        meanColor =
          Color(
            red = activeSamples.map { it.r }.average().toFloat(),
            green = activeSamples.map { it.g }.average().toFloat(),
            blue = activeSamples.map { it.b }.average().toFloat(),
          ),
        qTopLeft = qTopLeft,
        qTopRight = qTopRight,
        qBotLeft = qBotLeft,
        qBotRight = qBotRight,
        centerProminence = centerProminence,
      )

    // Visual structure metrics (density, horizontal symmetry, upper quietness, diagonal bias, hue diversity)
    var neighborDiffSum = 0f
    var neighborPairs = 0
    var topDiffSum = 0f
    var topPairs = 0
    var symDiffSum = 0f
    var symPairs = 0

    for (ri in 0 until rows) {
      for (ci in 0 until cols) {
        val curr = grid[ri * cols + ci] ?: continue
        if (ci + 1 < cols) {
          grid[ri * cols + ci + 1]?.let { right ->
            val d = colorDistRaw(curr, right)
            neighborDiffSum += d
            neighborPairs++
            if (ri <= 3) {
              topDiffSum += d
              topPairs++
            }
          }
        }
        if (ri + 1 < rows) {
          grid[(ri + 1) * cols + ci]?.let { down ->
            val d = colorDistRaw(curr, down)
            neighborDiffSum += d
            neighborPairs++
            if (ri <= 2) {
              topDiffSum += d
              topPairs++
            }
          }
        }
        if (ci < cols / 2) {
          grid[ri * cols + (cols - 1 - ci)]?.let { mirror ->
            symDiffSum += colorDistRaw(curr, mirror)
            symPairs++
          }
        }
      }
    }

    val visualDensity =
      if (neighborPairs > 0) ((neighborDiffSum / neighborPairs) * 2.8f).coerceIn(0f, 1f) else 0.1f
    val topDensity =
      if (topPairs > 0) ((topDiffSum / topPairs) * 2.8f).coerceIn(0f, 1f) else 0.1f
    val upperQuietness = (1f - topDensity).coerceIn(0f, 1f)
    val symmetryScore =
      if (symPairs > 0) (1f - ((symDiffSum / symPairs) * 2.5f)).coerceIn(0f, 1f) else 0.5f
    val diagA = averageSamples(activeSamples) { (it.xf < 0.5f && it.yf < 0.5f) || (it.xf >= 0.5f && it.yf >= 0.5f) } ?: dominant
    val diagB = averageSamples(activeSamples) { (it.xf >= 0.5f && it.yf < 0.5f) || (it.xf < 0.5f && it.yf >= 0.5f) } ?: dominant
    val diagonalBias = perceptualDistance(diagA, diagB)

    val activeHueGroups = (0 until 12).count { hIdx ->
      val groupWeight =
        buckets[5 + hIdx * 3].weightSum +
          buckets[5 + hIdx * 3 + 1].weightSum +
          buckets[5 + hIdx * 3 + 2].weightSum
      groupWeight >= totalWeight * 0.08f
    }
    val hueDiversity = (activeHueGroups / 5f).coerceIn(0f, 1f)

    val activeCountF = activeSamples.size.toFloat().coerceAtLeast(1f)
    val foliageFraction = greenFoliageHits / activeCountF
    val botanicalAffinity =
      (foliageFraction * 1.45f + (if (warmth > -0.10f && visualDensity > 0.20f) 0.12f else 0f))
        .coerceIn(0f, 1f)
    val nocturnalFraction = nocturnalStarHits / activeCountF
    val goldFraction = goldSparkHits / activeCountF
    val celestialAffinity =
      (nocturnalFraction * 1.35f + (if (meanLum < 0.34f && goldFraction > 0.03f) 0.30f else 0f))
        .coerceIn(0f, 1f)

    val metrics =
      BookCoverCompositionMetrics(
        visualDensity = visualDensity,
        symmetryScore = symmetryScore,
        diagonalBias = diagonalBias,
        upperQuietness = upperQuietness,
        hueDiversity = hueDiversity,
        botanicalAffinity = botanicalAffinity,
        celestialAffinity = celestialAffinity,
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
        topBand = tameEditorialColor(topBand, 0.80f),
        midBand = tameEditorialColor(midBand, 0.80f),
        bottomBand = tameEditorialColor(bottomBand, 0.80f),
        leftColor = tameEditorialColor(leftColor, 0.80f),
        rightColor = tameEditorialColor(rightColor, 0.80f),
        centerColor = tameEditorialColor(centerColor, 0.82f),
        outerColor = tameEditorialColor(outerColor, 0.80f),
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
        metrics = metrics,
      )

    val defaultComposition =
      buildDefaultComposition(
        motif = motif,
        mood = mood,
        spatial = spatial,
        metrics = metrics,
        dominant = dominant,
        seed = seed,
      )

    return BookCoverVisualAnalysis(
      palette =
        BookCoverPalette(
          dominant = dominant,
          secondary = secondary,
          accent = accent,
          supportingNeutral = supportingNeutral,
          edgePalette = edgePalette,
          lightSupport = lightSupport,
          darkContrast = darkContrast,
        ),
      spatial = spatial,
      mood = mood,
      motif = motif,
      compositionSeed = seed,
      metrics = metrics,
      defaultComposition = defaultComposition,
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
    val warmth = (base.red - base.blue).coerceIn(-1f, 1f)
    val secondary = deriveTonalCompanion(base)
    val accent =
      if (isDark) lerp(paletteEntry.onCloth, deriveHarmonizedAccent(base, secondary), 0.55f)
      else deriveHarmonizedAccent(base, secondary)
    val lightSupport = deriveLightSupportColor(base, secondary, warmth)
    val darkContrast = deriveDarkContrastColor(base, secondary, warmth)
    val supportingNeutral =
      if (isDark) lightSupport else darkContrast

    val edgeTop = lerp(base, lightSupport, 0.14f)
    val edgeMid = base
    val edgeBot = lerp(base, darkContrast, 0.14f)
    val edgePalette = BookCoverEdgePalette(top = edgeTop, mid = edgeMid, bottom = edgeBot)

    val hash = mixSeeds(stableHash(title), stableHash(author))
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
    val mood =
      BookCoverMood(
        isDark = isDark,
        isLight = isLight,
        warmth = warmth,
        saturationLevel = 0.28f,
        contrastLevel = 0.20f,
      )
    val spatial =
      BookCoverSpatialProfile(
        topBand = edgeTop,
        midBand = base,
        bottomBand = edgeBot,
        leftColor = lerp(base, secondary, 0.18f),
        rightColor = base,
        centerColor = secondary,
        outerColor = base,
        topVsBottomContrast = 0.10f,
        leftVsRightContrast = 0.10f,
        centerProminence = 0.18f,
        heaviestQuadrant = quadrant,
      )
    val metrics =
      BookCoverCompositionMetrics(
        visualDensity = 0.16f + ((hash % 25) / 100f),
        symmetryScore = 0.55f + ((hash % 30) / 100f),
        diagonalBias = 0.12f,
        upperQuietness = 0.80f,
        hueDiversity = 0.20f,
        botanicalAffinity = if (base.green > base.red + 0.04f) 0.45f else 0.08f,
        celestialAffinity = if (isDark && base.blue > base.red + 0.04f) 0.48f else 0.05f,
      )
    val defaultComposition =
      buildDefaultComposition(
        motif = motif,
        mood = mood,
        spatial = spatial,
        metrics = metrics,
        dominant = base,
        seed = hash,
      )

    return BookCoverVisualAnalysis(
      palette =
        BookCoverPalette(
          dominant = base,
          secondary = secondary,
          accent = accent,
          supportingNeutral = supportingNeutral,
          edgePalette = edgePalette,
          lightSupport = lightSupport,
          darkContrast = darkContrast,
        ),
      spatial = spatial,
      mood = mood,
      motif = motif,
      compositionSeed = hash,
      metrics = metrics,
      defaultComposition = defaultComposition,
    )
  }

  internal fun buildDefaultComposition(
    motif: BackArtworkMotif,
    mood: BookCoverMood,
    spatial: BookCoverSpatialProfile,
    metrics: BookCoverCompositionMetrics,
    dominant: Color,
    seed: Int,
  ): BackArtworkCompositionSpec {
    val ranked =
      rankDesignFamilies(
        motif = motif,
        mood = mood,
        spatial = spatial,
        metrics = metrics,
        dominant = dominant,
      )
    val primaryFamily = ranked.firstOrNull() ?: BackArtworkDesignFamily.GEOMETRIC
    return instantiateSpecForFamily(
      family = primaryFamily,
      rankedFamilies = ranked,
      mood = mood,
      spatial = spatial,
      metrics = metrics,
      dominant = dominant,
      seed = seed,
    )
  }

  internal fun rankDesignFamilies(
    motif: BackArtworkMotif,
    mood: BookCoverMood,
    spatial: BookCoverSpatialProfile,
    metrics: BookCoverCompositionMetrics,
    dominant: Color,
  ): List<BackArtworkDesignFamily> {
    val scores = HashMap<BackArtworkDesignFamily, Float>(10)
    for (f in BackArtworkDesignFamily.entries) {
      scores[f] = 0.15f
    }

    fun add(family: BackArtworkDesignFamily, delta: Float) {
      scores[family] = (scores[family] ?: 0f) + delta
    }

    // Spatial structural fit
    when (motif) {
      BackArtworkMotif.HORIZON_BANDS -> {
        add(BackArtworkDesignFamily.ATMOSPHERIC, 0.45f)
        add(BackArtworkDesignFamily.ILLUSTRATIVE, 0.36f)
        add(BackArtworkDesignFamily.ORGANIC, 0.32f)
      }
      BackArtworkMotif.CENTER_MEDALLION -> {
        add(BackArtworkDesignFamily.SYMBOLIC, 0.48f)
        add(BackArtworkDesignFamily.CELESTIAL, 0.28f)
        add(BackArtworkDesignFamily.ILLUSTRATIVE, 0.24f)
      }
      BackArtworkMotif.DIAGONAL_SPLIT -> {
        add(BackArtworkDesignFamily.GEOMETRIC, 0.48f)
        add(BackArtworkDesignFamily.ABSTRACT, 0.36f)
      }
      BackArtworkMotif.EDITORIAL_FRAME -> {
        add(BackArtworkDesignFamily.MONOCHROMATIC, 0.44f)
        add(BackArtworkDesignFamily.SYMBOLIC, 0.26f)
        add(BackArtworkDesignFamily.GEOMETRIC, 0.22f)
      }
      BackArtworkMotif.ASYMMETRIC_FIELD -> {
        add(BackArtworkDesignFamily.ABSTRACT, 0.40f)
        add(BackArtworkDesignFamily.ORGANIC, 0.35f)
        add(BackArtworkDesignFamily.PATTERNED, 0.25f)
      }
    }

    // Subject & material affinities
    if (metrics.botanicalAffinity >= 0.26f) {
      add(BackArtworkDesignFamily.BOTANICAL, 0.55f * metrics.botanicalAffinity + 0.25f)
      add(BackArtworkDesignFamily.ORGANIC, 0.24f)
    }
    if (metrics.celestialAffinity >= 0.24f || (mood.isDark && dominant.blue > dominant.red + 0.08f)) {
      add(BackArtworkDesignFamily.CELESTIAL, 0.52f * metrics.celestialAffinity + 0.26f)
      add(BackArtworkDesignFamily.ATMOSPHERIC, 0.20f)
    }
    if (metrics.hueDiversity <= 0.28f || mood.contrastLevel < 0.16f) {
      add(BackArtworkDesignFamily.MONOCHROMATIC, 0.42f)
    }
    if (metrics.symmetryScore >= 0.62f) {
      add(BackArtworkDesignFamily.SYMBOLIC, 0.22f)
      add(BackArtworkDesignFamily.PATTERNED, 0.16f)
    } else if (metrics.symmetryScore < 0.45f) {
      add(BackArtworkDesignFamily.ABSTRACT, 0.24f)
      add(BackArtworkDesignFamily.ORGANIC, 0.20f)
    }
    if (metrics.visualDensity >= 0.38f && metrics.hueDiversity >= 0.40f) {
      add(BackArtworkDesignFamily.PATTERNED, 0.38f)
      add(BackArtworkDesignFamily.ILLUSTRATIVE, 0.26f)
    }
    if (metrics.diagonalBias >= 0.16f) {
      add(BackArtworkDesignFamily.GEOMETRIC, 0.26f)
      add(BackArtworkDesignFamily.ABSTRACT, 0.18f)
    }
    if (mood.saturationLevel >= 0.45f && mood.contrastLevel >= 0.32f) {
      add(BackArtworkDesignFamily.GEOMETRIC, 0.16f)
      add(BackArtworkDesignFamily.ABSTRACT, 0.16f)
    }
    if (mood.saturationLevel < 0.35f && spatial.topVsBottomContrast >= 0.12f) {
      add(BackArtworkDesignFamily.ATMOSPHERIC, 0.24f)
    }

    return scores.entries
      .sortedWith(compareByDescending<Map.Entry<BackArtworkDesignFamily, Float>> { it.value }.thenBy { it.key.ordinal })
      .take(4)
      .map { it.key }
  }

  private fun instantiateSpecForFamily(
    family: BackArtworkDesignFamily,
    rankedFamilies: List<BackArtworkDesignFamily>,
    mood: BookCoverMood,
    spatial: BookCoverSpatialProfile,
    metrics: BookCoverCompositionMetrics,
    dominant: Color,
    seed: Int,
  ): BackArtworkCompositionSpec {
    val variantIndex = (seed ushr 3) and 3
    val scaleFactor = 0.84f + ((seed ushr 5) and 0xF) * (0.34f / 15f)
    val elementCount = 2 + ((seed ushr 9) % 4)
    val mirrorHorizontal =
      when (spatial.heaviestQuadrant) {
        SpatialQuadrant.TOP_LEFT, SpatialQuadrant.BOTTOM_LEFT -> false
        SpatialQuadrant.TOP_RIGHT, SpatialQuadrant.BOTTOM_RIGHT -> true
        SpatialQuadrant.CENTER -> ((seed ushr 11) and 1) == 1
      }
    val orientationDeg = (((seed ushr 12) % 29) - 14).toFloat()
    val layerDepthMode = (seed ushr 17) % 3
    val borderTreatment =
      when {
        family == BackArtworkDesignFamily.MONOCHROMATIC && (seed and 1) == 0 -> BackBorderTreatment.DOUBLE_RULE
        family == BackArtworkDesignFamily.SYMBOLIC && ((seed ushr 2) and 1) == 0 -> BackBorderTreatment.INNER_HAIRLINE
        family == BackArtworkDesignFamily.CELESTIAL && ((seed ushr 4) and 1) == 1 -> BackBorderTreatment.CORNER_BRACKETS
        metrics.symmetryScore > 0.68f && ((seed ushr 6) % 3 == 0) -> BackBorderTreatment.INNER_HAIRLINE
        else -> BackBorderTreatment.NONE
      }
    val typographyStyle =
      when (family) {
        BackArtworkDesignFamily.GEOMETRIC,
        BackArtworkDesignFamily.PATTERNED,
        -> if ((seed and 3) == 0) BackTypographyStyle.MINIMAL_QUIET else BackTypographyStyle.GEOMETRIC_SANS
        BackArtworkDesignFamily.MONOCHROMATIC,
        BackArtworkDesignFamily.ATMOSPHERIC,
        -> if (mood.contrastLevel < 0.18f) BackTypographyStyle.MINIMAL_QUIET else BackTypographyStyle.EDITORIAL_SERIF
        BackArtworkDesignFamily.SYMBOLIC,
        BackArtworkDesignFamily.CELESTIAL,
        BackArtworkDesignFamily.BOTANICAL,
        -> if ((seed and 1) == 0) BackTypographyStyle.DECORATIVE_BADGE else BackTypographyStyle.EDITORIAL_SERIF
        BackArtworkDesignFamily.ORGANIC,
        BackArtworkDesignFamily.ABSTRACT,
        BackArtworkDesignFamily.ILLUSTRATIVE,
        -> if ((seed and 2) == 0) BackTypographyStyle.EDITORIAL_SERIF else BackTypographyStyle.GEOMETRIC_SANS
      }

    // Adaptive surface finish: vibrant geometric/celestial designs stay sleeker; warm/organic/monochrome get subtle tactile tooth
    val (textureFinish, textureOpacity) =
      when {
        family == BackArtworkDesignFamily.GEOMETRIC ||
          family == BackArtworkDesignFamily.CELESTIAL ||
          mood.saturationLevel > 0.56f ->
          BackTextureFinish.SMOOTH_SATIN to 0.08f
        family == BackArtworkDesignFamily.BOTANICAL ||
          family == BackArtworkDesignFamily.ORGANIC ||
          family == BackArtworkDesignFamily.MONOCHROMATIC ->
          BackTextureFinish.TACTILE_COTTON to (if (mood.isDark) 0.28f else 0.36f)
        else ->
          BackTextureFinish.SUBTLE_PAPER to (if (mood.isDark) 0.20f else 0.26f)
      }

    val decorativeAccentMode = (seed ushr 19) % 4

    return BackArtworkCompositionSpec(
      family = family,
      variantIndex = variantIndex,
      scaleFactor = scaleFactor,
      elementCount = elementCount,
      mirrorHorizontal = mirrorHorizontal,
      orientationDeg = orientationDeg,
      layerDepthMode = layerDepthMode,
      borderTreatment = borderTreatment,
      typographyStyle = typographyStyle,
      textureFinish = textureFinish,
      textureOpacity = textureOpacity,
      negativeSpaceOnTop = metrics.upperQuietness >= 0.45f || dominant.luminance() > 0.18f,
      decorativeAccentMode = decorativeAccentMode,
      rankedFamilies = rankedFamilies,
      designSeed = seed,
    )
  }

  private fun colorDistRaw(a: SamplePoint, b: SamplePoint): Float {
    val dr = a.r - b.r
    val dg = a.g - b.g
    val db = a.b - b.b
    return sqrt(0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db)
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
    metrics: BookCoverCompositionMetrics,
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
    h = h * 31 + (metrics.visualDensity * 31f).toInt()
    h = h * 31 + (metrics.symmetryScore * 31f).toInt()
    return h and Int.MAX_VALUE
  }

  private fun stableHash(text: String): Int {
    var h = 0x811C9DC5.toInt()
    for (ch in text) {
      h = h xor ch.code
      h *= 0x01000193
    }
    return h and Int.MAX_VALUE
  }

  private fun mixSeeds(a: Int, b: Int): Int {
    var x = a xor (b * 0x9E3779B9.toInt())
    x = x xor (x ushr 16)
    x *= 0x85EBCA6B.toInt()
    x = x xor (x ushr 13)
    return x and Int.MAX_VALUE
  }

  /**
   * Derives a hue-faithful tonal companion in HSV space so monochromatic or dual-tone palettes
   * never collapse into muddy brown/beige.
   */
  private fun deriveTonalCompanion(base: Color): Color {
    val hsv = toHsv(base)
    val lum = base.luminance()
    if (hsv[1] < 0.06f) {
      // Near-achromatic base: shift luminance cleanly while adding only whisper-soft paper warmth/coolness
      return if (lum < 0.45f) lerp(base, Color(0xFFDCD6CC), 0.30f)
      else lerp(base, Color(0xFF34312D), 0.28f)
    }
    hsv[0] = (hsv[0] + if (lum < 0.45f) 10f else -10f + 360f) % 360f
    if (lum < 0.40f) {
      hsv[1] = (hsv[1] * 0.84f).coerceIn(0.10f, 0.85f)
      hsv[2] = (hsv[2] + 0.24f).coerceIn(0.22f, 0.88f)
    } else {
      hsv[1] = (hsv[1] * 1.10f).coerceIn(0.14f, 0.85f)
      hsv[2] = (hsv[2] - 0.22f).coerceIn(0.16f, 0.78f)
    }
    return fromHsv(hsv)
  }

  /**
   * Derives an accent harmonized by split-complementary / analogous hue rotation in HSV space.
   */
  private fun deriveHarmonizedAccent(
    dominant: Color,
    secondary: Color,
  ): Color {
    val domHsv = toHsv(dominant)
    val avgLum = (dominant.luminance() + secondary.luminance()) * 0.5f
    if (domHsv[1] < 0.08f) {
      return if (avgLum < 0.40f) Color(0xFFD8A657) else Color(0xFFA64632)
    }
    val hueShift = if (domHsv[0] in 15f..70f) 185f else 150f
    domHsv[0] = (domHsv[0] + hueShift) % 360f
    domHsv[1] = max(domHsv[1], 0.46f).coerceAtMost(0.82f)
    domHsv[2] = if (avgLum < 0.38f) 0.84f else 0.68f
    return fromHsv(domHsv)
  }

  private fun deriveLightSupportColor(
    dominant: Color,
    secondary: Color,
    warmth: Float,
  ): Color {
    val hsv = toHsv(if (dominant.luminance() > secondary.luminance()) dominant else secondary)
    if (hsv[1] < 0.06f) {
      return if (warmth >= -0.04f) Color(0xFFEDE6D9) else Color(0xFFE3E8EC)
    }
    hsv[1] = (hsv[1] * 0.36f).coerceIn(0.08f, 0.32f)
    hsv[2] = max(hsv[2], 0.86f).coerceAtMost(0.95f)
    return fromHsv(hsv)
  }

  private fun deriveDarkContrastColor(
    dominant: Color,
    secondary: Color,
    warmth: Float,
  ): Color {
    val anchor = if (dominant.luminance() < secondary.luminance()) dominant else secondary
    val hsv = toHsv(anchor)
    if (hsv[1] < 0.06f) {
      return if (warmth >= -0.04f) Color(0xFF211C17) else Color(0xFF1A2026)
    }
    hsv[1] = (hsv[1] * 0.72f).coerceIn(0.18f, 0.72f)
    hsv[2] = min(hsv[2], 0.24f).coerceAtLeast(0.12f)
    return fromHsv(hsv)
  }

  private fun computeSupportingNeutral(
    dominant: Color,
    warmth: Float,
    isDark: Boolean,
  ): Color {
    val base = if (isDark) deriveLightSupportColor(dominant, dominant, warmth) else deriveDarkContrastColor(dominant, dominant, warmth)
    return lerp(base, dominant, 0.08f)
  }

  internal fun tameEditorialColor(
    color: Color,
    maxSaturation: Float = 0.83f,
  ): Color {
    val hsv = toHsv(color)
    if (hsv[1] > maxSaturation) {
      // Smoothly tether extreme neon > 0.83 into crisp rich pigment (< 0.85) without muddying normal vibrant colors
      hsv[1] = (maxSaturation * 0.94f + (hsv[1] - maxSaturation) * 0.18f).coerceAtMost(0.84f)
    }
    return fromHsv(hsv)
  }

  private fun toHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
      (color.red * 255f).toInt().coerceIn(0, 255),
      (color.green * 255f).toInt().coerceIn(0, 255),
      (color.blue * 255f).toInt().coerceIn(0, 255),
      hsv,
    )
    return hsv
  }

  private fun fromHsv(hsv: FloatArray): Color {
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
