package com.example.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.diary_sheet_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      OutlinedTextField(
        value = content,
        onValueChange = { content = it },
        label = { Text(stringResource(R.string.diary_field_content)) },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth().testTag("diary_entry_content_input"),
      )

      if (availableBooks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
          Text(
            text = stringResource(R.string.diary_field_book_optional),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            item {
              FilterChip(
                selected = selectedWorkId == null,
                onClick = { selectedWorkId = null },
                label = { Text(stringResource(R.string.diary_no_book_linked)) },
              )
            }
            items(availableBooks, key = { it.id }) { book ->
              FilterChip(
                selected = selectedWorkId == book.id,
                onClick = { selectedWorkId = book.id },
                label = { Text(book.title) },
              )
            }
          }
        }
      }

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
        Button(
          onClick = {
            if (content.isNotBlank()) {
              onSave(content.trim(), selectedWorkId)
            }
          },
          enabled = content.isNotBlank(),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("diary_save_button"),
        ) {
          Text(stringResource(R.string.diary_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
