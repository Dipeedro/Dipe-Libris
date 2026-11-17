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
  val editorialMasthead: TextStyle,
  val navBarLabel: TextStyle,
  val monoMetadata: TextStyle,
  val editorialKicker: TextStyle,
  val bookCoverTitle: TextStyle,
  val bookCoverMonogram: TextStyle,
  val bookCoverAuthor: TextStyle,
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

fun bodyFontFamilyFor(preset: TypographyPreset): FontFamily =
  when (preset) {
    TypographyPreset.CLASSIC_EDITORIAL -> InterFontFamily
    TypographyPreset.LITERARY_PRESS -> LoraFontFamily
    TypographyPreset.MODERN_ARCHIVE -> InterFontFamily
  }

fun buildTypographyBundle(preset: TypographyPreset): Pair<Typography, DipeTypographyExtensions> {
  val displayFamily = displayFontFamilyFor(preset)
  val bodyFamily = bodyFontFamilyFor(preset)

  val typography =
    Typography(
      displayLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 34.sp,
          lineHeight = 40.sp,
          letterSpacing = (-0.5).sp,
        ),
      displayMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 26.sp,
          lineHeight = 32.sp,
          letterSpacing = (-0.25).sp,
        ),
      headlineLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 24.sp,
          lineHeight = 30.sp,
          letterSpacing = (-0.2).sp,
        ),
      headlineMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 20.sp,
          lineHeight = 26.sp,
          letterSpacing = (-0.1).sp,
        ),
      headlineSmall =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 18.sp,
          lineHeight = 24.sp,
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
          fontSize = 15.5.sp,
          lineHeight = 21.sp,
          letterSpacing = 0.sp,
        ),
      titleSmall =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.5.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.05.sp,
        ),
      bodyLarge =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 15.5.sp,
          lineHeight = 25.sp,
          letterSpacing = 0.1.sp,
        ),
      bodyMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 14.sp,
          lineHeight = 21.sp,
          letterSpacing = 0.1.sp,
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
          letterSpacing = 0.15.sp,
        ),
      labelMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 11.5.sp,
          lineHeight = 16.sp,
          letterSpacing = 0.2.sp,
        ),
      labelSmall =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          letterSpacing = 0.6.sp,
          fontFeatureSettings = "tnum",
        ),
    )

  val editorialKicker =
    TextStyle(
      fontFamily = JetBrainsMonoFontFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 10.5.sp,
      lineHeight = 14.sp,
      letterSpacing = 1.3.sp,
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
      editorialMasthead =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 31.sp,
          lineHeight = 36.sp,
          letterSpacing = (-0.5).sp,
        ),
      navBarLabel =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 10.5.sp,
          lineHeight = 14.sp,
          letterSpacing = 0.sp,
        ),
      monoMetadata =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          letterSpacing = 0.25.sp,
          fontFeatureSettings = "tnum",
        ),
      editorialKicker = editorialKicker,
      bookCoverTitle = bookCoverTitle,
      bookCoverMonogram =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 16.sp,
          lineHeight = 20.sp,
          letterSpacing = 0.5.sp,
        ),
      bookCoverAuthor = editorialKicker.copy(fontSize = 9.5.sp, letterSpacing = 1.0.sp),
      bookSpineTitle =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 12.sp,
          lineHeight = 15.sp,
          letterSpacing = 0.2.sp,
        ),
    )

  return typography to extensions
}
