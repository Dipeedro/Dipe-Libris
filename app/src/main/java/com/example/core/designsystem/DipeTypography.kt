package com.example.core.designsystem

import android.os.Build
import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.TypographyPreset

@OptIn(ExperimentalTextApi::class)
private fun buildEditorialFontFamily(@FontRes resId: Int): FontFamily =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
      Font(
        resId = resId,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
      ),
      Font(
        resId = resId,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
      ),
      Font(
        resId = resId,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
      ),
    )
  } else {
    FontFamily(
      Font(resId = resId, weight = FontWeight.Normal),
      Font(resId = resId, weight = FontWeight.Medium),
      Font(resId = resId, weight = FontWeight.SemiBold),
    )
  }

val PlayfairDisplayFontFamily = buildEditorialFontFamily(R.font.playfair_display)
val CormorantGaramondFontFamily = buildEditorialFontFamily(R.font.cormorant_garamond)
val LoraFontFamily = buildEditorialFontFamily(R.font.lora)
val InterFontFamily = buildEditorialFontFamily(R.font.inter)
val JetBrainsMonoFontFamily = buildEditorialFontFamily(R.font.jetbrains_mono)

@Immutable
data class DipeTypographyExtensions(
  val preset: TypographyPreset,
  val displayFontFamily: FontFamily,
  val bodyFontFamily: FontFamily,
  val monoMetadata: TextStyle,
  val editorialKicker: TextStyle,
  val bookCoverTitle: TextStyle,
  val bookCoverTitleCompact: TextStyle,
  val bookCoverAuthor: TextStyle,
  val bookCoverAuthorCompact: TextStyle,
  val bookSpineTitle: TextStyle,
)

val LocalDipeTypography =
  staticCompositionLocalOf {
    buildTypographyBundle(TypographyPreset.CLASSIC_EDITORIAL).second
  }

fun displayFontFamilyFor(preset: TypographyPreset): FontFamily =
  when (preset) {
    TypographyPreset.CLASSIC_EDITORIAL -> PlayfairDisplayFontFamily
    TypographyPreset.LITERARY_PRESS -> CormorantGaramondFontFamily
    TypographyPreset.MODERN_ARCHIVE -> LoraFontFamily
  }

fun buildTypographyBundle(preset: TypographyPreset): Pair<Typography, DipeTypographyExtensions> {
  val (displayFamily, bodyFamily) =
    when (preset) {
      TypographyPreset.CLASSIC_EDITORIAL -> PlayfairDisplayFontFamily to InterFontFamily
      TypographyPreset.LITERARY_PRESS -> CormorantGaramondFontFamily to LoraFontFamily
      TypographyPreset.MODERN_ARCHIVE -> LoraFontFamily to InterFontFamily
    }

  val typography =
    Typography(
      displayLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 32.sp,
          lineHeight = 38.sp,
          letterSpacing = (-0.4).sp,
        ),
      displayMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 25.sp,
          lineHeight = 31.sp,
          letterSpacing = (-0.2).sp,
        ),
      headlineLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 23.sp,
          lineHeight = 29.sp,
          letterSpacing = (-0.15).sp,
        ),
      headlineMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 19.sp,
          lineHeight = 25.sp,
          letterSpacing = 0.sp,
        ),
      headlineSmall =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 17.sp,
          lineHeight = 23.sp,
          letterSpacing = 0.sp,
        ),
      titleLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 17.sp,
          lineHeight = 23.sp,
          letterSpacing = 0.sp,
        ),
      titleMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 15.sp,
          lineHeight = 21.sp,
          letterSpacing = 0.05.sp,
        ),
      titleSmall =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.1.sp,
        ),
      bodyLarge =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 15.5.sp,
          lineHeight = 24.sp,
          letterSpacing = 0.1.sp,
        ),
      bodyMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          letterSpacing = 0.12.sp,
        ),
      bodySmall =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 12.5.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.15.sp,
        ),
      labelLarge =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.2.sp,
        ),
      labelMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 11.5.sp,
          lineHeight = 16.sp,
          letterSpacing = 0.35.sp,
        ),
      labelSmall =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          letterSpacing = 0.8.sp,
          fontFeatureSettings = "tnum",
        ),
    )

  val editorialKicker =
    TextStyle(
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 10.5.sp,
      lineHeight = 14.sp,
      letterSpacing = 1.4.sp,
    )

  val bookCoverTitle =
    TextStyle(
      fontFamily = displayFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = 20.sp,
      letterSpacing = (-0.1).sp,
    )

  val extensions =
    DipeTypographyExtensions(
      preset = preset,
      displayFontFamily = displayFamily,
      bodyFontFamily = bodyFamily,
      monoMetadata =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          letterSpacing = 0.3.sp,
          fontFeatureSettings = "tnum",
        ),
      editorialKicker = editorialKicker,
      bookCoverTitle = bookCoverTitle,
      bookCoverTitleCompact = bookCoverTitle.copy(fontSize = 12.sp, lineHeight = 15.sp),
      bookCoverAuthor = editorialKicker.copy(fontSize = 9.5.sp, letterSpacing = 1.1.sp),
      bookCoverAuthorCompact = editorialKicker.copy(fontSize = 8.sp, letterSpacing = 0.9.sp),
      bookSpineTitle =
        editorialKicker.copy(fontSize = 8.5.sp, lineHeight = 10.5.sp, letterSpacing = 0.8.sp),
    )

  return typography to extensions
}
