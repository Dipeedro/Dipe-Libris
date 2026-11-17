package com.example.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography

private val BookCoverShape =
  RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)

private val BookSpineShape = RoundedCornerShape(DipeRadii.sharp)

@Composable
fun BookCoverArtwork(
  title: String,
  author: String,
  isFavorite: Boolean,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  elevated: Boolean = false,
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }
  val authorUpper = remember(author) { author.uppercase() }

  Box(
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
        .semantics { contentDescription = coverCd }
        .drawWithCache {
          val spineX = size.width * 0.085f
          val spineGradient =
            Brush.horizontalGradient(
              0.0f to palette.clothShade,
              0.07f to palette.clothHighlight,
              0.11f to Color.Transparent,
              startX = 0f,
              endX = size.width,
            )
          val pageEdgeWidth = if (compact) 0f else 3.dp.toPx()
          val pageEdgeLeft = size.width - pageEdgeWidth
          val primaryStroke = 1.dp.toPx()
          val secondaryStroke = 0.75.dp.toPx()
          val secondaryOffset = 2.5.dp.toPx()
          val frameInset = if (compact) 0f else 7.dp.toPx()
          val ribbonPath =
            if (isFavorite) {
              val rWidth = (if (compact) 6.dp else 8.dp).toPx()
              val rHeight = (if (compact) 15.dp else 23.dp).toPx()
              val rEnd = (if (compact) 8.dp else 13.dp).toPx()
              val rRight = size.width - pageEdgeWidth - rEnd
              val rLeft = rRight - rWidth
              val rMid = (rLeft + rRight) * 0.5f
              val notch = 3.5.dp.toPx()
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
            // Subtle tactile spine light & shadow
            drawRect(brush = spineGradient)

            // Subtle physical page block on fore-edge for full-size covers
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

            // Spine binding creases
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

            // Fine inset editorial blind-deboss frame on full-size covers
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
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(
            start = if (compact) 12.dp else 24.dp,
            end = if (compact) DipeSpacing.sm else 20.dp,
            top = if (compact) DipeSpacing.sm else 20.dp,
            bottom = if (compact) DipeSpacing.sm else 20.dp,
          ),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = if (compact) typography.bookCoverTitleCompact else typography.bookCoverTitle,
        color = palette.onCloth,
        maxLines = if (compact) 3 else 4,
        overflow = TextOverflow.Ellipsis,
      )

      Column {
        Box(
          modifier =
            Modifier.width(if (compact) 16.dp else 22.dp)
              .height(0.75.dp)
              .background(palette.ruleColor)
        )
        Spacer(modifier = Modifier.height(DipeSpacing.xs))
        Text(
          text = authorUpper,
          style = if (compact) typography.bookCoverAuthorCompact else typography.bookCoverAuthor,
          color = palette.authorColor,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
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
) {
  val coverTokens = LocalDipeCoverTokens.current
  val typography = LocalDipeTypography.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val palette =
    remember(title, author, coverTokens) { coverTokens.paletteFor(title = title, author = author) }
  val shortTitle = remember(title) { title.take(18) }

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
              0.25f to Color.Transparent,
              0.85f to Color.Transparent,
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
      modifier = Modifier.fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (isFavorite) {
        Box(modifier = Modifier.width(12.dp).height(3.dp).background(coverTokens.favoriteRibbon))
      } else {
        Box(modifier = Modifier.width(12.dp).height(1.dp).background(palette.ruleColor))
      }
      Text(
        text = shortTitle,
        style = typography.bookSpineTitle,
        color = palette.onCloth,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      Box(modifier = Modifier.width(12.dp).height(1.dp).background(palette.ruleColor))
    }
  }
}
