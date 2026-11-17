package com.example.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.model.ReadingStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBookSheet(
  onDismiss: () -> Unit,
  onSave: (title: String, author: String, pages: Int?, year: Int?, editionNote: String?, status: ReadingStatus) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var title by remember { mutableStateOf("") }
  var author by remember { mutableStateOf("") }
  var pagesText by remember { mutableStateOf("") }
  var yearText by remember { mutableStateOf("") }
  var editionNote by remember { mutableStateOf("") }
  var selectedStatus by remember { mutableStateOf(ReadingStatus.UNREAD) }
  var validationErrorRes by remember { mutableStateOf<Int?>(null) }
  var titleAuthorError by remember { mutableStateOf(false) }
  var pagesError by remember { mutableStateOf(false) }
  var yearError by remember { mutableStateOf(false) }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      focusedBorderColor = MaterialTheme.colorScheme.primary,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        Text(
          text = stringResource(R.string.book_add_sheet_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        HairlineDivider()
      }

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          titleAuthorError = false
          validationErrorRes = null
        },
        label = { Text(stringResource(R.string.book_field_title)) },
        isError = titleAuthorError && title.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_title_input"),
      )

      OutlinedTextField(
        value = author,
        onValueChange = {
          author = it
          titleAuthorError = false
          validationErrorRes = null
        },
        label = { Text(stringResource(R.string.book_field_author)) },
        isError = titleAuthorError && author.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_author_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        OutlinedTextField(
          value = pagesText,
          onValueChange = { input ->
            pagesText = input.filter { it.isDigit() }.take(6)
            pagesError = false
            validationErrorRes = null
          },
          label = { Text(stringResource(R.string.book_field_pages)) },
          isError = pagesError,
          singleLine = true,
          shape = MaterialTheme.shapes.small,
          colors = textFieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f).testTag("add_book_pages_input"),
        )
        OutlinedTextField(
          value = yearText,
          onValueChange = { input ->
            yearText = input.filter { it.isDigit() }.take(4)
            yearError = false
            validationErrorRes = null
          },
          label = { Text(stringResource(R.string.book_field_year)) },
          isError = yearError,
          singleLine = true,
          shape = MaterialTheme.shapes.small,
          colors = textFieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f).testTag("add_book_year_input"),
        )
      }

      OutlinedTextField(
        value = editionNote,
        onValueChange = { editionNote = it },
        label = { Text(stringResource(R.string.book_field_edition_note)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("add_book_edition_input"),
      )

      Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
        Text(
          text = stringResource(R.string.book_field_status),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          val chipColors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              containerColor = MaterialTheme.colorScheme.surface,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          ReadingStatus.entries.forEach { status ->
            val selected = selectedStatus == status
            FilterChip(
              selected = selected,
              onClick = { selectedStatus = status },
              label = { Text(readingStatusLabel(status)) },
              shape = CircleShape,
              colors = chipColors,
              border =
                FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = selected,
                  borderColor = MaterialTheme.colorScheme.outlineVariant,
                  borderWidth = 0.75.dp,
                ),
              modifier = Modifier.testTag("add_book_status_${status.name.lowercase()}"),
            )
          }
        }
      }

      validationErrorRes?.let { errorRes ->
        Text(
          text = stringResource(errorRes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("add_book_validation_error"),
        )
      }

      HairlineDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_book_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Button(
          onClick = {
            val parsedPages =
              if (pagesText.isBlank()) null else pagesText.toIntOrNull()?.takeIf { it in 1..50_000 }
            val parsedYear =
              if (yearText.isBlank()) null else yearText.toIntOrNull()?.takeIf { it in 1..2100 }

            when {
              title.isBlank() || author.isBlank() -> {
                titleAuthorError = true
                validationErrorRes = R.string.book_validation_required
              }
              pagesText.isNotBlank() && parsedPages == null -> {
                pagesError = true
                validationErrorRes = R.string.book_validation_pages
              }
              yearText.isNotBlank() && parsedYear == null -> {
                yearError = true
                validationErrorRes = R.string.book_validation_year
              }
              else -> {
                onSave(
                  title.trim(),
                  author.trim(),
                  parsedPages,
                  parsedYear,
                  editionNote.trim().takeIf { it.isNotEmpty() },
                  selectedStatus,
                )
              }
            }
          },
          shape = CircleShape,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_book_save_button"),
        ) {
          Text(stringResource(R.string.book_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
