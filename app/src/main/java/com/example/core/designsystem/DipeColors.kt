package com.example.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.core.model.AppThemeMode

/**
 * Monochrome tokens independent from reading themes.
 */
@Immutable
data class DipeBrandTokens(
  val monochromeInk: Color = Color(0xFF141413),
  val monochromePaper: Color = Color(0xFFF6F4EE),
  val monochromeMuted: Color = Color(0xFF6B6964),
  val monochromeHairline: Color = Color(0x2E141413),
  val badgeContainer: Color = Color(0xFF141413),
  val badgeContent: Color = Color(0xFFF6F4EE),
  val badgeBorder: Color = Color(0x2E141413),
  val avatarRing: Color = Color(0xFFFAF8F3),
)

/**
 * Precomputed palette entry for tactile cloth-bound cover rendering with zero per-frame allocations.
 */
@Immutable
data class DipeCoverPaletteEntry(
  val clothColor: Color,
  val clothHighlight: Color,
  val clothShade: Color,
  val onCloth: Color,
  val spineCreasePrimary: Color,
  val spineCreaseSecondary: Color,
  val ruleColor: Color,
  val authorColor: Color,
  val frameBorderColor: Color,
) {
  companion object {
    fun fromCloth(
      cloth: Color,
      darkInk: Color,
      lightInk: Color,
    ): DipeCoverPaletteEntry {
      val isLight = cloth.luminance() > 0.45f
      val onCloth = if (isLight) darkInk else lightInk
      return DipeCoverPaletteEntry(
        clothColor = cloth,
        clothHighlight =
          if (isLight) Color(0x33FFFFFF) else Color(0x1CFFFFFF),
        clothShade =
          if (isLight) Color(0x1E1C1A17) else Color(0x3D0A0908),
        onCloth = onCloth,
        spineCreasePrimary = onCloth.copy(alpha = if (isLight) 0.18f else 0.24f),
        spineCreaseSecondary = onCloth.copy(alpha = 0.10f),
        ruleColor = onCloth.copy(alpha = 0.36f),
        authorColor = onCloth.copy(alpha = 0.80f),
        frameBorderColor = onCloth.copy(alpha = 0.16f),
      )
    }
  }
}

@Immutable
data class DipeCoverTokens(
  val favoriteRibbon: Color = Color(0xFFB34436),
  val pageEdgeLight: Color = Color(0xFFEBE4D4),
  val pageEdgeShadow: Color = Color(0xFFCFC6B4),
  val darkInkOnLightCloth: Color = Color(0xFF1C1A17),
  val lightInkOnDarkCloth: Color = Color(0xFFF4F0E7),
  val entries: List<DipeCoverPaletteEntry> =
    listOf(
        Color(0xFF28333C), // Deep slate cloth
        Color(0xFF3E3128), // Espresso leather
        Color(0xFF2F3C34), // Forest linen
        Color(0xFF8C3E2F), // Classic terracotta cloth
        Color(0xFF232226), // Charcoal ink
        Color(0xFF53483A), // Warm umber buckram
        Color(0xFFD8AE47), // Ochre editorial gold
        Color(0xFFE9E1D2), // Natural parchment board
        Color(0xFF364540), // Sage archive linen
        Color(0xFFD9D1C3), // Warm stone cover
      )
      .map { cloth ->
        DipeCoverPaletteEntry.fromCloth(
          cloth = cloth,
          darkInk = Color(0xFF1C1A17),
          lightInk = Color(0xFFF4F0E7),
        )
      },
) {
  fun paletteFor(title: String, author: String): DipeCoverPaletteEntry {
    val hash = (title.hashCode() * 31 + author.hashCode()) and Int.MAX_VALUE
    return entries[hash % entries.size]
  }
}

val DefaultCoverTokens = DipeCoverTokens()

val LocalDipeBrandTokens = staticCompositionLocalOf { DipeBrandTokens() }

val LocalDipeCoverTokens = staticCompositionLocalOf { DefaultCoverTokens }

fun resolveBrandTokens(isDarkEffective: Boolean): DipeBrandTokens =
  if (isDarkEffective) {
    DipeBrandTokens(
      badgeContainer = Color(0xFF1E1C19),
      badgeContent = Color(0xFFF6F4EE),
      badgeBorder = Color(0x6BF6F4EE),
      avatarRing = Color(0x80F6F4EE),
    )
  } else {
    DipeBrandTokens()
  }

val PaperColorScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF181714),
    onPrimary = Color(0xFFFAF8F3),
    primaryContainer = Color(0xFFEBE5D9),
    onPrimaryContainer = Color(0xFF181714),
    secondary = Color(0xFFA6442E),
    onSecondary = Color(0xFFFAF8F3),
    secondaryContainer = Color(0xFFF3E2DC),
    onSecondaryContainer = Color(0xFF4F1D12),
    tertiary = Color(0xFF4B534E),
    onTertiary = Color(0xFFFAF8F3),
    tertiaryContainer = Color(0xFFE3E5E0),
    onTertiaryContainer = Color(0xFF262B28),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF181714),
    surface = Color(0xFFFAF8F3),
    onSurface = Color(0xFF181714),
    surfaceVariant = Color(0xFFEFEAE0),
    onSurfaceVariant = Color(0xFF635D55),
    surfaceContainerLowest = Color(0xFFFDFCF9),
    surfaceContainerLow = Color(0xFFF2EDE3),
    surfaceContainer = Color(0xFFEBE4D7),
    surfaceContainerHigh = Color(0xFFE2DACE),
    surfaceContainerHighest = Color(0xFFD8D0C2),
    outline = Color(0xFF8C8478),
    outlineVariant = Color(0xFFDCD5C7),
    error = Color(0xFF9E2F2F),
    onError = Color(0xFFFAF8F3),
  )

val InkColorScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFFEAE5DA),
    onPrimary = Color(0xFF131210),
    primaryContainer = Color(0xFF292621),
    onPrimaryContainer = Color(0xFFEAE5DA),
    secondary = Color(0xFFD08D6E),
    onSecondary = Color(0xFF1C1410),
    secondaryContainer = Color(0xFF2D231E),
    onSecondaryContainer = Color(0xFFE8C8B8),
    tertiary = Color(0xFF9AA39D),
    onTertiary = Color(0xFF151816),
    tertiaryContainer = Color(0xFF212522),
    onTertiaryContainer = Color(0xFFC8D0CB),
    background = Color(0xFF12110F),
    onBackground = Color(0xFFEAE5DA),
    surface = Color(0xFF12110F),
    onSurface = Color(0xFFEAE5DA),
    surfaceVariant = Color(0xFF1D1B18),
    onSurfaceVariant = Color(0xFFA79F93),
    surfaceContainerLowest = Color(0xFF0E0D0B),
    surfaceContainerLow = Color(0xFF191714),
    surfaceContainer = Color(0xFF211E1A),
    surfaceContainerHigh = Color(0xFF292621),
    surfaceContainerHighest = Color(0xFF322E28),
    outline = Color(0xFF767067),
    outlineVariant = Color(0xFF2F2B25),
    error = Color(0xFFD98282),
    onError = Color(0xFF220A0A),
  )

val SepiaColorScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF261E15),
    onPrimary = Color(0xFFF3ECDC),
    primaryContainer = Color(0xFFE2D6BE),
    onPrimaryContainer = Color(0xFF261E15),
    secondary = Color(0xFF9A4426),
    onSecondary = Color(0xFFF3ECDC),
    secondaryContainer = Color(0xFFEAD4C4),
    onSecondaryContainer = Color(0xFF4D1E0F),
    tertiary = Color(0xFF58594A),
    onTertiary = Color(0xFFF3ECDC),
    tertiaryContainer = Color(0xFFE1DEC9),
    onTertiaryContainer = Color(0xFF2F3025),
    background = Color(0xFFF1E9D6),
    onBackground = Color(0xFF261E15),
    surface = Color(0xFFF1E9D6),
    onSurface = Color(0xFF261E15),
    surfaceVariant = Color(0xFFE5DCC5),
    onSurfaceVariant = Color(0xFF635545),
    surfaceContainerLowest = Color(0xFFF5EFE0),
    surfaceContainerLow = Color(0xFFE8DEC6),
    surfaceContainer = Color(0xFFE0D4B9),
    surfaceContainerHigh = Color(0xFFD7CAAD),
    surfaceContainerHighest = Color(0xFFCEBF9F),
    outline = Color(0xFF867561),
    outlineVariant = Color(0xFFCEC0A4),
    error = Color(0xFF8E3026),
    onError = Color(0xFFF3ECDC),
  )

fun resolveColorScheme(themeMode: AppThemeMode, isSystemDark: Boolean): Pair<ColorScheme, Boolean> =
  when (themeMode) {
    AppThemeMode.PAPER -> PaperColorScheme to false
    AppThemeMode.INK -> InkColorScheme to true
    AppThemeMode.SEPIA -> SepiaColorScheme to false
    AppThemeMode.SYSTEM ->
      if (isSystemDark) InkColorScheme to true else PaperColorScheme to false
  }
