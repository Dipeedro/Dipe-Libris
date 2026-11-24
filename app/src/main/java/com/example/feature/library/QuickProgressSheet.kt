package com.example.feature.library

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.R
import com.example.core.designsystem.DipeElevation
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.TopSquircleShape
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ReadingProgressMath
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PROGRESS_ANIM_DURATION_MS = 220
private val ProgressAnimEasing = FastOutSlowInEasing

@Composable
fun QuickProgressSheet(
  bookTitle: String,
  initialPage: Int,
  totalPages: Int?,
  initialPercent: Float,
  onDismiss: () -> Unit,
  onSavePage: (page: Int, note: String?) -> Unit,
  onSavePercent: (percent: Float, note: String?) -> Unit = { _, _ -> },
  onMarkFinished: (note: String?) -> Unit,
  focusTotalPagesOnOpen: Boolean = false,
  requireTotalPagesIfMissing: Boolean = false,
  onSavePageWithTotal: ((page: Int, note: String?, totalPages: Int?) -> Unit)? = null,
) {
  val language = LocalAppLanguage.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val needsTotalPagesInput = (totalPages == null || totalPages <= 0) && onSavePageWithTotal != null
  var totalPagesInput by remember(bookTitle, totalPages) { mutableStateOf("") }
  var isTotalFieldFocused by remember { mutableStateOf(false) }
  val totalFocusRequester = remember { FocusRequester() }

  val parsedEnteredTotal =
    totalPagesInput.trim().toIntOrNull()?.takeIf { it in 1..50_000 }
  val effectiveTotalPages = totalPages?.takeIf { it > 0 } ?: parsedEnteredTotal
  val hasKnownTotal = effectiveTotalPages != null

  var currentPage by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
  var noteText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showCompleteConfirmation by remember { mutableStateOf(false) }
  var isNoteFieldFocused by remember { mutableStateOf(false) }

  val invalidTotalPagesMsg = stringResource(R.string.progress_validation_total_pages)
  val invalidNegativeMsg = stringResource(R.string.progress_validation_page_negative)
  val invalidExceedsMsg =
    effectiveTotalPages?.let {
      stringResource(
        R.string.progress_validation_page_exceeds,
        LocaleManager.formatInteger(it, language),
      )
    } ?: ""

  if (needsTotalPagesInput && focusTotalPagesOnOpen) {
    LaunchedEffect(Unit) {
      runCatching { totalFocusRequester.requestFocus() }
    }
  }

  val livePreview =
    remember(currentPage, effectiveTotalPages, initialPercent) {
      ReadingProgressMath.calculateFromPage(
        page = currentPage.coerceAtLeast(0),
        totalPages = effectiveTotalPages,
        fallbackPercent = if (effectiveTotalPages != null) 0f else initialPercent,
      )
    }

  val borderlessFieldColors =
    TextFieldDefaults.colors(
      focusedContainerColor = DipeTheme.colors.surfaceTonal,
      unfocusedContainerColor = DipeTheme.colors.surfaceTonal,
      errorContainerColor = DipeTheme.colors.surfaceTonal,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
      disabledIndicatorColor = Color.Transparent,
      errorIndicatorColor = Color.Transparent,
      focusedLabelColor = DipeTheme.colors.readingProgress,
      unfocusedLabelColor = DipeTheme.colors.textSecondary,
      errorLabelColor = DipeTheme.colors.destructive,
      focusedTextColor = DipeTheme.colors.textPrimary,
      unfocusedTextColor = DipeTheme.colors.textPrimary,
      errorTextColor = DipeTheme.colors.textPrimary,
      cursorColor = DipeTheme.colors.readingProgress,
    )

  val applyDelta: (Int) -> Unit = { delta ->
    errorText = null
    currentPage =
      if (effectiveTotalPages != null && effectiveTotalPages > 0) {
        (currentPage + delta).coerceIn(0, effectiveTotalPages)
      } else {
        (currentPage + delta).coerceAtLeast(0)
      }
  }

  val roundedPercent = livePreview.progressPercent.roundToInt().coerceIn(0, 100)
  val formattedPercent = LocaleManager.formatPercent(livePreview.progressPercent, language)
  val pageUnitLabel = stringResource(R.string.progress_page_unit)
  val formattedTotalInline =
    effectiveTotalPages?.let {
      stringResource(
        R.string.progress_of_total_inline,
        LocaleManager.formatInteger(it, language),
      )
    }

  val sheetBackgroundColor = DipeTheme.colors.surfaceElevated
  val isLightSheetBackground = sheetBackgroundColor.luminance() > 0.5f

  // Reversible, interruptible sheet drag & backdrop state
  val initialEnterOffsetPx = remember(reduceMotion, density) {
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
        animationSpec = tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
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
              animationSpec = tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
            )
          }
          onDismiss()
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

  val sheetNestedScroll = remember(reduceMotion) {
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
    onDismissRequest = {},
    properties =
      DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    // Configure the Dialog window for true edge-to-edge full-bleed scrim behind status and navigation bars
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
    val warmScrimColor = Color(0xFF211A14).copy(alpha = 0.33f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val statusTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      val sheetMaxHeight = (maxHeight - statusTopPadding - 24.dp).coerceAtLeast(240.dp)

      // Uniform warm translucent brown scrim covering 100% of screen behind status & nav bars
      Box(
        modifier =
          Modifier.fillMaxSize()
            .graphicsLayer { alpha = scrimOpacity }
            .background(warmScrimColor)
            .pointerInput(Unit) { detectTapGestures { startClosingAnimation() } },
      )

      // Bottom sheet anchored at absolute bottom edge; internal padding preserves nav bar + 28dp cream margin
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
          color = sheetBackgroundColor,
          tonalElevation = DipeElevation.flat,
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Dedicated drag handle area with touch-action: none semantics (consumes vertical drag)
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 38.dp)
                  .testTag("quick_progress_drag_handle")
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
                  .verticalScroll(rememberScrollState())
                  .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                  .padding(horizontal = DipeSpacing.lg)
                  .padding(top = DipeSpacing.xs, bottom = 28.dp)
                  .testTag("quick_progress_sheet"),
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
            ) {
              // Sheet masthead
              Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
                Text(
                  text = stringResource(R.string.progress_sheet_title),
                  style = MaterialTheme.typography.headlineMedium,
                  color = DipeTheme.colors.textPrimary,
                )
                Text(
                  text = bookTitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = DipeTheme.colors.textSecondary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              if (needsTotalPagesInput) {
                val totalFieldShape = RoundedCornerShape(14.dp)
                val totalFieldBorderColor =
                  if (isTotalFieldFocused) DipeTheme.colors.readingProgress.copy(alpha = 0.45f)
                  else Color.Transparent
                TextField(
                  value = totalPagesInput,
                  onValueChange = { raw ->
                    totalPagesInput = raw.filter(Char::isDigit).take(6)
                    errorText = null
                  },
                  label = { Text(stringResource(R.string.progress_field_total_pages)) },
                  singleLine = true,
                  shape = totalFieldShape,
                  colors = borderlessFieldColors,
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Number,
                      imeAction = ImeAction.Done,
                    ),
                  modifier =
                    Modifier.fillMaxWidth()
                      .border(width = 1.dp, color = totalFieldBorderColor, shape = totalFieldShape)
                      .focusRequester(totalFocusRequester)
                      .onFocusChanged { isTotalFieldFocused = it.isFocused }
                      .testTag("progress_total_pages_input"),
                )
              }

              // Borderless tonal Hero Visualizer with flush-left percent (when total is known) + page text
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = DipeTheme.colors.surfaceTonal,
                tonalElevation = DipeElevation.flat,
              ) {
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.md),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  if (hasKnownTotal) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.Bottom,
                    ) {
                      HeroPercentageDisplay(
                        percent = roundedPercent,
                        formattedDigits = LocaleManager.formatInteger(roundedPercent, language),
                        accessibleText = formattedPercent,
                        style = DipeTheme.typography.progressHeroNumber,
                        color = DipeTheme.colors.readingProgress,
                        reduceMotion = reduceMotion,
                      )

                      PageContextDisplay(
                        currentPage = livePreview.currentPage,
                        pagePrefix = pageUnitLabel,
                        formattedPageDigits =
                          LocaleManager.formatInteger(livePreview.currentPage, language),
                        totalInlineSuffix = formattedTotalInline,
                        style = DipeTheme.typography.monoMetadata,
                        color = DipeTheme.colors.textSecondary,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.weight(1f).padding(bottom = DipeSpacing.sm),
                      )
                    }

                    VisualOnlyProgressTrack(
                      progressPercent = livePreview.progressPercent,
                      reduceMotion = reduceMotion,
                    )
                  } else {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        text = stringResource(R.string.progress_total_unknown_hint),
                        style = DipeTheme.typography.monoMetadata,
                        color = DipeTheme.colors.readingProgress,
                      )

                      PageContextDisplay(
                        currentPage = livePreview.currentPage,
                        pagePrefix = pageUnitLabel,
                        formattedPageDigits =
                          LocaleManager.formatInteger(livePreview.currentPage, language),
                        totalInlineSuffix = null,
                        style = DipeTheme.typography.monoMetadata,
                        color = DipeTheme.colors.textSecondary,
                        reduceMotion = reduceMotion,
                      )
                    }
                  }
                }
              }

              // Asymmetric hierarchy steppers: -1 and +1 prominent primary; -10 and +10 compact secondary
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val steppers = listOf(-10, -1, 1, 10)
                steppers.forEach { delta ->
                  val isPrimary = delta == -1 || delta == 1
                  val tagSuffix = if (delta < 0) "minus_${-delta}" else "plus_$delta"
                  val label = if (delta > 0) "+$delta" else delta.toString()
                  TonalStepperButton(
                    label = label,
                    isPrimary = isPrimary,
                    reduceMotion = reduceMotion,
                    onClick = { applyDelta(delta) },
                    modifier =
                      Modifier.weight(if (isPrimary) 1.35f else 0.75f)
                        .heightIn(min = if (isPrimary) 56.dp else 48.dp)
                        .testTag("progress_step_$tagSuffix"),
                  )
                }
              }

              // Borderless tonal short note field
              val fieldShape = RoundedCornerShape(14.dp)
              val noteFieldBorderColor =
                if (isNoteFieldFocused) DipeTheme.colors.readingProgress.copy(alpha = 0.45f)
                else Color.Transparent
              TextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.progress_field_note)) },
                maxLines = 2,
                shape = fieldShape,
                colors = borderlessFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier =
                  Modifier.fillMaxWidth()
                    .border(width = 1.dp, color = noteFieldBorderColor, shape = fieldShape)
                    .onFocusChanged { isNoteFieldFocused = it.isFocused }
                    .testTag("progress_note_input"),
              )

              errorText?.let { message ->
                Text(
                  text = message,
                  style = MaterialTheme.typography.bodySmall,
                  color = DipeTheme.colors.destructive,
                  modifier = Modifier.testTag("progress_validation_error"),
                )
              }

              HairlineDivider()

              // Column footer: secondary outlined "Finish reading" with check icon above full-width terracotta "Save"
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
              ) {
                OutlinedButton(
                  onClick = { showCompleteConfirmation = true },
                  shape = CircleShape,
                  border = BorderStroke(1.dp, DipeTheme.colors.controlBorder),
                  colors =
                    ButtonDefaults.outlinedButtonColors(
                      containerColor = Color.Transparent,
                      contentColor = DipeTheme.colors.textPrimary,
                    ),
                  contentPadding =
                    PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("progress_complete_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.sm))
                  Text(
                    text = stringResource(R.string.progress_complete_short),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                  )
                }

                Button(
                  onClick = {
                    val cleanNote = noteText.trim().takeIf { it.isNotEmpty() }
                    val trimmedTotalInput = totalPagesInput.trim()
                    val hasInvalidEnteredTotal =
                      needsTotalPagesInput &&
                        ((trimmedTotalInput.isNotEmpty() && parsedEnteredTotal == null) ||
                          (requireTotalPagesIfMissing && trimmedTotalInput.isEmpty()))
                    when {
                      hasInvalidEnteredTotal -> errorText = invalidTotalPagesMsg
                      currentPage < 0 -> errorText = invalidNegativeMsg
                      effectiveTotalPages != null && currentPage > effectiveTotalPages ->
                        errorText = invalidExceedsMsg
                      onSavePageWithTotal != null ->
                        onSavePageWithTotal(currentPage, cleanNote, parsedEnteredTotal)
                      else -> onSavePage(currentPage, cleanNote)
                    }
                  },
                  shape = CircleShape,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.secondary,
                      contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                  contentPadding =
                    PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("progress_save_button"),
                ) {
                  Text(
                    text = stringResource(R.string.book_save_action),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                  )
                }
              }

              // Remove extra Spacer at bottom since Column bottom padding already provides 28.dp + safe area
            }
          }
        }
      }
    }
  }

  if (showCompleteConfirmation) {
    AlertDialog(
      onDismissRequest = { showCompleteConfirmation = false },
      shape = MaterialTheme.shapes.large,
      containerColor = DipeTheme.colors.surfaceElevated,
      title = {
        Text(
          text = stringResource(R.string.progress_complete_confirm_title),
          style = MaterialTheme.typography.headlineSmall,
          color = DipeTheme.colors.textPrimary,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.progress_complete_confirm_message, bookTitle),
          style = MaterialTheme.typography.bodyMedium,
          color = DipeTheme.colors.textSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showCompleteConfirmation = false
            onMarkFinished(noteText.trim().takeIf { it.isNotEmpty() })
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          modifier = Modifier.heightIn(min = 48.dp).testTag("progress_complete_confirm_button"),
        ) {
          Text(
            text = stringResource(R.string.progress_complete_confirm_action),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showCompleteConfirmation = false },
          modifier = Modifier.heightIn(min = 48.dp).testTag("progress_complete_cancel_button"),
        ) {
          Text(
            text = stringResource(R.string.book_cancel_action),
            style = MaterialTheme.typography.labelLarge,
            color = DipeTheme.colors.textSecondary,
          )
        }
      },
      modifier = Modifier.testTag("progress_complete_confirm_dialog"),
    )
  }
}

@Composable
private fun HeroPercentageDisplay(
  percent: Int,
  formattedDigits: String,
  accessibleText: String,
  style: TextStyle,
  color: Color,
  reduceMotion: Boolean,
) {
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val liningTabularStyle = remember(style) { style.copy(fontFeatureSettings = "lnum, tnum") }

  val metrics =
    remember(liningTabularStyle, textMeasurer) {
      var maxDigitWidthPx = 0
      var maxHeightPx = 0
      for (digit in 0..9) {
        val res = textMeasurer.measure(text = digit.toString(), style = liningTabularStyle)
        if (res.size.width > maxDigitWidthPx) maxDigitWidthPx = res.size.width
        if (res.size.height > maxHeightPx) maxHeightPx = res.size.height
      }
      val pctRes = textMeasurer.measure(text = "%", style = liningTabularStyle)
      if (pctRes.size.height > maxHeightPx) maxHeightPx = pctRes.size.height
      val lineHeightPx = with(density) { liningTabularStyle.lineHeight.roundToPx() }
      val cellWidthPx = ceil(maxDigitWidthPx * 1.02f).toInt().coerceAtLeast(1)
      val fixedHeightPx = maxOf(maxHeightPx, lineHeightPx).coerceAtLeast(1)
      val reservedWidthPx = cellWidthPx * 3 + pctRes.size.width + cellWidthPx / 4
      Triple(cellWidthPx, fixedHeightPx, reservedWidthPx)
    }

  val cellWidthDp = with(density) { metrics.first.toDp() }
  val fixedHeightDp = with(density) { metrics.second.toDp() }
  val reservedWidthDp = with(density) { metrics.third.toDp() }

  var previousPercent by remember { mutableIntStateOf(percent) }
  val isIncreasing = percent >= previousPercent
  SideEffect { previousPercent = percent }

  Box(
    modifier =
      Modifier.widthIn(min = reservedWidthDp)
        .height(fixedHeightDp)
        .clipToBounds()
        .clearAndSetSemantics {
          testTag = "progress_hero_percent"
          contentDescription = accessibleText
        },
    contentAlignment = Alignment.CenterStart,
  ) {
    if (reduceMotion) {
      HeroPercentRow(
        digits = formattedDigits,
        cellWidthDp = cellWidthDp,
        fixedHeightDp = fixedHeightDp,
        style = liningTabularStyle,
        color = color,
      )
    } else {
      AnimatedContent(
        targetState = percent to formattedDigits,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
          val enter =
            slideInVertically(
              animationSpec =
                tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
            ) { height -> if (isIncreasing) height / 2 else -height / 2 } +
              fadeIn(
                animationSpec =
                  tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
              )
          val exit =
            slideOutVertically(
              animationSpec =
                tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
            ) { height -> if (isIncreasing) -height / 2 else height / 2 } +
              fadeOut(
                animationSpec =
                  tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
              )
          (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "unified_hero_percent",
      ) { (_, targetDigits) ->
        HeroPercentRow(
          digits = targetDigits,
          cellWidthDp = cellWidthDp,
          fixedHeightDp = fixedHeightDp,
          style = liningTabularStyle,
          color = color,
        )
      }
    }
  }
}

@Composable
private fun HeroPercentRow(
  digits: String,
  cellWidthDp: androidx.compose.ui.unit.Dp,
  fixedHeightDp: androidx.compose.ui.unit.Dp,
  style: TextStyle,
  color: Color,
) {
  Row(
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    digits.forEach { ch ->
      Box(
        modifier = Modifier.width(cellWidthDp).height(fixedHeightDp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = ch.toString(),
          style = style,
          color = color,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
    Box(
      modifier = Modifier.height(fixedHeightDp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "%",
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

@Composable
private fun PageContextDisplay(
  currentPage: Int,
  pagePrefix: String,
  formattedPageDigits: String,
  totalInlineSuffix: String?,
  style: TextStyle,
  color: Color,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val liningTabularStyle = remember(style) { style.copy(fontFeatureSettings = "lnum, tnum") }

  val metrics =
    remember(liningTabularStyle, textMeasurer) {
      var maxDigitWidthPx = 0
      var maxHeightPx = 0
      for (digit in 0..9) {
        val res = textMeasurer.measure(text = digit.toString(), style = liningTabularStyle)
        if (res.size.width > maxDigitWidthPx) maxDigitWidthPx = res.size.width
        if (res.size.height > maxHeightPx) maxHeightPx = res.size.height
      }
      val lineHeightPx = with(density) { liningTabularStyle.lineHeight.roundToPx() }
      val cellWidthPx = ceil(maxDigitWidthPx.toDouble()).toInt().coerceAtLeast(1)
      val fixedHeightPx = maxOf(maxHeightPx, lineHeightPx).coerceAtLeast(1)
      cellWidthPx to fixedHeightPx
    }

  val cellWidthDp = with(density) { metrics.first.toDp() }
  val fixedHeightDp = with(density) { metrics.second.toDp() }
  // Width depends ONLY on the number of digits in formattedPageDigits; never varies across numbers with same digit count
  val numberWidthDp = cellWidthDp * formattedPageDigits.length.coerceAtLeast(1)

  var previousPage by remember { mutableIntStateOf(currentPage) }
  val isIncreasing = currentPage >= previousPage
  SideEffect { previousPage = currentPage }

  val fullAccessibleText =
    if (totalInlineSuffix != null) "$pagePrefix $formattedPageDigits $totalInlineSuffix"
    else "$pagePrefix $formattedPageDigits"

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End,
    modifier =
      modifier.height(fixedHeightDp).clearAndSetSemantics {
        testTag = "progress_hero_page"
        contentDescription = fullAccessibleText
      },
  ) {
    // Separate static "p. " element: never moves during roll animation unless digit count changes
    Box(modifier = Modifier.height(fixedHeightDp), contentAlignment = Alignment.Center) {
      Text(
        text = "$pagePrefix ",
        style = liningTabularStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
      )
    }

    // Separate rolling page number box: exact width = digitCount * cellWidthDp, tabular monospace cells
    Box(
      modifier = Modifier.width(numberWidthDp).height(fixedHeightDp).clipToBounds(),
      contentAlignment = Alignment.CenterEnd,
    ) {
      if (reduceMotion) {
        FixedWidthPageDigitsRow(
          digits = formattedPageDigits,
          cellWidthDp = cellWidthDp,
          fixedHeightDp = fixedHeightDp,
          style = liningTabularStyle,
          color = color,
        )
      } else {
        AnimatedContent(
          targetState = currentPage to formattedPageDigits,
          contentAlignment = Alignment.CenterEnd,
          transitionSpec = {
            val enter =
              slideInVertically(
                animationSpec =
                  tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
              ) { height -> if (isIncreasing) height / 2 else -height / 2 } +
                fadeIn(
                  animationSpec =
                    tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
                )
            val exit =
              slideOutVertically(
                animationSpec =
                  tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
              ) { height -> if (isIncreasing) -height / 2 else height / 2 } +
                fadeOut(
                  animationSpec =
                    tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
                )
            (enter togetherWith exit).using(SizeTransform(clip = false))
          },
          label = "unified_page_digits",
        ) { (_, targetDigits) ->
          FixedWidthPageDigitsRow(
            digits = targetDigits,
            cellWidthDp = cellWidthDp,
            fixedHeightDp = fixedHeightDp,
            style = liningTabularStyle,
            color = color,
          )
        }
      }
    }

    // Separate static " de Y" element anchored at right edge: never shifts when digits roll or grow
    if (totalInlineSuffix != null) {
      Box(modifier = Modifier.height(fixedHeightDp), contentAlignment = Alignment.Center) {
        Text(
          text = " $totalInlineSuffix",
          style = liningTabularStyle,
          color = color,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun FixedWidthPageDigitsRow(
  digits: String,
  cellWidthDp: androidx.compose.ui.unit.Dp,
  fixedHeightDp: androidx.compose.ui.unit.Dp,
  style: TextStyle,
  color: Color,
) {
  Row(
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    digits.forEach { ch ->
      Box(
        modifier = Modifier.width(cellWidthDp).height(fixedHeightDp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = ch.toString(),
          style = style,
          color = color,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun VisualOnlyProgressTrack(
  progressPercent: Float,
  reduceMotion: Boolean,
) {
  val targetFraction = (progressPercent / 100f).coerceIn(0f, 1f)
  val animatedFraction by
    animateFloatAsState(
      targetValue = targetFraction,
      animationSpec =
        if (reduceMotion) snap()
        else tween(durationMillis = PROGRESS_ANIM_DURATION_MS, easing = ProgressAnimEasing),
      label = "visual_track_fraction",
    )

  val fillColor = DipeTheme.colors.readingProgress
  val trackBaseColor = DipeTheme.colors.textPrimary.copy(alpha = 0.12f)
  val density = LocalDensity.current
  val trackHeightDp = 5.dp

  Canvas(modifier = Modifier.fillMaxWidth().height(trackHeightDp)) {
    val trackHeightPx = with(density) { trackHeightDp.toPx() }
    val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

    // Gray/tonal track background
    drawRoundRect(
      color = trackBaseColor,
      topLeft = Offset.Zero,
      size = Size(size.width, trackHeightPx),
      cornerRadius = corner,
    )

    val fillWidth = (size.width * animatedFraction).coerceIn(0f, size.width)
    if (animatedFraction > 0.002f && fillWidth > 0f) {
      drawRoundRect(
        color = fillColor,
        topLeft = Offset.Zero,
        size = Size(fillWidth, trackHeightPx),
        cornerRadius = corner,
      )
    }
  }
}

@Composable
private fun TonalStepperButton(
  label: String,
  isPrimary: Boolean,
  reduceMotion: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && !reduceMotion) 0.96f else 1f,
      animationSpec = if (reduceMotion) snap() else DipeTheme.motion.editorialSpring(),
      label = "stepper_press_scale",
    )

  FilledTonalButton(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(if (isPrimary) 16.dp else 14.dp),
    colors =
      ButtonDefaults.filledTonalButtonColors(
        containerColor =
          if (isPrimary) MaterialTheme.colorScheme.surfaceContainerHighest
          else DipeTheme.colors.surfaceTonal,
        contentColor =
          if (isPrimary) DipeTheme.colors.textPrimary else DipeTheme.colors.textSecondary,
      ),
    contentPadding =
      PaddingValues(
        horizontal = if (isPrimary) DipeSpacing.sm else DipeSpacing.xs,
        vertical = DipeSpacing.sm,
      ),
    modifier =
      modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
      },
  ) {
    Text(
      text = label,
      style =
        if (isPrimary) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        else MaterialTheme.typography.labelMedium,
      maxLines = 1,
    )
  }
}
