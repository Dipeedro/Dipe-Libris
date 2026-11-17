package com.example.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.core.model.AppThemeMode

/**
 * Monochrome brand tokens independent from editorial reading themes.
 */
@Immutable
data class DipeBrandTokens(
  val monochromeInk: Color = Color(0xFF141413),
  val monochromePaper: Color = Color(0xFFF6F4EE),
  val monochromeMuted: Color = Color(0xFF6B6964),
  val monochromeHairline: Color = Color(0x33141413),
  val badgeContainer: Color = Color(0xFF141413),
  val badgeContent: Color = Color(0xFFF6F4EE),
  val badgeBorder: Color = Color(0x33141413),
  val avatarRing: Color = Color(0xFFFAF7F2),
)

@Immutable
data class DipeCoverTokens(
  val clothPalettes: List<Color> =
    listOf(
      Color(0xFF2B343B), // Deep slate cloth
      Color(0xFF3F332A), // Espresso leather
      Color(0xFF333D35), // Forest linen
      Color(0xFF5A372E), // Terracotta binding
      Color(0xFF28262B), // Charcoal ink
      Color(0xFF474136), // Umber buckram
      Color(0xFFE7DFCF), // Natural parchment board
      Color(0xFFD6CEC0), // Warm stone cover
    ),
  val favoriteRibbon: Color = Color(0xFFB54A3C),
  val darkInkOnLightCloth: Color = Color(0xFF1D1B18),
  val lightInkOnDarkCloth: Color = Color(0xFFF3EFE6),
)

val LocalDipeBrandTokens = staticCompositionLocalOf { DipeBrandTokens() }

val LocalDipeCoverTokens = staticCompositionLocalOf { DipeCoverTokens() }

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
    primary = Color(0xFF191816),
    onPrimary = Color(0xFFFAF7F2),
    primaryContainer = Color(0xFFE9E3D7),
    onPrimaryContainer = Color(0xFF191816),
    secondary = Color(0xFF6E5A47),
    onSecondary = Color(0xFFFAF7F2),
    secondaryContainer = Color(0xFFEDE6DA),
    onSecondaryContainer = Color(0xFF3E3125),
    tertiary = Color(0xFF4F5652),
    onTertiary = Color(0xFFFAF7F2),
    tertiaryContainer = Color(0xFFE3E5E1),
    onTertiaryContainer = Color(0xFF282D2A),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF191816),
    surface = Color(0xFFFAF7F2),
    onSurface = Color(0xFF191816),
    surfaceVariant = Color(0xFFEFEAE1),
    onSurfaceVariant = Color(0xFF5D5952),
    surfaceContainerLowest = Color(0xFFFDFBF8),
    surfaceContainerLow = Color(0xFFF4EFE6),
    surfaceContainer = Color(0xFFEDE7DC),
    surfaceContainerHigh = Color(0xFFE5DED1),
    surfaceContainerHighest = Color(0xFFDDD5C6),
    outline = Color(0xFF8A8378),
    outlineVariant = Color(0xFFDCD5C7),
    error = Color(0xFF8C2D2D),
    onError = Color(0xFFFAF7F2),
  )

val InkColorScheme: ColorScheme =
  darkColorScheme(
    primary = Color(0xFFECE7DD),
    onPrimary = Color(0xFF141311),
    primaryContainer = Color(0xFF2B2823),
    onPrimaryContainer = Color(0xFFECE7DD),
    secondary = Color(0xFFB8A892),
    onSecondary = Color(0xFF1C1915),
    secondaryContainer = Color(0xFF26231E),
    onSecondaryContainer = Color(0xFFDCD0BE),
    tertiary = Color(0xFF9AA39D),
    onTertiary = Color(0xFF161917),
    tertiaryContainer = Color(0xFF232724),
    onTertiaryContainer = Color(0xFFC8D0CB),
    background = Color(0xFF121110),
    onBackground = Color(0xFFECE7DD),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFECE7DD),
    surfaceVariant = Color(0xFF1E1C19),
    onSurfaceVariant = Color(0xFFA7A095),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF181714),
    surfaceContainer = Color(0xFF201E1A),
    surfaceContainerHigh = Color(0xFF282520),
    surfaceContainerHighest = Color(0xFF312E28),
    outline = Color(0xFF777168),
    outlineVariant = Color(0xFF33302A),
    error = Color(0xFFD98282),
    onError = Color(0xFF220A0A),
  )

val SepiaColorScheme: ColorScheme =
  lightColorScheme(
    primary = Color(0xFF282018),
    onPrimary = Color(0xFFF3ECDC),
    primaryContainer = Color(0xFFE2D6BE),
    onPrimaryContainer = Color(0xFF282018),
    secondary = Color(0xFF7A4E2D),
    onSecondary = Color(0xFFF3ECDC),
    secondaryContainer = Color(0xFFE8D8C2),
    onSecondaryContainer = Color(0xFF472C17),
    tertiary = Color(0xFF595A4B),
    onTertiary = Color(0xFFF3ECDC),
    tertiaryContainer = Color(0xFFE1DEC9),
    onTertiaryContainer = Color(0xFF303126),
    background = Color(0xFFF2EBD9),
    onBackground = Color(0xFF282018),
    surface = Color(0xFFF2EBD9),
    onSurface = Color(0xFF282018),
    surfaceVariant = Color(0xFFE6DEC8),
    onSurfaceVariant = Color(0xFF645646),
    surfaceContainerLowest = Color(0xFFF6F0E1),
    surfaceContainerLow = Color(0xFFEBE2CC),
    surfaceContainer = Color(0xFFE3D8BF),
    surfaceContainerHigh = Color(0xFFDACEB1),
    surfaceContainerHighest = Color(0xFFD1C3A4),
    outline = Color(0xFF877662),
    outlineVariant = Color(0xFFCFC2A7),
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
