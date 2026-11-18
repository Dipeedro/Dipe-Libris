package com.example.core.designsystem

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.core.model.AppThemeMode
import com.example.core.model.TypographyPreset

@Composable
fun DipeLibrisTheme(
  themeMode: AppThemeMode = AppThemeMode.PAPER,
  typographyPreset: TypographyPreset = TypographyPreset.CLASSIC_EDITORIAL,
  userReduceMotion: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isSystemDark = isSystemInDarkTheme()
  val (colorScheme, isDarkEffective) =
    remember(themeMode, isSystemDark) { resolveColorScheme(themeMode, isSystemDark) }
  val brandTokens = remember(isDarkEffective) { resolveBrandTokens(isDarkEffective) }
  val (typography, typographyExtensions) =
    remember(typographyPreset) { buildTypographyBundle(typographyPreset) }

  val context = LocalContext.current
  var systemReduceMotion by remember(context) {
    mutableStateOf(isSystemReduceMotionEnabled(context))
  }
  DisposableEffect(context) {
    val resolver = context.contentResolver
    val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
    val observer =
      object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
          systemReduceMotion = isSystemReduceMotionEnabled(context)
        }
      }
    try {
      resolver.registerContentObserver(uri, false, observer)
    } catch (_: Exception) {
      // Ignore on restricted environments
    }
    systemReduceMotion = isSystemReduceMotionEnabled(context)
    onDispose {
      try {
        resolver.unregisterContentObserver(observer)
      } catch (_: Exception) {
        // Ignore
      }
    }
  }

  val effectiveReduceMotion = userReduceMotion || systemReduceMotion
  val motionConfig = remember(effectiveReduceMotion) { DipeMotionConfig(effectiveReduceMotion) }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !isDarkEffective
        insetsController.isAppearanceLightNavigationBars = !isDarkEffective
      }
    }
  }

  val semanticColors = remember(colorScheme) { resolveSemanticColors(colorScheme) }

  CompositionLocalProvider(
    LocalDipeColors provides semanticColors,
    LocalDipeTypography provides typographyExtensions,
    LocalDipeMotion provides motionConfig,
    LocalDipeBrandTokens provides brandTokens,
    LocalDipeCoverTokens provides DefaultCoverTokens,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = DipeShapes,
      content = content,
    )
  }
}

object DipeTheme {
  val colors: DipeSemanticColors
    @Composable get() = LocalDipeColors.current

  val typography: DipeTypographyExtensions
    @Composable get() = LocalDipeTypography.current

  val brand: DipeBrandTokens
    @Composable get() = LocalDipeBrandTokens.current

  val motion: DipeMotionConfig
    @Composable get() = LocalDipeMotion.current
}

/**
 * Allows wrapping individual book detail sheets in a per-book typographic preset
 * in future phases without altering the surrounding theme.
 */
@Composable
fun DipeTypographyScope(
  presetOverride: TypographyPreset?,
  content: @Composable () -> Unit,
) {
  if (presetOverride == null) {
    content()
  } else {
    val (typography, extensions) = remember(presetOverride) { buildTypographyBundle(presetOverride) }
    CompositionLocalProvider(LocalDipeTypography provides extensions) {
      MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = typography,
        shapes = MaterialTheme.shapes,
        content = content,
      )
    }
  }
}
