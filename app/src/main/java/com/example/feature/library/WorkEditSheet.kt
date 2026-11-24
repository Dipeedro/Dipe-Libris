package com.example.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.core.designsystem.components.EditorialModalBottomSheet
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

@Composable
fun WorkEditSheet(
  initialTitle: String,
  initialAuthor: String,
  onDismiss: () -> Unit,
  onSave: (title: String, author: String) -> Unit,
  initialOriginalTitle: String? = null,
  onSaveWithOriginalTitle: ((title: String, author: String, originalTitle: String?) -> Unit)? = null,
) {
  var title by remember(initialTitle) { mutableStateOf(initialTitle) }
  var author by remember(initialAuthor) { mutableStateOf(initialAuthor) }
  var originalTitle by remember(initialOriginalTitle) { mutableStateOf(initialOriginalTitle.orEmpty()) }
  var showError by remember { mutableStateOf(false) }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      focusedBorderColor = MaterialTheme.colorScheme.secondary,
    )

  EditorialModalBottomSheet(
    onDismissRequest = onDismiss,
    dismissOnBackPress = false,
  ) { requestDismiss ->
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("work_edit_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.work_edit_sheet_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      OutlinedTextField(
        value = title,
        onValueChange = {
          title = it
          showError = false
        },
        label = { Text(stringResource(R.string.book_field_title)) },
        isError = showError && title.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("work_edit_title_input"),
      )

      OutlinedTextField(
        value = author,
        onValueChange = {
          author = it
          showError = false
        },
        label = { Text(stringResource(R.string.book_field_author)) },
        isError = showError && author.isBlank(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("work_edit_author_input"),
      )

      if (onSaveWithOriginalTitle != null) {
        OutlinedTextField(
          value = originalTitle,
          onValueChange = { originalTitle = it },
          label = { Text(stringResource(R.string.review_field_original_title)) },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          colors = textFieldColors,
          modifier = Modifier.fillMaxWidth().testTag("work_edit_original_title_input"),
        )
      }

      if (showError) {
        Text(
          text = stringResource(R.string.book_validation_required),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("work_edit_validation_error"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = requestDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("work_edit_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Spacer(modifier = Modifier.width(DipeSpacing.sm))
        Button(
          onClick = {
            if (title.isBlank() || author.isBlank()) {
              showError = true
            } else {
              val trimmedTitle = title.trim()
              val trimmedAuthor = author.trim()
              val trimmedOriginal = originalTitle.trim().takeIf { it.isNotEmpty() }
              if (onSaveWithOriginalTitle != null) {
                onSaveWithOriginalTitle(trimmedTitle, trimmedAuthor, trimmedOriginal)
              } else {
                onSave(trimmedTitle, trimmedAuthor)
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
          modifier = Modifier.minimumInteractiveComponentSize().testTag("work_edit_save_button"),
        ) {
          Text(stringResource(R.string.book_save_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
