package com.example.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.model.CatalogBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDiaryEntrySheet(
  availableBooks: List<CatalogBook>,
  onDismiss: () -> Unit,
  onSave: (content: String, workId: Long?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var content by remember { mutableStateOf("") }
  var selectedWorkId by remember { mutableStateOf<Long?>(null) }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      focusedBorderColor = MaterialTheme.colorScheme.secondary,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.diary_sheet_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      OutlinedTextField(
        value = content,
        onValueChange = { content = it },
        label = { Text(stringResource(R.string.diary_field_content)) },
        minLines = 4,
        maxLines = 8,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("diary_entry_content_input"),
      )

      if (availableBooks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
          Text(
            text = stringResource(R.string.diary_field_book_optional),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val chipColors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
            item(key = "no_book_linked", contentType = "book_chip") {
              FilterChip(
                selected = selectedWorkId == null,
                onClick = { selectedWorkId = null },
                shape = CircleShape,
                colors = chipColors,
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedWorkId == null,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    borderWidth = 0.5.dp,
                  ),
                label = { Text(stringResource(R.string.diary_no_book_linked)) },
              )
            }
            items(
              items = availableBooks,
              key = { it.id },
              contentType = { "book_chip" },
            ) { book ->
              val selected = selectedWorkId == book.id
              FilterChip(
                selected = selected,
                onClick = { selectedWorkId = book.id },
                shape = CircleShape,
                colors = chipColors,
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    borderWidth = 0.5.dp,
                  ),
                label = { Text(book.title) },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.xs))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("diary_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Spacer(modifier = Modifier.width(DipeSpacing.sm))
        Button(
          onClick = {
            if (content.isNotBlank()) {
              onSave(content.trim(), selectedWorkId)
            }
          },
          enabled = content.isNotBlank(),
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("diary_save_button"),
        ) {
          Text(stringResource(R.string.diary_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
