package com.example.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
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
  fun <T> standardTween(durationMillis: Int = 220): FiniteAnimationSpec<T> =
    if (reduceMotion) snap()
    else tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)

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

class TopSquircleShape(
  private val cornerRadius: Dp = 22.dp,
) : androidx.compose.ui.graphics.Shape {
  override fun createOutline(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
  ): androidx.compose.ui.graphics.Outline {
    val radiusPx = with(density) { cornerRadius.toPx() }
    val reach = (radiusPx * 1.4f).coerceAtMost(size.width * 0.45f).coerceAtMost(size.height * 0.85f)
    val handle = reach * 0.30f
    val path =
      androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, reach)
        cubicTo(
          x1 = 0f,
          y1 = handle,
          x2 = handle,
          y2 = 0f,
          x3 = reach,
          y3 = 0f,
        )
        lineTo(size.width - reach, 0f)
        cubicTo(
          x1 = size.width - handle,
          y1 = 0f,
          x2 = size.width,
          y2 = handle,
          x3 = size.width,
          y3 = reach,
        )
        lineTo(size.width, size.height)
        close()
      }
    return androidx.compose.ui.graphics.Outline.Generic(path)
  }
}

class TopEndSquircleShape(
  private val cornerRadius: Dp = 24.dp,
) : androidx.compose.ui.graphics.Shape {
  override fun createOutline(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density,
  ): androidx.compose.ui.graphics.Outline {
    val radiusPx = with(density) { cornerRadius.toPx() }
    val reach = (radiusPx * 1.4f).coerceAtMost(size.width * 0.65f).coerceAtMost(size.height * 0.45f)
    val handle = reach * 0.30f
    val path =
      androidx.compose.ui.graphics.Path().apply {
        if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
          moveTo(size.width, 0f)
          lineTo(reach, 0f)
          cubicTo(
            x1 = handle,
            y1 = 0f,
            x2 = 0f,
            y2 = handle,
            x3 = 0f,
            y3 = reach,
          )
          lineTo(0f, size.height)
          lineTo(size.width, size.height)
          close()
        } else {
          moveTo(0f, 0f)
          lineTo(size.width - reach, 0f)
          cubicTo(
            x1 = size.width - handle,
            y1 = 0f,
            x2 = size.width,
            y2 = handle,
            x3 = size.width,
            y3 = reach,
          )
          lineTo(size.width, size.height)
          lineTo(0f, size.height)
          close()
        }
      }
    return androidx.compose.ui.graphics.Outline.Generic(path)
  }
}

