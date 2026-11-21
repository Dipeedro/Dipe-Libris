package com.example.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography
import java.io.File

private val BookCoverShape =
  RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)

private val BookSpineShape = RoundedCornerShape(DipeRadii.sharp)

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
  onImageLoadError: (() -> Unit)? = null,
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val context = LocalContext.current
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }
  val authorUpper = remember(author) { author.uppercase() }
  val monogram =
    remember(title) {
      val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
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

  BoxWithConstraints(
    modifier =
      modifier
        .aspectRatio(2f / 3f)
        .graphicsLayer {
          if (elevated) {
            shadowElevation = 10.dp.toPx()
            shape = BookCoverShape
            clip = false
          }
        }
        .clip(BookCoverShape)
        .background(palette.clothColor)
        .border(0.75.dp, borderColor, BookCoverShape)
        .semantics { contentDescription = coverCd },
  ) {
    val targetWidthPx =
      if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
        constraints.maxWidth.coerceIn(64, 1600)
      } else {
        with(density) { (if (compact) 56.dp else 160.dp).roundToPx() }.coerceAtLeast(64)
      }
    val targetHeightPx =
      if (constraints.hasBoundedHeight && constraints.maxHeight > 0 && constraints.maxHeight < Int.MAX_VALUE / 2) {
        constraints.maxHeight.coerceIn(96, 2400)
      } else {
        (targetWidthPx * 3) / 2
      }

    val imageRequest =
      remember(activeUri, context, targetWidthPx, targetHeightPx) {
        activeUri?.let { raw ->
          val file = File(raw)
          val data: Any =
            when {
              file.exists() -> file
              raw.startsWith("content://") || raw.startsWith("file://") ->
                android.net.Uri.parse(raw)
              else -> raw
            }
          ImageRequest.Builder(context)
            .data(data)
            .size(targetWidthPx, targetHeightPx)
            .precision(Precision.INEXACT)
            .crossfade(true)
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

    androidx.compose.runtime.LaunchedEffect(activeUri, painterState, isTinyRemotePlaceholder) {
      if (activeUri != null && (painterState is AsyncImagePainter.State.Error || isTinyRemotePlaceholder)) {
        if (activeUriIndex + 1 < candidateUris.size) {
          activeUriIndex++
        } else if (painterState is AsyncImagePainter.State.Error) {
          onImageLoadError?.invoke()
        }
      }
    }
    val hasLoadedPhoto = painterState is AsyncImagePainter.State.Success && !isTinyRemotePlaceholder

    if (painter != null) {
      Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.fillMaxSize().drawWithContent {
            drawContent()
            if (hasLoadedPhoto) {
              // Subtle book-binding crease and favorite ribbon over photographed covers
              val spineGradient =
                Brush.horizontalGradient(
                  0.0f to Color(0x42000000),
                  0.05f to Color(0x1EFFFFFF),
                  0.09f to Color.Transparent,
                  startX = 0f,
                  endX = size.width,
                )
              drawRect(brush = spineGradient)
              if (isFavorite) {
                val isTiny = compact || size.width < 92.dp.toPx()
                val rWidth = (if (isTiny) 5.dp else 8.dp).toPx()
                val rHeight = (if (isTiny) 14.dp else 23.dp).toPx()
                val rEnd = (if (isTiny) 6.dp else 13.dp).toPx()
                val rRight = size.width - rEnd
                val rLeft = rRight - rWidth
                val rMid = (rLeft + rRight) * 0.5f
                val notch = (if (isTiny) 2.5.dp else 3.5.dp).toPx()
                val ribbonPath =
                  Path().apply {
                    moveTo(rLeft, 0f)
                    lineTo(rRight, 0f)
                    lineTo(rRight, rHeight)
                    lineTo(rMid, rHeight - notch)
                    lineTo(rLeft, rHeight)
                    close()
                  }
                drawPath(path = ribbonPath, color = coverTokens.favoriteRibbon)
              }
            }
          },
      )
    }

    if (!hasLoadedPhoto) {
      val coverWidth = maxWidth
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
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }

  Box(
    modifier =
      modifier
        .height(height)
        .width(48.dp)
        .clip(BookSpineShape)
        .background(palette.clothColor)
        .border(0.75.dp, borderColor, BookSpineShape)
        .semantics { contentDescription = coverCd }
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
