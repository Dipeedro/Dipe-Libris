package com.example.feature.library

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val PROGRESS_ANIM_DURATION_MS = 220
private val ProgressAnimEasing = FastOutSlowInEasing

@OptIn(ExperimentalMaterial3Api::class)
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
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val language = LocalAppLanguage.current
  val reduceMotion = DipeTheme.motion.reduceMotion

  var currentPage by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
  var noteText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showCompleteConfirmation by remember { mutableStateOf(false) }
  var isNoteFieldFocused by remember { mutableStateOf(false) }

  val invalidNegativeMsg = stringResource(R.string.progress_validation_page_negative)
  val invalidExceedsMsg =
    totalPages?.let {
      stringResource(
        R.string.progress_validation_page_exceeds,
        LocaleManager.formatInteger(it, language),
      )
    } ?: ""

  val livePreview =
    remember(currentPage, totalPages, initialPercent) {
      ReadingProgressMath.calculateFromPage(
        page = currentPage.coerceAtLeast(0),
        totalPages = totalPages,
        fallbackPercent = initialPercent,
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
      if (totalPages != null && totalPages > 0) (currentPage + delta).coerceIn(0, totalPages)
      else (currentPage + delta).coerceAtLeast(0)
  }

  val roundedPercent = livePreview.progressPercent.roundToInt().coerceIn(0, 100)
  val formattedPercent = LocaleManager.formatPercent(livePreview.progressPercent, language)
  val pageUnitLabel = stringResource(R.string.progress_page_unit)
  val formattedTotalInline =
    totalPages?.let {
      stringResource(
        R.string.progress_of_total_inline,
        LocaleManager.formatInteger(it, language),
      )
    }
  val maxPageDigits =
    remember(totalPages, livePreview.currentPage, language) {
      val maxDisplay =
        totalPages?.let { LocaleManager.formatInteger(it, language) }
          ?: LocaleManager.formatInteger(livePreview.currentPage.coerceAtLeast(100), language)
      maxDisplay.length.coerceAtLeast(3)
    }

  val sheetBackgroundColor = DipeTheme.colors.surfaceElevated
  val isLightSheetBackground = sheetBackgroundColor.luminance() > 0.5f

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    shape = TopSquircleShape(cornerRadius = 24.dp),
    containerColor = sheetBackgroundColor,
    tonalElevation = DipeElevation.flat,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
  ) {
    // Extend sheet background seamlessly behind Android's system navigation bar without gray scrim
    val dialogView = LocalView.current
    if (!dialogView.isInEditMode) {
      SideEffect {
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
          WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dialogWindow.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
          } else {
            @Suppress("DEPRECATION")
            dialogWindow.navigationBarColor = sheetBackgroundColor.toArgb()
          }
          WindowCompat.getInsetsController(dialogWindow, dialogView)
            .isAppearanceLightNavigationBars = isLightSheetBackground
        }
      }
    }

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
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

      // Borderless tonal Hero Visualizer featuring synchronized unified numbers + non-interactive progress track
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DipeTheme.colors.surfaceTonal,
        tonalElevation = DipeElevation.flat,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.md),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
          ) {
            Row(
              verticalAlignment = Alignment.Bottom,
              modifier =
                Modifier.clearAndSetSemantics {
                  testTag = "progress_hero_percent"
                  contentDescription = formattedPercent
                },
            ) {
              UnifiedTabularNumber(
                value = roundedPercent,
                formattedValue = LocaleManager.formatInteger(roundedPercent, language),
                maxChars = 3,
                style = DipeTheme.typography.progressHeroNumber,
                color = DipeTheme.colors.readingProgress,
                reduceMotion = reduceMotion,
              )
              Text(
                text = "%",
                style = DipeTheme.typography.progressHeroNumber,
                color = DipeTheme.colors.readingProgress,
                maxLines = 1,
                softWrap = false,
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.padding(bottom = DipeSpacing.sm)
                  .clearAndSetSemantics {
                    testTag = "progress_hero_page"
                    contentDescription =
                      if (formattedTotalInline != null) {
                        "$pageUnitLabel ${LocaleManager.formatInteger(livePreview.currentPage, language)} $formattedTotalInline"
                      } else {
                        "$pageUnitLabel ${LocaleManager.formatInteger(livePreview.currentPage, language)}"
                      }
                  },
            ) {
              Text(
                text = "$pageUnitLabel ",
                style = DipeTheme.typography.monoMetadata,
                color = DipeTheme.colors.textSecondary,
                maxLines = 1,
                softWrap = false,
              )
              UnifiedTabularNumber(
                value = livePreview.currentPage,
                formattedValue = LocaleManager.formatInteger(livePreview.currentPage, language),
                maxChars = maxPageDigits,
                style = DipeTheme.typography.monoMetadata,
                color = DipeTheme.colors.textSecondary,
                reduceMotion = reduceMotion,
              )
              if (formattedTotalInline != null) {
                Text(
                  text = " $formattedTotalInline",
                  style = DipeTheme.typography.monoMetadata,
                  color = DipeTheme.colors.textSecondary,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }

          VisualOnlyProgressTrack(
            progressPercent = livePreview.progressPercent,
            currentPage = livePreview.currentPage,
            reduceMotion = reduceMotion,
          )
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
            when {
              currentPage < 0 -> errorText = invalidNegativeMsg
              totalPages != null && currentPage > totalPages -> errorText = invalidExceedsMsg
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

      Spacer(modifier = Modifier.height(DipeSpacing.sm))
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
private fun UnifiedTabularNumber(
  value: Int,
  formattedValue: String,
  maxChars: Int,
  style: TextStyle,
  color: Color,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val tabularStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }

  val slotMetrics =
    remember(tabularStyle, textMeasurer) {
      var maxWidthPx = 0
      var maxHeightPx = 0
      for (digit in 0..9) {
        val result = textMeasurer.measure(text = digit.toString(), style = tabularStyle)
        if (result.size.width > maxWidthPx) maxWidthPx = result.size.width
        if (result.size.height > maxHeightPx) maxHeightPx = result.size.height
      }
      val paddedWidthPx = ceil(maxWidthPx * 1.04f).toInt().coerceAtLeast(1)
      paddedWidthPx to maxHeightPx.coerceAtLeast(1)
    }

  val slotWidthDp = with(density) { slotMetrics.first.toDp() }
  val slotHeightDp = with(density) { slotMetrics.second.toDp() }
  val reservedCount = maxOf(maxChars, formattedValue.length)
  val totalWidthDp = slotWidthDp * reservedCount

  var previousValue by remember { mutableIntStateOf(value) }
  val isIncreasing = value >= previousValue
  SideEffect { previousValue = value }

  Box(
    modifier = modifier.width(totalWidthDp).height(slotHeightDp).clipToBounds(),
    contentAlignment = Alignment.CenterEnd,
  ) {
    if (reduceMotion) {
      FixedDigitColumns(
        formattedValue = formattedValue,
        slotWidthDp = slotWidthDp,
        slotHeightDp = slotHeightDp,
        style = tabularStyle,
        color = color,
      )
    } else {
      AnimatedContent(
        targetState = value to formattedValue,
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
        label = "unified_tabular_number",
      ) { (_, targetFormatted) ->
        FixedDigitColumns(
          formattedValue = targetFormatted,
          slotWidthDp = slotWidthDp,
          slotHeightDp = slotHeightDp,
          style = tabularStyle,
          color = color,
        )
      }
    }
  }
}

@Composable
private fun FixedDigitColumns(
  formattedValue: String,
  slotWidthDp: androidx.compose.ui.unit.Dp,
  slotHeightDp: androidx.compose.ui.unit.Dp,
  style: TextStyle,
  color: Color,
) {
  Row(
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    formattedValue.forEach { ch ->
      Box(
        modifier = Modifier.width(slotWidthDp).height(slotHeightDp),
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
  currentPage: Int,
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

  val sheenProgress = remember { Animatable(0f) }
  var lastAnimatedPage by remember { mutableIntStateOf(currentPage) }
  LaunchedEffect(currentPage, reduceMotion) {
    if (!reduceMotion && currentPage != lastAnimatedPage) {
      lastAnimatedPage = currentPage
      sheenProgress.snapTo(0f)
      sheenProgress.animateTo(
        targetValue = 1f,
        animationSpec =
          tween(durationMillis = PROGRESS_ANIM_DURATION_MS * 2, easing = ProgressAnimEasing),
      )
    } else {
      lastAnimatedPage = currentPage
    }
  }

  val fillColor = DipeTheme.colors.readingProgress
  val trackBaseColor = DipeTheme.colors.textPrimary.copy(alpha = 0.12f)
  val sheenTint = DipeTheme.colors.surfaceElevated.copy(alpha = 0.50f)
  val markerCoreColor = DipeTheme.colors.surfaceElevated
  val density = LocalDensity.current
  val markerRadiusDp = 5.5.dp
  val trackHeightDp = 5.dp

  Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
    val trackHeightPx = with(density) { trackHeightDp.toPx() }
    val markerRadiusPx = with(density) { markerRadiusDp.toPx() }
    val centerY = size.height / 2f
    val trackTop = centerY - trackHeightPx / 2f
    val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
    val usableSpanPx = (size.width - markerRadiusPx * 2f).coerceAtLeast(1f)

    // Unfilled background track
    drawRoundRect(
      color = trackBaseColor,
      topLeft = Offset(0f, trackTop),
      size = Size(size.width, trackHeightPx),
      cornerRadius = corner,
    )

    val markerX = markerRadiusPx + usableSpanPx * animatedFraction
    val fillWidth = markerX.coerceIn(0f, size.width)

    if (animatedFraction > 0.002f && fillWidth > 0f) {
      drawRoundRect(
        color = fillColor,
        topLeft = Offset(0f, trackTop),
        size = Size(fillWidth, trackHeightPx),
        cornerRadius = corner,
      )

      val sweep = sheenProgress.value
      if (!reduceMotion && sweep > 0f && sweep < 1f) {
        val glintWidth = (fillWidth * 0.42f).coerceAtLeast(trackHeightPx * 3f)
        val glintStart = -glintWidth + (fillWidth + glintWidth * 1.4f) * sweep
        drawRoundRect(
          brush =
            Brush.horizontalGradient(
              colors = listOf(Color.Transparent, sheenTint, Color.Transparent),
              startX = glintStart,
              endX = glintStart + glintWidth,
            ),
          topLeft = Offset(0f, trackTop),
          size = Size(fillWidth, trackHeightPx),
          cornerRadius = corner,
        )
      }
    }

    // Fixed visual endpoint marker on progress fill tip (no touch/drag interaction)
    drawCircle(
      color = fillColor,
      radius = markerRadiusPx,
      center = Offset(markerX, centerY),
    )
    drawCircle(
      color = markerCoreColor,
      radius = markerRadiusPx * 0.38f,
      center = Offset(markerX, centerY),
    )
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
