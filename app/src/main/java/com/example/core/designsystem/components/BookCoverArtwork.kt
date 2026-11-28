package com.example.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.core.data.local.media.CoverPlaceholderDetector
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BookCoverShape =
  RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)

private val BookSpineShape = RoundedCornerShape(DipeRadii.sharp)
private val CoverShadowColor = Color(0xFF211A14)
private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")
private const val COVER_ENTRANCE_DURATION_MS = 600

@Immutable
data class BookCoverEdgePalette(
  val top: Color,
  val mid: Color,
  val bottom: Color,
)

internal fun harmonizeCoverEdgePalette(
  top: Color,
  mid: Color,
  bottom: Color,
): BookCoverEdgePalette {
  val avg =
    Color(
      red = (top.red + mid.red + bottom.red) / 3f,
      green = (top.green + mid.green + bottom.green) / 3f,
      blue = (top.blue + mid.blue + bottom.blue) / 3f,
      alpha = 1f,
    )
  fun temper(raw: Color): Color {
    val blended = lerp(raw, avg, 0.10f)
    // Ensure stark white covers still keep a tactile matte board edge distinct from cream pages
    return if (blended.luminance() > 0.93f) lerp(blended, Color(0xFFE3DBCD), 0.34f) else blended
  }
  return BookCoverEdgePalette(
    top = temper(top),
    mid = temper(mid),
    bottom = temper(bottom),
  )
}

internal object CoverEdgeColorExtractor {
  private val cache = LruCache<String, BookCoverEdgePalette>(64)

  fun getCached(key: String?): BookCoverEdgePalette? = key?.let { cache.get(it) }

  fun extractFromDrawable(
    cacheKey: String?,
    drawable: Drawable?,
  ): BookCoverEdgePalette? {
    if (cacheKey != null) {
      cache.get(cacheKey)?.let { return it }
    }
    if (drawable == null) return null

    val palette =
      if (drawable is BitmapDrawable) {
        val bmp = drawable.bitmap ?: return null
        extractFromBitmapSafely(bmp)
      } else {
        extractFromGenericDrawable(drawable)
      }

    if (palette != null && cacheKey != null) {
      cache.put(cacheKey, palette)
    }
    return palette
  }

  private fun extractFromBitmapSafely(bitmap: Bitmap): BookCoverEdgePalette? {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
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
      extractFromSoftwareBitmap(readable)
    } catch (_: Throwable) {
      null
    } finally {
      tempCopy?.let { if (!it.isRecycled) it.recycle() }
    }
  }

  private fun extractFromGenericDrawable(drawable: Drawable): BookCoverEdgePalette? {
    val targetW = 16
    val targetH = 24
    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    return try {
      val canvas = AndroidCanvas(bmp)
      val prevBounds = drawable.copyBounds()
      drawable.setBounds(0, 0, targetW, targetH)
      drawable.draw(canvas)
      drawable.bounds = prevBounds
      extractFromSoftwareBitmap(bmp)
    } catch (_: Throwable) {
      null
    } finally {
      if (!bmp.isRecycled) bmp.recycle()
    }
  }

  internal fun extractFromSoftwareBitmap(bitmap: Bitmap): BookCoverEdgePalette? {
    if (bitmap.isRecycled) return null
    val w = bitmap.width
    val h = bitmap.height
    if (w < 4 || h < 4) return null

    val innerFractions = floatArrayOf(0.80f, 0.85f, 0.89f)
    val rimFraction = 0.95f
    fun sampleBand(yStartFrac: Float, yEndFrac: Float): Color? {
      var rInner = 0L
      var gInner = 0L
      var bInner = 0L
      var countInner = 0
      var rRim = 0L
      var gRim = 0L
      var bRim = 0L
      var countRim = 0
      val steps = 5
      for (yi in 0 until steps) {
        val yf = yStartFrac + (yEndFrac - yStartFrac) * (yi / (steps - 1f))
        val py = (yf * (h - 1)).toInt().coerceIn(0, h - 1)
        for (xf in innerFractions) {
          val px = (xf * (w - 1)).toInt().coerceIn(0, w - 1)
          val argb = bitmap.getPixel(px, py)
          if (AndroidColor.alpha(argb) >= 128) {
            rInner += AndroidColor.red(argb)
            gInner += AndroidColor.green(argb)
            bInner += AndroidColor.blue(argb)
            countInner++
          }
        }
        val pxRim = (rimFraction * (w - 1)).toInt().coerceIn(0, w - 1)
        val argbRim = bitmap.getPixel(pxRim, py)
        if (AndroidColor.alpha(argbRim) >= 128) {
          rRim += AndroidColor.red(argbRim)
          gRim += AndroidColor.green(argbRim)
          bRim += AndroidColor.blue(argbRim)
          countRim++
        }
      }
      if (countInner == 0 && countRim == 0) return null
      val innerAvgBrightness =
        if (countInner > 0) (rInner + gInner + bInner) / (3f * countInner) else 255f
      val rimAvgBrightness =
        if (countRim > 0) (rRim + gRim + bRim) / (3f * countRim) else 255f
      val ignoreWhiteScanMargin = countInner > 0 && countRim > 0 && rimAvgBrightness > 236f && innerAvgBrightness < 210f
      val rTotal = if (ignoreWhiteScanMargin) rInner else rInner + rRim
      val gTotal = if (ignoreWhiteScanMargin) gInner else gInner + gRim
      val bTotal = if (ignoreWhiteScanMargin) bInner else bInner + bRim
      val totalCount = if (ignoreWhiteScanMargin) countInner else countInner + countRim
      if (totalCount == 0) return null
      return Color(
        red = (rTotal.toFloat() / totalCount) / 255f,
        green = (gTotal.toFloat() / totalCount) / 255f,
        blue = (bTotal.toFloat() / totalCount) / 255f,
        alpha = 1f,
      )
    }

    val top = sampleBand(0.06f, 0.30f)
    val mid = sampleBand(0.35f, 0.65f)
    val bottom = sampleBand(0.70f, 0.94f)
    val fallback = mid ?: top ?: bottom ?: return null
    return harmonizeCoverEdgePalette(
      top = top ?: fallback,
      mid = mid ?: fallback,
      bottom = bottom ?: fallback,
    )
  }
}

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
  if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

@Composable
private fun BoxScope.BookCoverShadowLayer(
  elevation: Dp,
  shape: Shape,
  progressProvider: () -> Float,
  reduceMotion: Boolean,
  shadowProgressProvider: (() -> Float)? = null,
) {
  if (shadowProgressProvider != null) {
    val s = if (reduceMotion) 1f else shadowProgressProvider().coerceIn(0f, 1f)
    val animatedElevation = elevation * s
    val shadowScale = 0.96f + (0.04f * s)
    Box(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer {
            scaleX = shadowScale
            scaleY = shadowScale
          }
          .shadow(
            elevation = animatedElevation,
            shape = shape,
            clip = false,
            ambientColor = CoverShadowColor.copy(alpha = 0.18f * s),
            spotColor = CoverShadowColor.copy(alpha = 0.28f * s),
          ),
    )
  } else {
    val p = if (reduceMotion) 1f else progressProvider().coerceIn(0f, 1f)
    val s = if (reduceMotion) 1f else smoothstep(0.35f, 1f, p)
    Box(
      modifier =
        Modifier.matchParentSize()
          .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = CoverShadowColor.copy(alpha = 0.18f * s),
            spotColor = CoverShadowColor.copy(alpha = 0.28f * s),
          ),
    )
  }
}

@Composable
fun BookCoverArtwork(
  title: String,
  author: String,
  isFavorite: Boolean,
  modifier: Modifier = Modifier,
  coverUri: String? = null,
  fallbackCoverUris: List<String> = emptyList(),
  compact: Boolean = false,
  elevated: Boolean = false,
  shadowElevation: Dp = 8.dp,
  animateEntrance: Boolean = false,
  entranceKey: Any? = null,
  entranceProgress: (() -> Float)? = null,
  applyEntranceScale: Boolean = true,
  shadowProgress: (() -> Float)? = null,
  bottomProgressPercent: Float? = null,
  widthHintDp: Dp? = null,
  contentScale: ContentScale = ContentScale.Crop,
  shape: Shape = BookCoverShape,
  onImageLoadError: (() -> Unit)? = null,
  onCoverEdgePaletteChanged: ((BookCoverEdgePalette?) -> Unit)? = null,
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val context = LocalContext.current
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }
  val authorUpper = remember(author) { author.uppercase() }
  val monogram =
    remember(title) {
      val words = title.trim().split(WHITESPACE_SPLIT_REGEX).filter { it.isNotEmpty() }
      when {
        words.isEmpty() -> "L"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
      }
    }

  val candidateUris =
    remember(coverUri, fallbackCoverUris) {
      (listOfNotNull(coverUri?.takeIf { it.isNotBlank() }) + fallbackCoverUris.filter { it.isNotBlank() })
        .distinct()
    }
  var rejectedUris by remember(candidateUris.firstOrNull()) { mutableStateOf(emptySet<String>()) }
  val activeUri = candidateUris.firstOrNull { it !in rejectedUris }
  val density = LocalDensity.current
  val effectiveWidthDp = widthHintDp ?: if (compact) 52.dp else 160.dp

  val minTargetWidthPx = if (compact) 64 else 320
  val targetWidthPx =
    remember(effectiveWidthDp, density, compact) {
      with(density) { effectiveWidthDp.roundToPx() }.coerceIn(minTargetWidthPx, 1600)
    }
  val targetHeightPx = (targetWidthPx * 3) / 2

  val imageRequest =
    remember(activeUri, context, targetWidthPx, targetHeightPx, contentScale) {
      activeUri?.let { raw ->
        val data: Any =
          when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("content://") || raw.startsWith("file://") ->
              android.net.Uri.parse(raw)
            raw.startsWith("/") -> File(raw)
            else -> raw
          }
        ImageRequest.Builder(context)
          .data(data)
          .size(targetWidthPx, targetHeightPx)
          .precision(Precision.INEXACT)
          .crossfade(false)
          .build()
      }
    }
  val painter = imageRequest?.let { rememberAsyncImagePainter(model = it) }
  val painterState = painter?.state
  val isInvalidRemotePlaceholder =
    if (painterState is AsyncImagePainter.State.Success && activeUri?.startsWith("http") == true) {
      val drawable = painterState.result.drawable
      val w = drawable.intrinsicWidth
      val h = drawable.intrinsicHeight
      (w in 1..48) ||
        (h in 1..48) ||
        (CoverPlaceholderDetector.isGoogleBooksHost(activeUri) &&
          runCatching { CoverPlaceholderDetector.isKnownGooglePlaceholderDrawable(drawable) }
            .getOrDefault(false))
    } else {
      false
    }

  LaunchedEffect(activeUri, painterState, isInvalidRemotePlaceholder) {
    if (activeUri != null && (painterState is AsyncImagePainter.State.Error || isInvalidRemotePlaceholder)) {
      if (activeUri !in rejectedUris) {
        rejectedUris = rejectedUris + activeUri
      }
      val hasNextCandidate = candidateUris.any { it != activeUri && it !in rejectedUris }
      if (!hasNextCandidate && painterState is AsyncImagePainter.State.Error) {
        onImageLoadError?.invoke()
      }
    }
  }
  val isPhotoLoading =
    activeUri != null &&
      (painterState is AsyncImagePainter.State.Empty || painterState is AsyncImagePainter.State.Loading)
  val hasLoadedPhoto = painterState is AsyncImagePainter.State.Success && !isInvalidRemotePlaceholder
  val showTypographicCloth = activeUri == null || (!isPhotoLoading && !hasLoadedPhoto)

  if (onCoverEdgePaletteChanged != null) {
    val loadedDrawable = (painterState as? AsyncImagePainter.State.Success)?.result?.drawable
    LaunchedEffect(activeUri, hasLoadedPhoto, loadedDrawable) {
      if (!hasLoadedPhoto || loadedDrawable == null) {
        onCoverEdgePaletteChanged(null)
      } else {
        val cached = CoverEdgeColorExtractor.getCached(activeUri)
        if (cached != null) {
          onCoverEdgePaletteChanged(cached)
        } else {
          val extracted =
            withContext(Dispatchers.Default) {
              CoverEdgeColorExtractor.extractFromDrawable(activeUri, loadedDrawable)
            }
          onCoverEdgePaletteChanged(extracted)
        }
      }
    }
  }

  val wasInitiallyLoaded = remember(activeUri) { hasLoadedPhoto }
  val photoAlpha =
    remember(activeUri, reduceMotion) {
      Animatable(if (reduceMotion || wasInitiallyLoaded) 1f else 0f)
    }

  LaunchedEffect(activeUri, hasLoadedPhoto, reduceMotion) {
    if (hasLoadedPhoto && photoAlpha.value < 1f) {
      if (reduceMotion || wasInitiallyLoaded) {
        photoAlpha.snapTo(1f)
      } else {
        photoAlpha.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
      }
    }
  }

  val itemKey = entranceKey ?: "${title}_${author}"
  var hasPlayedEntrance by rememberSaveable(itemKey) {
    mutableStateOf(reduceMotion || !animateEntrance)
  }
  val localEntrance =
    remember(itemKey, reduceMotion, animateEntrance) {
      Animatable(if (reduceMotion || !animateEntrance || hasPlayedEntrance) 1f else 0f)
    }
  if (entranceProgress == null && animateEntrance) {
    LaunchedEffect(itemKey, reduceMotion) {
      if (reduceMotion || hasPlayedEntrance) {
        localEntrance.snapTo(1f)
        hasPlayedEntrance = true
      } else {
        hasPlayedEntrance = true
        localEntrance.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = COVER_ENTRANCE_DURATION_MS, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  val progressProvider: () -> Float =
    entranceProgress ?: if (animateEntrance) ({ localEntrance.value }) else ({ 1f })
  val shouldTransformEntrance = entranceProgress != null || animateEntrance
  val risePx = remember(density, compact) { with(density) { (if (compact) 8.dp else 14.dp).toPx() } }

  val resolvedAspectRatio =
    if (contentScale == ContentScale.Fit && hasLoadedPhoto) {
      val drawable = (painterState as AsyncImagePainter.State.Success).result.drawable
      val w = drawable.intrinsicWidth
      val h = drawable.intrinsicHeight
      if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.35f, 2.0f) else 2f / 3f
    } else {
      2f / 3f
    }

  val progressFillColor = DipeTheme.colors.readingProgress
  val progressTrackColor = DipeTheme.colors.readingProgressTrack

  Box(
    modifier =
      modifier
        .aspectRatio(
          ratio = resolvedAspectRatio,
          matchHeightConstraintsFirst = contentScale == ContentScale.Fit,
        )
        .then(
          if (shouldTransformEntrance) {
            Modifier.graphicsLayer {
              val p = if (reduceMotion) 1f else progressProvider().coerceIn(0f, 1f)
              val scale = if (applyEntranceScale) 0.94f + (0.06f * p) else 1f
              translationY = (1f - p) * risePx
              scaleX = scale
              scaleY = scale
            }
          } else {
            Modifier
          }
        )
        .semantics { contentDescription = coverCd },
    contentAlignment = Alignment.Center,
  ) {
    if (elevated) {
      BookCoverShadowLayer(
        elevation = shadowElevation,
        shape = shape,
        progressProvider = progressProvider,
        shadowProgressProvider = shadowProgress,
        reduceMotion = reduceMotion,
      )
    }

    Box(
      modifier =
        Modifier.matchParentSize()
          .then(
            if (shouldTransformEntrance) {
              Modifier.graphicsLayer {
                alpha = if (reduceMotion) 1f else progressProvider().coerceIn(0f, 1f)
              }
            } else {
              Modifier
            }
          )
          .clip(shape)
          .then(
            when {
              showTypographicCloth ->
                Modifier.background(palette.clothColor)
                  .border(0.75.dp, borderColor, shape)
              isPhotoLoading || (hasLoadedPhoto && photoAlpha.value < 1f) ->
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
              else -> Modifier
            }
          ),
      contentAlignment = Alignment.Center,
    ) {
      if (painter != null && activeUri != null) {
        Image(
          painter = painter,
          contentDescription = null,
          contentScale = contentScale,
          modifier =
            Modifier.fillMaxSize().graphicsLayer {
              alpha = if (reduceMotion) 1f else photoAlpha.value
            },
        )
        if (hasLoadedPhoto) {
          Spacer(
            modifier =
              Modifier.fillMaxSize().drawWithCache {
                val spineGradient =
                  Brush.horizontalGradient(
                    0.0f to Color(0x42000000),
                    0.05f to Color(0x1EFFFFFF),
                    0.09f to Color.Transparent,
                    startX = 0f,
                    endX = size.width,
                  )
                val ribbonPath =
                  if (isFavorite) {
                    val isTiny = compact || size.width < 92.dp.toPx()
                    val rWidth = (if (isTiny) 5.dp else 8.dp).toPx()
                    val rHeight = (if (isTiny) 14.dp else 23.dp).toPx()
                    val rEnd = (if (isTiny) 6.dp else 13.dp).toPx()
                    val rRight = size.width - rEnd
                    val rLeft = rRight - rWidth
                    val rMid = (rLeft + rRight) * 0.5f
                    val notch = (if (isTiny) 2.5.dp else 3.5.dp).toPx()
                    Path().apply {
                      moveTo(rLeft, 0f)
                      lineTo(rRight, 0f)
                      lineTo(rRight, rHeight)
                      lineTo(rMid, rHeight - notch)
                      lineTo(rLeft, rHeight)
                      close()
                    }
                  } else {
                    null
                  }
                onDrawBehind {
                  drawRect(brush = spineGradient)
                  if (ribbonPath != null) {
                    drawPath(path = ribbonPath, color = coverTokens.favoriteRibbon)
                  }
                }
              },
          )
        }
      }

      if (showTypographicCloth) {
        val coverWidth = effectiveWidthDp
        Box(
          modifier =
            Modifier.fillMaxSize().drawWithCache {
            val spineX = size.width * 0.085f
            val spineGradient =
              Brush.horizontalGradient(
                0.0f to palette.clothShade,
                0.07f to palette.clothHighlight,
                0.11f to Color.Transparent,
                startX = 0f,
                endX = size.width,
              )
            val isTiny = compact || size.width < 92.dp.toPx()
            val pageEdgeWidth = if (isTiny) 0f else 3.dp.toPx()
            val pageEdgeLeft = size.width - pageEdgeWidth
            val primaryStroke = 1.dp.toPx()
            val secondaryStroke = 0.75.dp.toPx()
            val secondaryOffset = 2.5.dp.toPx()
            val frameInset = if (isTiny) 0f else 7.dp.toPx()
            val ribbonPath =
              if (isFavorite) {
                val rWidth = (if (isTiny) 5.dp else 8.dp).toPx()
                val rHeight = (if (isTiny) 14.dp else 23.dp).toPx()
                val rEnd = (if (isTiny) 6.dp else 13.dp).toPx()
                val rRight = size.width - pageEdgeWidth - rEnd
                val rLeft = rRight - rWidth
                val rMid = (rLeft + rRight) * 0.5f
                val notch = (if (isTiny) 2.5.dp else 3.5.dp).toPx()
                Path().apply {
                  moveTo(rLeft, 0f)
                  lineTo(rRight, 0f)
                  lineTo(rRight, rHeight)
                  lineTo(rMid, rHeight - notch)
                  lineTo(rLeft, rHeight)
                  close()
                }
              } else {
                null
              }

            onDrawBehind {
              drawRect(brush = spineGradient)

              if (pageEdgeWidth > 0f) {
                drawRect(
                  color = coverTokens.pageEdgeLight,
                  topLeft = Offset(pageEdgeLeft, 2.dp.toPx()),
                  size = Size(pageEdgeWidth, (size.height - 4.dp.toPx()).coerceAtLeast(0f)),
                )
                drawLine(
                  color = coverTokens.pageEdgeShadow,
                  start = Offset(pageEdgeLeft, 1.dp.toPx()),
                  end = Offset(pageEdgeLeft, size.height - 1.dp.toPx()),
                  strokeWidth = secondaryStroke,
                )
              }

              drawLine(
                color = palette.spineCreasePrimary,
                start = Offset(spineX, 0f),
                end = Offset(spineX, size.height),
                strokeWidth = primaryStroke,
              )
              drawLine(
                color = palette.spineCreaseSecondary,
                start = Offset(spineX + secondaryOffset, 0f),
                end = Offset(spineX + secondaryOffset, size.height),
                strokeWidth = secondaryStroke,
              )

              if (frameInset > 0f) {
                val frameLeft = spineX + frameInset
                val frameRight = pageEdgeLeft - frameInset
                val frameWidth = (frameRight - frameLeft).coerceAtLeast(0f)
                val frameHeight = (size.height - frameInset * 2f).coerceAtLeast(0f)
                if (frameWidth > 0f && frameHeight > 0f) {
                  drawRect(
                    color = palette.frameBorderColor,
                    topLeft = Offset(frameLeft, frameInset),
                    size = Size(frameWidth, frameHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx()),
                  )
                }
              }

              if (ribbonPath != null) {
                drawPath(path = ribbonPath, color = coverTokens.favoriteRibbon)
              }
            }
          }
      ) {
        val renderMonogramOnly = compact || coverWidth < 96.dp
        if (renderMonogramOnly) {
          Box(
            modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 4.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              Text(
                text = monogram,
                style =
                  if (coverWidth < 46.dp) typography.bookCoverMonogram.copy(fontSize = 13.sp)
                  else typography.bookCoverMonogram,
                color = palette.onCloth,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
              )
              Box(
                modifier =
                  Modifier.width(if (coverWidth < 46.dp) 12.dp else 18.dp)
                    .height(0.75.dp)
                    .background(palette.ruleColor),
              )
            }
          }
        } else {
          val isMediumCover = coverWidth < 140.dp
          val titleStyle =
            if (isMediumCover) typography.bookCoverTitle.copy(fontSize = 13.5.sp, lineHeight = 17.sp)
            else typography.bookCoverTitle
          val authorStyle =
            if (isMediumCover) typography.bookCoverAuthor.copy(fontSize = 8.5.sp, letterSpacing = 0.9.sp)
            else typography.bookCoverAuthor

          Column(
            modifier =
              Modifier.fillMaxSize()
                .padding(
                  start = if (isMediumCover) 18.dp else 24.dp,
                  end = if (isMediumCover) 14.dp else 20.dp,
                  top = if (isMediumCover) 16.dp else 22.dp,
                  bottom = if (isMediumCover) 16.dp else 22.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = title,
              style = titleStyle,
              color = palette.onCloth,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
            )

            Column {
              Box(
                modifier =
                  Modifier.width(if (isMediumCover) 18.dp else 24.dp)
                    .height(0.75.dp)
                    .background(palette.ruleColor),
              )
              Spacer(modifier = Modifier.height(DipeSpacing.xs))
              Text(
                text = authorUpper,
                style = authorStyle,
                color = palette.authorColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      }

      if (bottomProgressPercent != null) {
        val fraction = (bottomProgressPercent / 100f).coerceIn(0f, 1f)
        Box(
          modifier =
            Modifier.align(Alignment.BottomStart)
              .fillMaxWidth()
              .height(3.dp)
              .background(progressTrackColor.copy(alpha = 0.92f)),
        ) {
          if (fraction > 0f) {
            Box(
              modifier =
                Modifier.fillMaxWidth(fraction)
                  .fillMaxHeight()
                  .background(progressFillColor),
            )
          }
        }
      }
    }
  }
}

@Composable
fun BookSpineTile(
  title: String,
  author: String,
  isFavorite: Boolean,
  height: Dp,
  modifier: Modifier = Modifier,
  width: Dp = 56.dp,
  hasActiveReading: Boolean = false,
  hasMultipleCopies: Boolean = false,
  elevated: Boolean = false,
  shadowElevation: Dp = 8.dp,
  animateEntrance: Boolean = false,
  entranceKey: Any? = null,
  entranceProgress: (() -> Float)? = null,
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val density = LocalDensity.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }

  val itemKey = entranceKey ?: "${title}_${author}"
  var hasPlayedEntrance by rememberSaveable(itemKey) {
    mutableStateOf(reduceMotion || !animateEntrance)
  }
  val localEntrance =
    remember(itemKey, reduceMotion, animateEntrance) {
      Animatable(if (reduceMotion || !animateEntrance || hasPlayedEntrance) 1f else 0f)
    }
  if (entranceProgress == null && animateEntrance) {
    LaunchedEffect(itemKey, reduceMotion) {
      if (reduceMotion || hasPlayedEntrance) {
        localEntrance.snapTo(1f)
        hasPlayedEntrance = true
      } else {
        hasPlayedEntrance = true
        localEntrance.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = COVER_ENTRANCE_DURATION_MS, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  val progressProvider: () -> Float =
    entranceProgress ?: if (animateEntrance) ({ localEntrance.value }) else ({ 1f })
  val shouldTransformEntrance = entranceProgress != null || animateEntrance
  val risePx = remember(density) { with(density) { 8.dp.toPx() } }

  Box(
    modifier =
      modifier
        .height(height)
        .width(width)
        .then(
          if (shouldTransformEntrance) {
            Modifier.graphicsLayer {
              val p = if (reduceMotion) 1f else progressProvider().coerceIn(0f, 1f)
              val scale = 0.94f + (0.06f * p)
              translationY = (1f - p) * risePx
              scaleX = scale
              scaleY = scale
            }
          } else {
            Modifier
          }
        )
        .semantics { contentDescription = coverCd },
    contentAlignment = Alignment.Center,
  ) {
    if (elevated) {
      BookCoverShadowLayer(
        elevation = shadowElevation,
        shape = BookSpineShape,
        progressProvider = progressProvider,
        reduceMotion = reduceMotion,
      )
    }

    Box(
      modifier =
        Modifier.matchParentSize()
          .then(
            if (shouldTransformEntrance) {
              Modifier.graphicsLayer {
                alpha = if (reduceMotion) 1f else progressProvider().coerceIn(0f, 1f)
              }
            } else {
              Modifier
            }
          )
          .clip(BookSpineShape)
          .background(palette.clothColor)
          .border(0.75.dp, borderColor, BookSpineShape)
          .drawWithCache {
            val spineShade =
              Brush.horizontalGradient(
                0.0f to palette.clothHighlight,
                0.22f to Color.Transparent,
                0.82f to Color.Transparent,
                1.0f to palette.clothShade,
                startX = 0f,
                endX = size.width,
              )
            onDrawBehind { drawRect(brush = spineShade) }
          }
          .padding(vertical = DipeSpacing.sm, horizontal = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (isFavorite) {
          Box(modifier = Modifier.width(16.dp).height(3.dp).background(coverTokens.favoriteRibbon))
        } else if (hasActiveReading) {
          Box(
            modifier =
              Modifier.width(6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondary),
          )
        } else {
          Box(modifier = Modifier.width(14.dp).height(1.dp).background(palette.ruleColor))
        }

        BoxWithConstraints(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          val charLen = title.trim().length
          val maxLineCount = if (charLen > 34 && maxWidth >= 34.dp) 3 else 2
          val adaptiveStyle =
            remember(typography.bookSpineTitle, charLen, maxHeight, maxWidth) {
              when {
                charLen <= 18 && maxHeight >= 150.dp ->
                  typography.bookSpineTitle.copy(fontSize = 12.5.sp, lineHeight = 14.5.sp)
                charLen <= 28 ->
                  typography.bookSpineTitle.copy(fontSize = 11.5.sp, lineHeight = 13.5.sp)
                else ->
                  typography.bookSpineTitle.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.1.sp,
                  )
              }
            }
          Text(
            text = title,
            style = adaptiveStyle,
            color = palette.onCloth,
            textAlign = TextAlign.Center,
            maxLines = maxLineCount,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier =
              Modifier.layout { measurable, constraints ->
                  val rotatedConstraints =
                    constraints.copy(
                      minWidth = 0,
                      maxWidth = constraints.maxHeight,
                      minHeight = 0,
                      maxHeight = constraints.maxWidth,
                    )
                  val placeable = measurable.measure(rotatedConstraints)
                  layout(placeable.height, placeable.width) {
                    placeable.place(
                      x = -(placeable.width - placeable.height) / 2,
                      y = (placeable.width - placeable.height) / 2,
                    )
                  }
                }
                .graphicsLayer { rotationZ = -90f },
          )
        }

        if (hasMultipleCopies) {
          Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(modifier = Modifier.width(14.dp).height(1.dp).background(palette.ruleColor))
            Box(modifier = Modifier.width(14.dp).height(1.dp).background(palette.ruleColor))
          }
        } else {
          Box(modifier = Modifier.width(14.dp).height(1.dp).background(palette.ruleColor))
        }
      }
    }
  }
}
