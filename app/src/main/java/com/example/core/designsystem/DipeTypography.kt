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
)

val LocalDipeTypography =
  staticCompositionLocalOf {
    buildTypographyBundle(TypographyPreset.CLASSIC_EDITORIAL).second
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
          fontSize = 36.sp,
          lineHeight = 44.sp,
          letterSpacing = (-0.5).sp,
        ),
      displayMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 30.sp,
          lineHeight = 36.sp,
          letterSpacing = (-0.25).sp,
        ),
      headlineLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 26.sp,
          lineHeight = 32.sp,
          letterSpacing = (-0.15).sp,
        ),
      headlineMedium =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 22.sp,
          lineHeight = 28.sp,
          letterSpacing = 0.sp,
        ),
      headlineSmall =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 19.sp,
          lineHeight = 26.sp,
          letterSpacing = 0.sp,
        ),
      titleLarge =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 18.sp,
          lineHeight = 24.sp,
          letterSpacing = 0.sp,
        ),
      titleMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 15.sp,
          lineHeight = 22.sp,
          letterSpacing = 0.1.sp,
        ),
      titleSmall =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.15.sp,
        ),
      bodyLarge =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 15.sp,
          lineHeight = 24.sp,
          letterSpacing = 0.15.sp,
        ),
      bodyMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 14.sp,
          lineHeight = 21.sp,
          letterSpacing = 0.15.sp,
        ),
      bodySmall =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 12.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.2.sp,
        ),
      labelLarge =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          letterSpacing = 0.25.sp,
        ),
      labelMedium =
        TextStyle(
          fontFamily = bodyFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 11.sp,
          lineHeight = 16.sp,
          letterSpacing = 0.4.sp,
        ),
      labelSmall =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          letterSpacing = 0.8.sp,
        ),
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
          lineHeight = 16.sp,
          letterSpacing = 0.4.sp,
        ),
      editorialKicker =
        TextStyle(
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          letterSpacing = 1.6.sp,
        ),
      bookCoverTitle =
        TextStyle(
          fontFamily = displayFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 16.sp,
          lineHeight = 20.sp,
          letterSpacing = (-0.1).sp,
        ),
    )

  return typography to extensions
}
