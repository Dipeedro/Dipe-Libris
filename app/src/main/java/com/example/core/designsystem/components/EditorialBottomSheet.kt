package com.example.core.designsystem.components

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.core.designsystem.DipeElevation
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.TopSquircleShape
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val EDITORIAL_SHEET_ANIM_MS = 220
private val EditorialSheetEasing = FastOutSlowInEasing
private val EditorialWarmScrimColor = Color(0xFF211A14).copy(alpha = 0.33f)

/**
 * Full-bleed edge-to-edge editorial modal bottom sheet whose scrim covers 100% of the viewport
 * behind status and navigation bars, extends its surface color behind the navigation bar, and
 * couples sheet + scrim transitions with a unified 220ms FastOutSlowInEasing curve.
 */
@Composable
fun EditorialModalBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetTestTag: String? = null,
  dismissOnBackPress: Boolean = true,
  dismissOnScrimClick: Boolean = true,
  scrollableContent: Boolean = true,
  containerColor: Color = DipeTheme.colors.surfaceElevated,
  footer: (@Composable (requestDismiss: () -> Unit) -> Unit)? = null,
  content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
  val reduceMotion = DipeTheme.motion.reduceMotion
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val isLightSheetBackground = containerColor.luminance() > 0.5f

  val initialEnterOffsetPx =
    remember(reduceMotion, density) {
      if (reduceMotion) 0f else with(density) { 180.dp.toPx() }
    }
  val enterProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val dragOffsetY = remember { Animatable(0f) }
  var sheetHeightPx by remember { mutableFloatStateOf(1f) }
  var isClosing by remember { mutableStateOf(false) }
  var lastNestedDragDeltaY by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(reduceMotion) {
    if (reduceMotion) {
      enterProgress.snapTo(1f)
    } else {
      enterProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = EDITORIAL_SHEET_ANIM_MS, easing = EditorialSheetEasing),
      )
    }
  }

  val startClosingAnimation: () -> Unit = {
    if (!isClosing) {
      isClosing = true
      scope.launch {
        try {
          if (reduceMotion) {
            dragOffsetY.snapTo(sheetHeightPx)
          } else {
            dragOffsetY.animateTo(
              targetValue = sheetHeightPx,
              animationSpec =
                tween(durationMillis = EDITORIAL_SHEET_ANIM_MS, easing = EditorialSheetEasing),
            )
          }
          onDismissRequest()
        } catch (_: CancellationException) {
          isClosing = false
        }
      }
    }
  }

  val snapBackToOpen: () -> Unit = {
    isClosing = false
    scope.launch {
      if (reduceMotion) {
        dragOffsetY.snapTo(0f)
      } else {
        dragOffsetY.animateTo(
          targetValue = 0f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
        )
      }
    }
  }

  val evaluateRelease: (velocityY: Float, lastDeltaY: Float) -> Unit = { velocityY, lastDeltaY ->
    val flingDownThresholdPx = with(density) { 400.dp.toPx() }
    val flingUpThresholdPx = with(density) { -120.dp.toPx() }
    val distanceThresholdPx = sheetHeightPx * 0.30f
    val lastMoveWasUpward = lastDeltaY < 0f || velocityY < flingUpThresholdPx
    val shouldClose =
      !lastMoveWasUpward &&
        (dragOffsetY.value > distanceThresholdPx || velocityY > flingDownThresholdPx)
    if (shouldClose) {
      startClosingAnimation()
    } else {
      snapBackToOpen()
    }
  }

  val sheetNestedScroll =
    remember(reduceMotion) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
          if (abs(available.y) > 0.5f) {
            lastNestedDragDeltaY = available.y
          }
          val current = dragOffsetY.value
          if (available.y < 0f && current > 0f) {
            val next = (current + available.y).coerceAtLeast(0f)
            val consumedY = next - current
            scope.launch { dragOffsetY.snapTo(next) }
            return Offset(0f, consumedY)
          }
          return Offset.Zero
        }

        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (source != NestedScrollSource.UserInput) return Offset.Zero
          if (abs(available.y) > 0.5f) {
            lastNestedDragDeltaY = available.y
          }
          if (available.y > 0f) {
            val next = (dragOffsetY.value + available.y).coerceAtLeast(0f)
            scope.launch { dragOffsetY.snapTo(next) }
            return Offset(0f, available.y)
          }
          return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          if (dragOffsetY.value > 0f) {
            evaluateRelease(available.y, lastNestedDragDeltaY)
            lastNestedDragDeltaY = 0f
            return available
          }
          lastNestedDragDeltaY = 0f
          return Velocity.Zero
        }
      }
    }

  Dialog(
    onDismissRequest = {
      if (dismissOnBackPress) startClosingAnimation()
    },
    properties =
      DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    if (dismissOnBackPress) {
      BackHandler { startClosingAnimation() }
    }

    val dialogView = LocalView.current
    if (!dialogView.isInEditMode) {
      SideEffect {
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
          WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
          dialogWindow.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
          dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
          dialogWindow.setDimAmount(0f)
          dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
          val attrs = dialogWindow.attributes
          var attrsModified = false
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val desiredCutoutMode =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
              } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
              }
            if (attrs.layoutInDisplayCutoutMode != desiredCutoutMode) {
              attrs.layoutInDisplayCutoutMode = desiredCutoutMode
              attrsModified = true
            }
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (attrs.fitInsetsTypes != 0 || attrs.fitInsetsSides != 0) {
              attrs.fitInsetsTypes = 0
              attrs.fitInsetsSides = 0
              attrsModified = true
            }
          }
          if (attrsModified) {
            dialogWindow.attributes = attrs
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dialogWindow.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            dialogWindow.isStatusBarContrastEnforced = false
          }
          @Suppress("DEPRECATION")
          dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
          @Suppress("DEPRECATION")
          dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
          val insetsController = WindowCompat.getInsetsController(dialogWindow, dialogView)
          insetsController.isAppearanceLightNavigationBars = isLightSheetBackground
          insetsController.isAppearanceLightStatusBars = false
        }
      }
    }

    val dragFraction =
      if (sheetHeightPx > 1f) (dragOffsetY.value / sheetHeightPx).coerceIn(0f, 1f) else 0f
    val scrimOpacity = (enterProgress.value * (1f - dragFraction)).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
      val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      val sheetMaxHeight = (maxHeight - statusTopPadding - 24.dp).coerceAtLeast(240.dp)

      Box(
        modifier =
          Modifier.fillMaxSize()
            .graphicsLayer { alpha = scrimOpacity }
            .background(EditorialWarmScrimColor)
            .let { base ->
              if (dismissOnScrimClick) {
                base.pointerInput(Unit) { detectTapGestures { startClosingAnimation() } }
              } else {
                base
              }
            },
      )

      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
      ) {
        Surface(
          modifier =
            Modifier.widthIn(max = 640.dp)
              .fillMaxWidth()
              .heightIn(max = sheetMaxHeight)
              .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
              .graphicsLayer {
                val enterOffset = (1f - enterProgress.value) * initialEnterOffsetPx
                translationY = (enterOffset + dragOffsetY.value).coerceAtLeast(0f)
              }
              .pointerInput(Unit) {
                awaitPointerEventScope {
                  while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && isClosing) {
                      isClosing = false
                      scope.launch { dragOffsetY.stop() }
                    }
                  }
                }
              }
              .nestedScroll(sheetNestedScroll),
          shape = TopSquircleShape(cornerRadius = 24.dp),
          color = containerColor,
          tonalElevation = DipeElevation.flat,
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 38.dp)
                  .pointerInput(reduceMotion) {
                    awaitEachGesture {
                      val down = awaitFirstDown(requireUnconsumed = false)
                      isClosing = false
                      scope.launch { dragOffsetY.stop() }
                      var dragY = dragOffsetY.value
                      var lastDeltaY = 0f
                      val velocityTracker = VelocityTracker()
                      velocityTracker.addPosition(down.uptimeMillis, down.position)
                      var activeId = down.id

                      while (true) {
                        val event = awaitPointerEvent()
                        val change =
                          event.changes.firstOrNull { it.id == activeId }
                            ?: event.changes.firstOrNull()
                            ?: break
                        activeId = change.id
                        if (!change.pressed) {
                          change.consume()
                          break
                        }
                        val dy = change.positionChange().y
                        if (abs(dy) > 0.5f) {
                          lastDeltaY = dy
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        dragY = (dragY + dy).coerceAtLeast(0f)
                        scope.launch { dragOffsetY.snapTo(dragY) }
                      }

                      val velocityY = velocityTracker.calculateVelocity().y
                      evaluateRelease(velocityY, lastDeltaY)
                    }
                  },
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier.padding(top = 14.dp, bottom = 10.dp)
                    .width(34.dp)
                    .height(4.dp)
                    .background(
                      color = DipeTheme.colors.textSecondary.copy(alpha = 0.45f),
                      shape = CircleShape,
                    ),
              )
            }

            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .weight(1f, fill = false)
                  .let { base ->
                    if (scrollableContent) base.verticalScroll(rememberScrollState()) else base
                  }
                  .let { base ->
                    if (footer == null) {
                      base.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    } else {
                      base
                    }
                  }
                  .let { base ->
                    if (sheetTestTag != null) base.testTag(sheetTestTag) else base
                  },
            ) {
              content(startClosingAnimation)
            }

            if (footer != null) {
              Box(
                modifier =
                  Modifier.fillMaxWidth()
                    .background(containerColor)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
              ) {
                footer(startClosingAnimation)
              }
            }
          }
        }
      }
    }
  }
}
