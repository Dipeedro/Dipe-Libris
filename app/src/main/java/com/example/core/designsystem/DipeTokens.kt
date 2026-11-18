package com.example.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object DipeSpacing {
  val xxs: Dp = 2.dp
  val xs: Dp = 4.dp
  val sm: Dp = 8.dp
  val compact: Dp = 12.dp
  val md: Dp = 16.dp
  val lg: Dp = 24.dp
  val xl: Dp = 32.dp
  val xxl: Dp = 48.dp

  fun horizontalMargin(widthClass: WindowWidthClass): Dp =
    if (widthClass == WindowWidthClass.COMPACT) 24.dp else 40.dp
}

object DipeRadii {
  val none: Dp = 0.dp
  val sharp: Dp = 2.dp
  val control: Dp = 10.dp
  val surface: Dp = 16.dp

  // Compatibility aliases for existing screens
  val subtle: Dp = control
  val card: Dp = control
  val sheet: Dp = surface
}

object DipeElevation {
  val flat: Dp = 0.dp
  val raised: Dp = 2.dp

  // Compatibility aliases for existing screens
  val whisper: Dp = flat
  val subtle: Dp = raised
}

val DipeShapes =
  Shapes(
    extraSmall = RoundedCornerShape(DipeRadii.sharp),
    small = RoundedCornerShape(DipeRadii.control),
    medium = RoundedCornerShape(DipeRadii.control),
    large = RoundedCornerShape(DipeRadii.surface),
    extraLarge = RoundedCornerShape(DipeRadii.surface),
  )

@Immutable
data class DipeMotionConfig(val reduceMotion: Boolean = false) {
  fun <T> standardTween(durationMillis: Int = 200): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(durationMillis = durationMillis)

  fun <T> editorialSpring(): FiniteAnimationSpec<T> =
    if (reduceMotion) snap()
    else
      spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
      )
}

val LocalDipeMotion = staticCompositionLocalOf { DipeMotionConfig(reduceMotion = false) }

fun isSystemReduceMotionEnabled(context: Context): Boolean =
  try {
    val scale =
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    scale == 0f
  } catch (_: Exception) {
    false
  }

enum class WindowWidthClass {
  COMPACT,
  MEDIUM,
  EXPANDED;

  companion object {
    fun fromWidth(width: Dp): WindowWidthClass =
      when {
        width < 600.dp -> COMPACT
        width < 840.dp -> MEDIUM
        else -> EXPANDED
      }
  }
}
