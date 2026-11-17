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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.copyMediaTypeLabel
import com.example.core.designsystem.components.editionFormatLabel
import com.example.core.model.EditionWithBookCopies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartReadingJourneySheet(
  isReread: Boolean,
  editions: List<EditionWithBookCopies>,
  preferredEditionId: Long?,
  onDismiss: () -> Unit,
  onStart: (copyId: Long?, note: String?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val allOptions =
    remember(editions) {
      editions.flatMap { group -> group.copies.map { copy -> group.edition to copy } }
    }
  val defaultCopyId =
    remember(allOptions, preferredEditionId) {
      allOptions.firstOrNull { it.first.id == preferredEditionId }?.second?.id
        ?: allOptions.firstOrNull()?.second?.id
    }
  var selectedCopyId by remember(defaultCopyId) { mutableStateOf(defaultCopyId) }
  var noteText by remember { mutableStateOf("") }

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
    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("start_journey_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text =
          stringResource(
            if (isReread) R.string.start_reread_sheet_title else R.string.start_journey_sheet_title
          ),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (allOptions.isNotEmpty()) {
        Text(
          text = stringResource(R.string.start_journey_choose_copy),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
          allOptions.forEach { (edition, copy) ->
            val selected = selectedCopyId == copy.id
            val editionSummary =
              listOfNotNull(
                  edition.publisherOrNote?.takeIf { it.isNotBlank() },
                  edition.publicationYear?.toString(),
                  edition.format?.let { editionFormatLabel(it) },
                )
                .joinToString(" · ")
                .ifEmpty { stringResource(R.string.book_detail_edition_label) }
            val copyTitle =
              copy.label?.takeIf { it.isNotBlank() } ?: copyMediaTypeLabel(copy.mediaType)
            val copyMeta =
              listOfNotNull(
                  copyMediaTypeLabel(copy.mediaType),
                  copy.locationOrPlatform?.takeIf { it.isNotBlank() },
                )
                .joinToString(" · ")

            EditorialSurfaceCard(
              containerColor =
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
              contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
              onClick = { selectedCopyId = copy.id },
              modifier = Modifier.testTag("start_journey_copy_${copy.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
              ) {
                RadioButton(
                  selected = selected,
                  onClick = { selectedCopyId = copy.id },
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
                ) {
                  Text(
                    text = copyTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "$editionSummary · $copyMeta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text(stringResource(R.string.copy_field_note)) },
        maxLines = 2,
        shape = MaterialTheme.shapes.medium,
        colors = textFieldColors,
        modifier = Modifier.fillMaxWidth().testTag("start_journey_note_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("start_journey_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
        Spacer(modifier = Modifier.width(DipeSpacing.sm))
        Button(
          onClick = { onStart(selectedCopyId, noteText.trim().takeIf { it.isNotEmpty() }) },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("start_journey_confirm_button"),
        ) {
          Text(stringResource(R.string.start_journey_action))
        }
      }

      Spacer(modifier = Modifier.height(DipeSpacing.lg))
    }
  }
}
