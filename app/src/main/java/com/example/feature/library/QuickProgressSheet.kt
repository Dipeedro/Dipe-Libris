package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ReadingProgressMath
import kotlin.math.roundToInt

enum class ProgressInputMode {
  PAGE,
  PERCENT,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickProgressSheet(
  bookTitle: String,
  initialPage: Int,
  totalPages: Int?,
  initialPercent: Float,
  onDismiss: () -> Unit,
  onSavePage: (page: Int, note: String?) -> Unit,
  onSavePercent: (percent: Float, note: String?) -> Unit,
  onMarkFinished: (note: String?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val language = LocalAppLanguage.current

  var mode by remember(totalPages, initialPage, initialPercent) {
    mutableStateOf(
      if (totalPages != null || initialPage > 0) ProgressInputMode.PAGE
      else ProgressInputMode.PERCENT
    )
  }
  var valueText by remember(mode, initialPage, initialPercent) {
    mutableStateOf(
      if (mode == ProgressInputMode.PAGE) initialPage.toString()
      else initialPercent.roundToInt().toString()
    )
  }
  var noteText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val invalidNegativeMsg = stringResource(R.string.progress_validation_page_negative)
  val invalidPercentMsg = stringResource(R.string.progress_validation_percent)
  val invalidExceedsMsg =
    totalPages?.let {
      stringResource(
        R.string.progress_validation_page_exceeds,
        LocaleManager.formatInteger(it, language),
      )
    } ?: ""

  val livePreview =
    remember(mode, valueText, totalPages, initialPage, initialPercent) {
      val parsed = valueText.toFloatOrNull()
      when (mode) {
        ProgressInputMode.PAGE -> {
          val pageVal = parsed?.roundToInt() ?: initialPage
          ReadingProgressMath.calculateFromPage(
            page = pageVal.coerceAtLeast(0),
            totalPages = totalPages,
            fallbackPercent = initialPercent,
          )
        }
        ProgressInputMode.PERCENT -> {
          val pctVal = parsed ?: initialPercent
          ReadingProgressMath.calculateFromPercent(
            percent = pctVal.coerceIn(0f, 100f),
            totalPages = totalPages,
            fallbackPage = initialPage,
          )
        }
      }
    }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      focusedBorderColor = MaterialTheme.colorScheme.secondary,
    )

  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  val applyDelta: (Int) -> Unit = { delta ->
    errorText = null
    when (mode) {
      ProgressInputMode.PAGE -> {
        val current = valueText.toIntOrNull() ?: initialPage
        val updated =
          if (totalPages != null && totalPages > 0) (current + delta).coerceIn(0, totalPages)
          else (current + delta).coerceAtLeast(0)
        valueText = updated.toString()
      }
      ProgressInputMode.PERCENT -> {
        val current = valueText.toFloatOrNull()?.roundToInt() ?: initialPercent.roundToInt()
        val updated = (current + delta).coerceIn(0, 100)
        valueText = updated.toString()
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("quick_progress_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
        Text(
          text = stringResource(R.string.progress_sheet_title),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = bookTitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Segmented Page / Percent toggle
      Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        FilterChip(
          selected = mode == ProgressInputMode.PAGE,
          onClick = {
            if (mode != ProgressInputMode.PAGE) {
              val currentPct = valueText.toFloatOrNull() ?: livePreview.progressPercent
              val converted =
                ReadingProgressMath.calculateFromPercent(
                  percent = currentPct.coerceIn(0f, 100f),
                  totalPages = totalPages,
                  fallbackPage = initialPage,
                )
              mode = ProgressInputMode.PAGE
              valueText = converted.currentPage.toString()
              errorText = null
            }
          },
          label = { Text(stringResource(R.string.progress_mode_page)) },
          shape = CircleShape,
          colors = chipColors,
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = mode == ProgressInputMode.PAGE,
              borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
              borderWidth = 0.5.dp,
            ),
          modifier = Modifier.testTag("progress_mode_page"),
        )
        FilterChip(
          selected = mode == ProgressInputMode.PERCENT,
          onClick = {
            if (mode != ProgressInputMode.PERCENT) {
              val currentPg = valueText.toIntOrNull() ?: livePreview.currentPage
              val converted =
                ReadingProgressMath.calculateFromPage(
                  page = currentPg.coerceAtLeast(0),
                  totalPages = totalPages,
                  fallbackPercent = initialPercent,
                )
              mode = ProgressInputMode.PERCENT
              valueText = converted.progressPercent.roundToInt().toString()
              errorText = null
            }
          },
          label = { Text(stringResource(R.string.progress_mode_percent)) },
          shape = CircleShape,
          colors = chipColors,
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = mode == ProgressInputMode.PERCENT,
              borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
              borderWidth = 0.5.dp,
            ),
          modifier = Modifier.testTag("progress_mode_percent"),
        )
      }

      // Synchronized readout + progress bar
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val pageSummary =
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
          Text(
            text = pageSummary,
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = LocaleManager.formatPercent(livePreview.progressPercent, language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        EditorialProgressBar(
          progressPercent = livePreview.progressPercent,
          height = 4.dp,
        )
      }

      OutlinedTextField(
        value = valueText,
        onValueChange = { input ->
          valueText = input.filter { it.isDigit() || (mode == ProgressInputMode.PERCENT && it == '.') }.take(6)
          errorText = null
        },
        label = {
          Text(
            stringResource(
              if (mode == ProgressInputMode.PAGE) R.string.progress_field_page
              else R.string.progress_field_percent
            )
          )
        },
        isError = errorText != null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        keyboardOptions =
          KeyboardOptions(
            keyboardType =
              if (mode == ProgressInputMode.PAGE) KeyboardType.Number
              else KeyboardType.Decimal
          ),
        modifier = Modifier.fillMaxWidth().testTag("progress_value_input"),
      )

      // Quick steppers
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        val steppers = listOf(-10, -1, 1, 5, 10)
        steppers.forEach { delta ->
          val tagSuffix = if (delta < 0) "minus_${-delta}" else "plus_$delta"
          val label = if (delta > 0) "+$delta" else delta.toString()
          OutlinedButton(
            onClick = { applyDelta(delta) },
            shape = CircleShape,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("progress_step_$tagSuffix"),
          ) {
            Text(text = label, style = LocalDipeTypography.current.monoMetadata)
          }
        }
      }

      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text(stringResource(R.string.progress_field_note)) },
        maxLines = 2,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("progress_note_input"),
      )

      errorText?.let { message ->
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("progress_validation_error"),
        )
      }

      OutlinedButton(
        onClick = { onMarkFinished(noteText.trim().takeIf { it.isNotEmpty() }) },
        shape = CircleShape,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("progress_complete_button"),
      ) {
        Text(
          text = stringResource(R.string.progress_complete_action),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.secondary,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("progress_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Spacer(modifier = Modifier.width(DipeSpacing.sm))
        Button(
          onClick = {
            val cleanNote = noteText.trim().takeIf { it.isNotEmpty() }
            when (mode) {
              ProgressInputMode.PAGE -> {
                val parsedPage = valueText.trim().toIntOrNull()
                when {
                  parsedPage == null || parsedPage < 0 -> errorText = invalidNegativeMsg
                  totalPages != null && parsedPage > totalPages -> errorText = invalidExceedsMsg
                  else -> onSavePage(parsedPage, cleanNote)
                }
              }
              ProgressInputMode.PERCENT -> {
                val parsedPct = valueText.trim().toFloatOrNull()
                if (parsedPct == null || parsedPct < 0f || parsedPct > 100f) {
                  errorText = invalidPercentMsg
                } else {
                  onSavePercent(parsedPct, cleanNote)
                }
              }
            }
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("progress_save_button"),
        ) {
          Text(stringResource(R.string.book_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
