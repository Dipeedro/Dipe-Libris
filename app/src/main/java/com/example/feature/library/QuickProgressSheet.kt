package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeElevation
import com.example.core.designsystem.DipeRadii
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ReadingProgressMath
import kotlin.math.roundToInt

enum class ProgressInputMode {
  PAGE,
  PERCENT,
}

@OptIn(ExperimentalMaterial3Api::class)
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
  var showCompleteConfirmation by remember { mutableStateOf(false) }

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
      focusedContainerColor = DipeTheme.colors.surfaceElevated,
      unfocusedContainerColor = DipeTheme.colors.surfaceTonal,
      unfocusedBorderColor = DipeTheme.colors.controlBorder,
      focusedBorderColor = DipeTheme.colors.inkActionContainer,
      focusedLabelColor = DipeTheme.colors.textPrimary,
      unfocusedLabelColor = DipeTheme.colors.textSecondary,
      cursorColor = DipeTheme.colors.inkActionContainer,
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
    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    shape = RoundedCornerShape(topStart = DipeRadii.surface, topEnd = DipeRadii.surface),
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

      // Single-datum Hero Readout + Terracotta progress bar
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = DipeTheme.colors.surfaceTonal,
        border = BorderStroke(0.5.dp, DipeTheme.colors.dividerHairline),
        tonalElevation = DipeElevation.flat,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(DipeSpacing.md),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
          ) {
            if (mode == ProgressInputMode.PAGE) {
              Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              ) {
                Text(
                  text = LocaleManager.formatInteger(livePreview.currentPage, language),
                  style = DipeTheme.typography.progressHeroNumber,
                  color = DipeTheme.colors.textPrimary,
                )
                val contextSuffix =
                  if (totalPages != null) {
                    stringResource(
                      R.string.progress_of_total_inline,
                      LocaleManager.formatInteger(totalPages, language),
                    )
                  } else {
                    stringResource(R.string.progress_page_unit)
                  }
                Text(
                  text = contextSuffix,
                  style = MaterialTheme.typography.titleMedium,
                  color = DipeTheme.colors.textSecondary,
                  modifier = Modifier.padding(bottom = DipeSpacing.sm),
                )
              }
              Text(
                text = LocaleManager.formatPercent(livePreview.progressPercent, language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DipeTheme.colors.readingProgress,
                modifier = Modifier.padding(bottom = DipeSpacing.sm),
              )
            } else {
              Text(
                text = LocaleManager.formatPercent(livePreview.progressPercent, language),
                style = DipeTheme.typography.progressHeroNumber,
                color = DipeTheme.colors.readingProgress,
              )
              if (totalPages != null) {
                Text(
                  text =
                    stringResource(
                      R.string.journey_page_of_total,
                      LocaleManager.formatInteger(livePreview.currentPage, language),
                      LocaleManager.formatInteger(totalPages, language),
                    ),
                  style = DipeTheme.typography.monoMetadata,
                  color = DipeTheme.colors.textSecondary,
                  modifier = Modifier.padding(bottom = DipeSpacing.sm),
                )
              }
            }
          }
          EditorialProgressBar(
            progressPercent = livePreview.progressPercent,
            height = 4.dp,
          )
        }
      }

      // Unified Ink segmented mode control (Page | Percent)
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = DipeTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, DipeTheme.colors.controlBorder),
        tonalElevation = DipeElevation.flat,
      ) {
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
          val pageSelected = mode == ProgressInputMode.PAGE
          val percentSelected = mode == ProgressInputMode.PERCENT

          Box(
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                  if (pageSelected) DipeTheme.colors.inkActionContainer else Color.Transparent
                )
                .selectable(
                  selected = pageSelected,
                  onClick = {
                    if (!pageSelected) {
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
                  role = Role.Tab,
                )
                .padding(horizontal = DipeSpacing.compact)
                .testTag("progress_mode_page"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.progress_mode_page),
              style = MaterialTheme.typography.labelLarge,
              color =
                if (pageSelected) DipeTheme.colors.inkActionContent
                else DipeTheme.colors.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Box(
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                  if (percentSelected) DipeTheme.colors.inkActionContainer else Color.Transparent
                )
                .selectable(
                  selected = percentSelected,
                  onClick = {
                    if (!percentSelected) {
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
                  role = Role.Tab,
                )
                .padding(horizontal = DipeSpacing.compact)
                .testTag("progress_mode_percent"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.progress_mode_percent),
              style = MaterialTheme.typography.labelLarge,
              color =
                if (percentSelected) DipeTheme.colors.inkActionContent
                else DipeTheme.colors.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Numeric entry field
      OutlinedTextField(
        value = valueText,
        onValueChange = { input ->
          valueText =
            input.filter { it.isDigit() || (mode == ProgressInputMode.PERCENT && it == '.') }.take(6)
          errorText = null
        },
        label = {
          Text(
            stringResource(
              if (mode == ProgressInputMode.PAGE) R.string.progress_field_page_entry
              else R.string.progress_field_percent_entry
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
              if (mode == ProgressInputMode.PAGE) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
          ),
        modifier = Modifier.fillMaxWidth().testTag("progress_value_input"),
      )

      // 4 symmetric quick steppers (-10, -1, +1, +10), >= 48x48 dp each
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        val steppers = listOf(-10, -1, 1, 10)
        steppers.forEach { delta ->
          val tagSuffix = if (delta < 0) "minus_${-delta}" else "plus_$delta"
          val label = if (delta > 0) "+$delta" else delta.toString()
          OutlinedButton(
            onClick = { applyDelta(delta) },
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, DipeTheme.colors.controlBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DipeTheme.colors.textPrimary),
            contentPadding = PaddingValues(horizontal = DipeSpacing.xs, vertical = DipeSpacing.sm),
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .testTag("progress_step_$tagSuffix"),
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }
      }

      // Consistent short note field
      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text(stringResource(R.string.progress_field_note)) },
        maxLines = 2,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("progress_note_input"),
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

      // Separated secondary completion trigger & primary Save / Cancel footer
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        TextButton(
          onClick = { showCompleteConfirmation = true },
          contentPadding = PaddingValues(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xs),
          modifier =
            Modifier.align(Alignment.Start)
              .heightIn(min = 48.dp)
              .testTag("progress_complete_button"),
        ) {
          Text(
            text = stringResource(R.string.progress_complete_short),
            style = MaterialTheme.typography.labelLarge,
            color = DipeTheme.colors.textPrimary,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp).testTag("progress_cancel_button"),
          ) {
            Text(
              text = stringResource(R.string.book_cancel_action),
              style = MaterialTheme.typography.labelLarge,
              color = DipeTheme.colors.textSecondary,
            )
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
            shape = MaterialTheme.shapes.medium,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = DipeTheme.colors.inkActionContainer,
                contentColor = DipeTheme.colors.inkActionContent,
              ),
            contentPadding =
              PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
            modifier = Modifier.heightIn(min = 48.dp).testTag("progress_save_button"),
          ) {
            Text(
              text = stringResource(R.string.book_save_action),
              style = MaterialTheme.typography.labelLarge,
            )
          }
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
          shape = MaterialTheme.shapes.medium,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = DipeTheme.colors.inkActionContainer,
              contentColor = DipeTheme.colors.inkActionContent,
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
