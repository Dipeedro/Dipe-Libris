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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeCoverTokens
import com.example.core.designsystem.LocalDipeTypography
import kotlin.math.abs

@Composable
fun BookCoverArtwork(
  title: String,
  author: String,
  isFavorite: Boolean,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val coverTokens = LocalDipeCoverTokens.current
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val clothColor =
    remember(title, author, coverTokens) {
      val idx =
        abs((title.hashCode() * 31 + author.hashCode())) % coverTokens.clothPalettes.size
      coverTokens.clothPalettes[idx]
    }
  val onCloth =
    remember(clothColor, coverTokens) {
      if (clothColor.luminance() > 0.45f) coverTokens.darkInkOnLightCloth
      else coverTokens.lightInkOnDarkCloth
    }
  val shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)

  Box(
    modifier =
      modifier
        .aspectRatio(2f / 3f)
        .clip(shape)
        .background(clothColor)
        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .semantics { contentDescription = coverCd }
        .drawBehind {
          val spineX = size.width * 0.09f
          drawLine(
            color = onCloth.copy(alpha = 0.18f),
            start = Offset(spineX, 0f),
            end = Offset(spineX, size.height),
            strokeWidth = 1.5f,
          )
          drawLine(
            color = onCloth.copy(alpha = 0.08f),
            start = Offset(spineX + 3f, 0f),
            end = Offset(spineX + 3f, size.height),
            strokeWidth = 1f,
          )
        }
  ) {
    if (isFavorite) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(end = DipeSpacing.sm)
            .width(6.dp)
            .height(if (compact) 14.dp else 20.dp)
            .background(coverTokens.favoriteRibbon)
      )
    }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(
            start = if (compact) DipeSpacing.sm + 4.dp else DipeSpacing.md + 4.dp,
            end = if (compact) DipeSpacing.sm else DipeSpacing.md,
            top = if (compact) DipeSpacing.sm else DipeSpacing.md,
            bottom = if (compact) DipeSpacing.sm else DipeSpacing.md,
          ),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style =
          if (compact) {
            LocalDipeTypography.current.bookCoverTitle.copy(fontSize = 12.sp, lineHeight = 15.sp)
          } else {
            LocalDipeTypography.current.bookCoverTitle
          },
        color = onCloth,
        maxLines = if (compact) 3 else 4,
        overflow = TextOverflow.Ellipsis,
      )

      Column {
        Box(
          modifier =
            Modifier.width(if (compact) 16.dp else 24.dp)
              .height(0.75.dp)
              .background(onCloth.copy(alpha = 0.35f))
        )
        Spacer(modifier = Modifier.height(DipeSpacing.xs))
        Text(
          text = author.uppercase(),
          style =
            LocalDipeTypography.current.editorialKicker.copy(
              fontSize = if (compact) 8.sp else 9.5.sp,
              letterSpacing = 1.1.sp,
            ),
          color = onCloth.copy(alpha = 0.78f),
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
  val coverCd = stringResource(R.string.cd_book_cover, title)
  val clothColor =
    remember(title, author, coverTokens) {
      val idx =
        abs((title.hashCode() * 31 + author.hashCode())) % coverTokens.clothPalettes.size
      coverTokens.clothPalettes[idx]
    }
  val onCloth =
    remember(clothColor, coverTokens) {
      if (clothColor.luminance() > 0.45f) coverTokens.darkInkOnLightCloth
      else coverTokens.lightInkOnDarkCloth
    }

  Box(
    modifier =
      modifier
        .height(height)
        .width(48.dp)
        .clip(RoundedCornerShape(DipeRadii.sharp))
        .background(clothColor)
        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DipeRadii.sharp))
        .semantics { contentDescription = coverCd }
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
        Box(modifier = Modifier.width(12.dp).height(1.dp).background(onCloth.copy(alpha = 0.3f)))
      }
      Text(
        text = title.take(18),
        style = LocalDipeTypography.current.editorialKicker.copy(fontSize = 8.sp, lineHeight = 10.sp),
        color = onCloth,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      Box(modifier = Modifier.width(12.dp).height(1.dp).background(onCloth.copy(alpha = 0.3f)))
    }
  }
}
