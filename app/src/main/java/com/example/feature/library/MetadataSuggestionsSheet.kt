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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.suggestionFieldLabel
import com.example.core.designsystem.components.suggestionReasonLabel
import com.example.core.model.MetadataSuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSuggestionsSheet(
  suggestions: List<MetadataSuggestion>,
  onAccept: (Long) -> Unit,
  onReject: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .padding(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm)
          .testTag("metadata_suggestions_sheet"),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      Text(
        text = stringResource(R.string.suggestions_sheet_title),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )

      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        contentPadding = PaddingValues(bottom = DipeSpacing.lg),
      ) {
        items(items = suggestions, key = { it.id }) { suggestion ->
          EditorialSurfaceCard(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentPadding = PaddingValues(DipeSpacing.md),
            modifier = Modifier.testTag("suggestion_card_${suggestion.id}"),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = suggestionFieldLabel(suggestion.fieldName),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                  Text(
                    text = stringResource(R.string.suggestions_source, suggestion.sourceName),
                    style = LocalDipeTypography.current.monoMetadata,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }

              Text(
                text = suggestionReasonLabel(suggestion.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )

              Text(
                text =
                  "${stringResource(R.string.suggestions_current_label)}: ${
                    suggestion.currentValue?.takeIf { it.isNotBlank() }
                      ?: stringResource(R.string.suggestions_empty)
                  }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )

              Text(
                text = "${stringResource(R.string.suggestions_found_label)}: ${suggestion.suggestedValue}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
              )

              Spacer(modifier = Modifier.height(DipeSpacing.xs))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
              ) {
                OutlinedButton(
                  onClick = { onReject(suggestion.id) },
                  shape = CircleShape,
                  modifier = Modifier.testTag("suggestion_reject_${suggestion.id}"),
                ) {
                  Text(stringResource(R.string.suggestions_reject))
                }
                Spacer(modifier = Modifier.width(DipeSpacing.sm))
                Button(
                  onClick = { onAccept(suggestion.id) },
                  shape = CircleShape,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.secondary,
                      contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                  modifier = Modifier.testTag("suggestion_accept_${suggestion.id}"),
                ) {
                  Text(stringResource(R.string.suggestions_accept))
                }
              }
            }
          }
        }
      }
    }
  }
}
