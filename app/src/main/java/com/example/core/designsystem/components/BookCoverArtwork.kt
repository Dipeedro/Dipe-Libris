package com.example.core.designsystem.components

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

private val BookCoverShape =
  RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)

private val BookSpineShape = RoundedCornerShape(DipeRadii.sharp)
private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")
private const val COVER_ENTRANCE_DURATION_MS = 600

private data class ShadowBitmapKey(
  val widthPx: Int,
  val heightPx: Int,
  val padPx: Int,
  val leftCornerPx: Int,
  val rightCornerPx: Int,
  val compact: Boolean,
)

private data class CachedShadowBitmap(
  val bitmap: ImageBitmap,
  val padPx: Int,
)

private object CoverShadowBitmapCache {
  private val cache = LruCache<ShadowBitmapKey, CachedShadowBitmap>(24)

  fun getOrCreate(
    widthPx: Int,
    heightPx: Int,
    leftCornerPx: Float,
    rightCornerPx: Float,
    ambientDyPx: Float,
    contactDyPx: Float,
    ambientBlurPx: Float,
    contactBlurPx: Float,
    compact: Boolean,
  ): CachedShadowBitmap {
    val safeW = widthPx.coerceAtLeast(1)
    val safeH = heightPx.coerceAtLeast(1)
    val padPx = ceil(ambientBlurPx + ambientDyPx + 4f).toInt().coerceAtLeast(4)
    val key =
      ShadowBitmapKey(
        widthPx = safeW,
        heightPx = safeH,
        padPx = padPx,
        leftCornerPx = leftCornerPx.roundToInt(),
        rightCornerPx = rightCornerPx.roundToInt(),
        compact = compact,
      )
    synchronized(cache) {
      cache.get(key)?.let { return it }
    }

    val bmpW = safeW + padPx * 2
    val bmpH = safeH + padPx * 2
    val androidBitmap =
      android.graphics.Bitmap.createBitmap(bmpW, bmpH, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)
    val radii =
      floatArrayOf(
        leftCornerPx, leftCornerPx,
        rightCornerPx, rightCornerPx,
        rightCornerPx, rightCornerPx,
        leftCornerPx, leftCornerPx,
      )

    val ambientPath =
      android.graphics.Path().apply {
        addRoundRect(
          padPx.toFloat(),
          padPx + ambientDyPx,
          (padPx + safeW).toFloat(),
          padPx + safeH + ambientDyPx,
          radii,
          android.graphics.Path.Direction.CW,
        )
      }
    val contactPath =
      android.graphics.Path().apply {
        addRoundRect(
          padPx.toFloat(),
          padPx + contactDyPx,
          (padPx + safeW).toFloat(),
          padPx + safeH + contactDyPx,
          radii,
          android.graphics.Path.Direction.CW,
        )
      }
    val ambientPaint =
      android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = 0.22f).toArgb()
        maskFilter = BlurMaskFilter(ambientBlurPx.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
      }
    val contactPaint =
      android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = 0.14f).toArgb()
        maskFilter = BlurMaskFilter(contactBlurPx.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
      }

    canvas.drawPath(ambientPath, ambientPaint)
    canvas.drawPath(contactPath, contactPaint)

    val inset = 1.5f
    if (safeW > inset * 2f && safeH > inset * 2f) {
      val clearRadii =
        floatArrayOf(
          (leftCornerPx - inset).coerceAtLeast(0f), (leftCornerPx - inset).coerceAtLeast(0f),
          (rightCornerPx - inset).coerceAtLeast(0f), (rightCornerPx - inset).coerceAtLeast(0f),
          (rightCornerPx - inset).coerceAtLeast(0f), (rightCornerPx - inset).coerceAtLeast(0f),
          (leftCornerPx - inset).coerceAtLeast(0f), (leftCornerPx - inset).coerceAtLeast(0f),
        )
      val clearPath =
        android.graphics.Path().apply {
          addRoundRect(
            padPx + inset,
            padPx + inset,
            padPx + safeW - inset,
            padPx + safeH - inset,
            clearRadii,
            android.graphics.Path.Direction.CW,
          )
        }
      val clearPaint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
          xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
      canvas.drawPath(clearPath, clearPaint)
    }

    val entry = CachedShadowBitmap(bitmap = androidBitmap.asImageBitmap(), padPx = padPx)
    synchronized(cache) {
      cache.put(key, entry)
    }
    return entry
  }
}

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
  if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
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
  animateEntrance: Boolean = false,
  entranceKey: Any? = null,
  entranceProgress: (() -> Float)? = null,
  bottomProgressPercent: Float? = null,
  widthHintDp: Dp? = null,
  contentScale: ContentScale = ContentScale.Crop,
  onImageLoadError: (() -> Unit)? = null,
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
  var activeUriIndex by remember(candidateUris) { mutableIntStateOf(0) }
  val activeUri = candidateUris.getOrNull(activeUriIndex)
  val density = LocalDensity.current
  val effectiveWidthDp = widthHintDp ?: if (compact) 52.dp else 160.dp

  val targetWidthPx =
    remember(effectiveWidthDp, density) {
      with(density) { effectiveWidthDp.roundToPx() }.coerceIn(64, 1600)
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
  val isTinyRemotePlaceholder =
    if (painterState is AsyncImagePainter.State.Success && activeUri?.startsWith("http") == true) {
      val w = painterState.result.drawable.intrinsicWidth
      val h = painterState.result.drawable.intrinsicHeight
      (w in 1..48) || (h in 1..48)
    } else {
      false
    }

  LaunchedEffect(activeUri, painterState, isTinyRemotePlaceholder) {
    if (activeUri != null && (painterState is AsyncImagePainter.State.Error || isTinyRemotePlaceholder)) {
      if (activeUriIndex + 1 < candidateUris.size) {
        activeUriIndex++
      } else if (painterState is AsyncImagePainter.State.Error) {
        onImageLoadError?.invoke()
      }
    }
  }
  val isPhotoLoading =
    activeUri != null &&
      (painterState is AsyncImagePainter.State.Empty || painterState is AsyncImagePainter.State.Loading)
  val hasLoadedPhoto = painterState is AsyncImagePainter.State.Success && !isTinyRemotePlaceholder
  val showTypographicCloth = activeUri == null || (!isPhotoLoading && !hasLoadedPhoto)

  val photoAlpha =
    remember(activeUri, reduceMotion) {
      Animatable(if (reduceMotion || hasLoadedPhoto) 1f else 0f)
    }
  var sawAsyncLoading by remember(activeUri) { mutableStateOf(false) }

  LaunchedEffect(activeUri, painterState, hasLoadedPhoto, reduceMotion) {
    if (painterState is AsyncImagePainter.State.Loading) {
      sawAsyncLoading = true
    }
    if (hasLoadedPhoto && photoAlpha.value < 1f) {
      if (reduceMotion || !sawAsyncLoading) {
        photoAlpha.snapTo(1f)
      } else {
        photoAlpha.animateTo(1f, tween(durationMillis = 150, easing = FastOutSlowInEasing))
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

  val shadowModifier =
    if (elevated) {
      Modifier.drawWithCache {
        val startCornerPx = 2.dp.toPx()
        val endCornerPx = 6.dp.toPx()
        val leftCornerPx = if (layoutDirection == LayoutDirection.Rtl) endCornerPx else startCornerPx
        val rightCornerPx = if (layoutDirection == LayoutDirection.Rtl) startCornerPx else endCornerPx
        val ambientDy = (if (compact) 2.dp else 4.dp).toPx()
        val contactDy = (if (compact) 0.5.dp else 1.dp).toPx()
        val ambientBlurPx = (if (compact) 4.dp else 10.dp).toPx()
        val contactBlurPx = (if (compact) 1.5.dp else 3.dp).toPx()
        val cachedShadow =
          if (size.width >= 1f && size.height >= 1f) {
            CoverShadowBitmapCache.getOrCreate(
              widthPx = size.width.roundToInt(),
              heightPx = size.height.roundToInt(),
              leftCornerPx = leftCornerPx,
              rightCornerPx = rightCornerPx,
              ambientDyPx = ambientDy,
              contactDyPx = contactDy,
              ambientBlurPx = ambientBlurPx,
              contactBlurPx = contactBlurPx,
              compact = compact,
            )
          } else {
            null
          }
        onDrawBehind {
          cachedShadow?.let { entry ->
            drawImage(
              image = entry.bitmap,
              dstOffset = IntOffset(-entry.padPx, -entry.padPx),
              dstSize =
                IntSize(
                  width = size.width.roundToInt() + entry.padPx * 2,
                  height = size.height.roundToInt() + entry.padPx * 2,
                ),
            )
          }
        }
      }
    } else {
      Modifier
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
    // Sibling shadow layer always kept in composition to avoid render-node jumps
    Box(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha =
              if (!elevated) {
                0f
              } else if (reduceMotion) {
                1f
              } else {
                val p = progressProvider().coerceIn(0f, 1f)
                val coverAlpha = p
                val shadowCurve = smoothstep(0.35f, 1f, p)
                minOf(shadowCurve, coverAlpha)
              }
          }
          .then(shadowModifier),
    )

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
          .clip(BookCoverShape)
          .then(
            when {
              showTypographicCloth ->
                Modifier.background(palette.clothColor)
                  .border(0.75.dp, borderColor, BookCoverShape)
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
              alpha =
                if (reduceMotion || (hasLoadedPhoto && !sawAsyncLoading)) 1f else photoAlpha.value
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
  hasActiveReading: Boolean = false,
  hasMultipleCopies: Boolean = false,
  elevated: Boolean = false,
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

  val spineShadowModifier =
    if (elevated) {
      Modifier.drawWithCache {
        val cornerPx = DipeRadii.sharp.toPx()
        val ambientDy = 2.dp.toPx()
        val contactDy = 0.5.dp.toPx()
        val ambientBlurPx = 4.dp.toPx()
        val contactBlurPx = 1.5.dp.toPx()
        val cachedShadow =
          if (size.width >= 1f && size.height >= 1f) {
            CoverShadowBitmapCache.getOrCreate(
              widthPx = size.width.roundToInt(),
              heightPx = size.height.roundToInt(),
              leftCornerPx = cornerPx,
              rightCornerPx = cornerPx,
              ambientDyPx = ambientDy,
              contactDyPx = contactDy,
              ambientBlurPx = ambientBlurPx,
              contactBlurPx = contactBlurPx,
              compact = true,
            )
          } else {
            null
          }
        onDrawBehind {
          cachedShadow?.let { entry ->
            drawImage(
              image = entry.bitmap,
              dstOffset = IntOffset(-entry.padPx, -entry.padPx),
              dstSize =
                IntSize(
                  width = size.width.roundToInt() + entry.padPx * 2,
                  height = size.height.roundToInt() + entry.padPx * 2,
                ),
            )
          }
        }
      }
    } else {
      Modifier
    }

  Box(
    modifier =
      modifier
        .height(height)
        .width(48.dp)
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
    Box(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha =
              if (!elevated) {
                0f
              } else if (reduceMotion) {
                1f
              } else {
                val p = progressProvider().coerceIn(0f, 1f)
                val coverAlpha = p
                val shadowCurve = smoothstep(0.35f, 1f, p)
                minOf(shadowCurve, coverAlpha)
              }
          }
          .then(spineShadowModifier),
    )

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
          .padding(vertical = DipeSpacing.sm, horizontal = DipeSpacing.xs),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (isFavorite) {
          Box(modifier = Modifier.width(14.dp).height(3.dp).background(coverTokens.favoriteRibbon))
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

        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = title,
            style = typography.bookSpineTitle,
            color = palette.onCloth,
            maxLines = 1,
            softWrap = false,
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
