package com.example.feature.library

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeElevation
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.TopSquircleShape
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ReadingProgressMath
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

  var valueText by remember(initialPage) { mutableStateOf(initialPage.coerceAtLeast(0).toString()) }
  var noteText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showCompleteConfirmation by remember { mutableStateOf(false) }
  var isPageFieldFocused by remember { mutableStateOf(false) }
  var isNoteFieldFocused by remember { mutableStateOf(false) }
  var isScrubbingBar by remember { mutableStateOf(false) }

  val invalidNegativeMsg = stringResource(R.string.progress_validation_page_negative)
  val invalidExceedsMsg =
    totalPages?.let {
      stringResource(
        R.string.progress_validation_page_exceeds,
        LocaleManager.formatInteger(it, language),
      )
    } ?: ""

  val livePreview =
    remember(valueText, totalPages, initialPage, initialPercent) {
      val parsed = valueText.toIntOrNull()
      val pageVal = parsed ?: initialPage
      ReadingProgressMath.calculateFromPage(
        page = pageVal.coerceAtLeast(0),
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
    val current = valueText.toIntOrNull() ?: initialPage
    val updated =
      if (totalPages != null && totalPages > 0) (current + delta).coerceIn(0, totalPages)
      else (current + delta).coerceAtLeast(0)
    valueText = updated.toString()
  }

  val formattedPercent = LocaleManager.formatPercent(livePreview.progressPercent, language)
  val roundedPercent = livePreview.progressPercent.roundToInt().coerceIn(0, 100)
  val formattedPageContext =
    if (totalPages != null) {
      stringResource(
        R.string.journey_page_of_total,
        LocaleManager.formatInteger(livePreview.currentPage, language),
        LocaleManager.formatInteger(totalPages, language),
      )
    } else {
      stringResource(
        R.string.journey_page_only,
        LocaleManager.formatInteger(livePreview.currentPage, language),
      )
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    shape = TopSquircleShape(cornerRadius = 24.dp),
    containerColor = DipeTheme.colors.surfaceElevated,
    tonalElevation = DipeElevation.flat,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
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

      // Borderless tonal Hero Visualizer featuring rolling percentage + live sliding track
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
            RollingOdometerPercent(
              formattedPercent = formattedPercent,
              numericPercent = roundedPercent,
              style = DipeTheme.typography.progressHeroNumber,
              color = DipeTheme.colors.readingProgress,
              reduceMotion = reduceMotion,
            )

            Text(
              text = formattedPageContext,
              style = DipeTheme.typography.monoMetadata,
              color = DipeTheme.colors.textSecondary,
              modifier = Modifier.padding(bottom = DipeSpacing.sm),
            )
          }

          LiveSlidingProgressTrack(
            progressPercent = livePreview.progressPercent,
            currentPage = livePreview.currentPage,
            totalPages = totalPages,
            isScrubbing = isScrubbingBar,
            reduceMotion = reduceMotion,
            onScrubStateChange = { isScrubbingBar = it },
            onScrubPage = { scrubbedPage ->
              errorText = null
              valueText = scrubbedPage.toString()
            },
          )
        }
      }

      // Borderless tonal numeric page entry field
      val fieldShape = RoundedCornerShape(14.dp)
      val pageFieldBorderColor =
        when {
          errorText != null -> DipeTheme.colors.destructive
          isPageFieldFocused -> DipeTheme.colors.readingProgress.copy(alpha = 0.50f)
          else -> Color.Transparent
        }
      TextField(
        value = valueText,
        onValueChange = { input ->
          valueText = input.filter { it.isDigit() }.take(6)
          errorText = null
        },
        label = { Text(stringResource(R.string.progress_field_page_entry)) },
        isError = errorText != null,
        singleLine = true,
        shape = fieldShape,
        colors = borderlessFieldColors,
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .border(width = 1.dp, color = pageFieldBorderColor, shape = fieldShape)
            .onFocusChanged { isPageFieldFocused = it.isFocused }
            .testTag("progress_value_input"),
      )

      // 4 symmetric borderless tonal quick steppers (-10, -1, +1, +10), >= 48x48 dp each
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        val steppers = listOf(-10, -1, 1, 10)
        steppers.forEach { delta ->
          val tagSuffix = if (delta < 0) "minus_${-delta}" else "plus_$delta"
          val label = if (delta > 0) "+$delta" else delta.toString()
          TonalStepperButton(
            label = label,
            reduceMotion = reduceMotion,
            onClick = { applyDelta(delta) },
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .testTag("progress_step_$tagSuffix"),
          )
        }
      }

      // Borderless tonal short note field
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

      // Unified single-row footer (gracefully wraps only under extreme font scaling)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        TextButton(
          onClick = { showCompleteConfirmation = true },
          contentPadding = PaddingValues(horizontal = DipeSpacing.xs, vertical = DipeSpacing.xs),
          modifier =
            Modifier.align(Alignment.CenterVertically)
              .heightIn(min = 48.dp)
              .testTag("progress_complete_button"),
        ) {
          Text(
            text = stringResource(R.string.progress_complete_short),
            style = MaterialTheme.typography.labelLarge,
            color = DipeTheme.colors.textPrimary,
            maxLines = 1,
          )
        }

        Row(
          modifier = Modifier.align(Alignment.CenterVertically),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xs),
            modifier = Modifier.heightIn(min = 48.dp).testTag("progress_cancel_button"),
          ) {
            Text(
              text = stringResource(R.string.book_cancel_action),
              style = MaterialTheme.typography.labelLarge,
              color = DipeTheme.colors.textSecondary,
              maxLines = 1,
            )
          }

          Button(
            onClick = {
              val cleanNote = noteText.trim().takeIf { it.isNotEmpty() }
              val parsedPage = valueText.trim().toIntOrNull()
              when {
                parsedPage == null || parsedPage < 0 -> errorText = invalidNegativeMsg
                totalPages != null && parsedPage > totalPages -> errorText = invalidExceedsMsg
                else -> onSavePage(parsedPage, cleanNote)
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
            modifier = Modifier.heightIn(min = 48.dp).testTag("progress_save_button"),
          ) {
            Text(
              text = stringResource(R.string.book_save_action),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.xs))
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
private fun RollingOdometerPercent(
  formattedPercent: String,
  numericPercent: Int,
  style: TextStyle,
  color: Color,
  reduceMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  var previousNumeric by remember { mutableIntStateOf(numericPercent) }
  val isIncreasing = numericPercent >= previousNumeric
  SideEffect { previousNumeric = numericPercent }

  if (reduceMotion) {
    Text(
      text = formattedPercent,
      style = style,
      color = color,
      maxLines = 1,
      modifier = modifier.testTag("progress_hero_percent"),
    )
    return
  }

  val digitsPart = formattedPercent.takeWhile { it.isDigit() }
  val suffixPart = formattedPercent.drop(digitsPart.length)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier.clearAndSetSemantics {
        testTag = "progress_hero_percent"
        contentDescription = formattedPercent
      },
  ) {
    digitsPart.forEachIndexed { index, digitChar ->
      val placeSlot = digitsPart.length - index
      androidx.compose.runtime.key(placeSlot) {
        AnimatedContent(
          targetState = digitChar,
          transitionSpec = {
            val enter =
              slideInVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
              ) { height -> if (isIncreasing) height / 2 else -height / 2 } +
                fadeIn(animationSpec = tween(durationMillis = 180))
            val exit =
              slideOutVertically(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
              ) { height -> if (isIncreasing) -height / 2 else height / 2 } +
                fadeOut(animationSpec = tween(durationMillis = 150))
            enter togetherWith exit
          },
          label = "odometer_digit_$placeSlot",
        ) { animatedDigit ->
          Text(
            text = animatedDigit.toString(),
            style = style,
            color = color,
            maxLines = 1,
          )
        }
      }
    }
    if (suffixPart.isNotEmpty()) {
      Text(
        text = suffixPart,
        style = style,
        color = color,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun LiveSlidingProgressTrack(
  progressPercent: Float,
  currentPage: Int,
  totalPages: Int?,
  isScrubbing: Boolean,
  reduceMotion: Boolean,
  onScrubStateChange: (Boolean) -> Unit,
  onScrubPage: (Int) -> Unit,
) {
  val targetFraction = (progressPercent / 100f).coerceIn(0f, 1f)
  val animatedFraction by
    animateFloatAsState(
      targetValue = targetFraction,
      animationSpec =
        if (reduceMotion || isScrubbing) snap()
        else DipeTheme.motion.editorialSpring(),
      label = "live_track_fraction",
    )

  val sheenProgress = remember { Animatable(0f) }
  val pulseGlow = remember { Animatable(0f) }
  var lastAnimatedPage by remember { mutableIntStateOf(currentPage) }
  LaunchedEffect(currentPage, reduceMotion) {
    if (!reduceMotion && currentPage != lastAnimatedPage) {
      lastAnimatedPage = currentPage
      sheenProgress.snapTo(0f)
      pulseGlow.snapTo(1f)
      sheenProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
      )
      pulseGlow.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
      )
    } else {
      lastAnimatedPage = currentPage
    }
  }

  val thumbPulse = if (reduceMotion) 0f else pulseGlow.value

  val thumbScale by
    animateFloatAsState(
      targetValue = if (isScrubbing) 1.24f else 1f,
      animationSpec = if (reduceMotion) snap() else DipeTheme.motion.editorialSpring(),
      label = "thumb_scrub_scale",
    )

  val fillColor = DipeTheme.colors.readingProgress
  val trackBaseColor = DipeTheme.colors.textPrimary.copy(alpha = 0.12f)
  val sheenTint = DipeTheme.colors.surfaceElevated.copy(alpha = 0.55f)
  val thumbCoreColor = DipeTheme.colors.surfaceElevated
  val density = LocalDensity.current
  val thumbRadiusDp = 6.5.dp
  val trackHeightDp = 5.dp

  BoxWithConstraints(
    modifier = Modifier.fillMaxWidth().height(26.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    val trackWidthPx = with(density) { maxWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
    val usableSpanPx = (trackWidthPx - thumbRadiusPx * 2f).coerceAtLeast(1f)

    val pointerModifier =
      if (totalPages != null && totalPages > 0) {
        Modifier.pointerInput(totalPages, trackWidthPx) {
          detectTapGestures { offset ->
            val fraction = ((offset.x - thumbRadiusPx) / usableSpanPx).coerceIn(0f, 1f)
            onScrubPage((fraction * totalPages).roundToInt().coerceIn(0, totalPages))
          }
        }.pointerInput(totalPages, trackWidthPx) {
          detectHorizontalDragGestures(
            onDragStart = { offset ->
              onScrubStateChange(true)
              val fraction = ((offset.x - thumbRadiusPx) / usableSpanPx).coerceIn(0f, 1f)
              onScrubPage((fraction * totalPages).roundToInt().coerceIn(0, totalPages))
            },
            onDragEnd = { onScrubStateChange(false) },
            onDragCancel = { onScrubStateChange(false) },
            onHorizontalDrag = { change, _ ->
              change.consume()
              val fraction = ((change.position.x - thumbRadiusPx) / usableSpanPx).coerceIn(0f, 1f)
              onScrubPage((fraction * totalPages).roundToInt().coerceIn(0, totalPages))
            },
          )
        }
      } else {
        Modifier
      }

    Canvas(modifier = Modifier.fillMaxSize().then(pointerModifier)) {
      val trackHeightPx = with(density) { trackHeightDp.toPx() }
      val centerY = size.height / 2f
      val trackTop = centerY - trackHeightPx / 2f
      val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

      // Background track
      drawRoundRect(
        color = trackBaseColor,
        topLeft = Offset(0f, trackTop),
        size = Size(size.width, trackHeightPx),
        cornerRadius = corner,
      )

      val indicatorX = thumbRadiusPx + usableSpanPx * animatedFraction
      val fillWidth = (indicatorX).coerceIn(0f, size.width)

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

      // Sliding indicator halo + terracotta cap + crisp inner dot
      val activeRadius = thumbRadiusPx * thumbScale
      val haloRadius = activeRadius * (1.45f + 0.30f * thumbPulse)
      drawCircle(
        color = fillColor.copy(alpha = if (isScrubbing) 0.24f else 0.10f + 0.08f * thumbPulse),
        radius = haloRadius,
        center = Offset(indicatorX, centerY),
      )
      drawCircle(
        color = fillColor,
        radius = activeRadius,
        center = Offset(indicatorX, centerY),
      )
      drawCircle(
        color = thumbCoreColor,
        radius = activeRadius * 0.36f,
        center = Offset(indicatorX, centerY),
      )
    }
  }
}

@Composable
private fun TonalStepperButton(
  label: String,
  reduceMotion: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && !reduceMotion) 0.95f else 1f,
      animationSpec = if (reduceMotion) snap() else DipeTheme.motion.editorialSpring(),
      label = "stepper_press_scale",
    )

  FilledTonalButton(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(14.dp),
    colors =
      ButtonDefaults.filledTonalButtonColors(
        containerColor = DipeTheme.colors.surfaceTonal,
        contentColor = DipeTheme.colors.textPrimary,
      ),
    contentPadding = PaddingValues(horizontal = DipeSpacing.xs, vertical = DipeSpacing.sm),
    modifier =
      modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
      },
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
    )
  }
}
