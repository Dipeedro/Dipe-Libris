package com.example.feature.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialPageHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager

@Composable
fun DiaryScreen(
  uiState: DiaryUiState,
  widthClass: WindowWidthClass,
  onOpenNewEntry: () -> Unit,
  onDeleteEntry: (Long) -> Unit,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  var pendingDeleteEntryId by remember { mutableStateOf<Long?>(null) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize(),
      contentPadding =
        PaddingValues(
          horizontal = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      item {
        EditorialPageHeader(
          kicker = stringResource(R.string.home_editorial_kicker),
          title = stringResource(R.string.diary_title),
          subtitle = stringResource(R.string.diary_subtitle),
          trailingContent = {
            OutlinedButton(
              onClick = onOpenNewEntry,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("diary_new_entry_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.diary_new_entry))
            }
          },
        )
      }

      if (uiState.entries.isEmpty()) {
        item {
          EditorialEmptyState(
            icon = Icons.Outlined.EditNote,
            title = stringResource(R.string.diary_empty_title),
            body = stringResource(R.string.diary_empty_body),
            actionLabel = stringResource(R.string.diary_new_entry),
            actionTestTag = "diary_empty_new_button",
            onAction = onOpenNewEntry,
          )
        }
      } else {
        items(uiState.entries, key = { it.id }) { entry ->
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .padding(vertical = DipeSpacing.sm)
                .testTag("diary_entry_${entry.id}"),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = LocaleManager.formatShortDate(entry.createdAtEpochMillis, language),
                  style = LocalDipeTypography.current.monoMetadata,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val linkedTitle = entry.linkedBookTitle
                val workId = entry.workId
                if (linkedTitle != null && workId != null) {
                  Box(
                    modifier =
                      Modifier.defaultMinSize(minHeight = 48.dp)
                        .clickable { onOpenBook(workId) }
                        .testTag("diary_entry_book_${entry.id}"),
                    contentAlignment = Alignment.CenterStart,
                  ) {
                    Text(
                      text =
                        if (entry.linkedBookAuthor != null) "$linkedTitle — ${entry.linkedBookAuthor}"
                        else linkedTitle,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                } else {
                  Text(
                    text = stringResource(R.string.diary_general_note_tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              IconButton(
                onClick = { pendingDeleteEntryId = entry.id },
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("diary_delete_${entry.id}"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = stringResource(R.string.diary_delete_entry),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Text(
              text = entry.content,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          HairlineDivider()
        }
      }
    }
  }

  pendingDeleteEntryId?.let { targetId ->
    AlertDialog(
      onDismissRequest = { pendingDeleteEntryId = null },
      title = { Text(stringResource(R.string.diary_delete_confirm_title)) },
      text = { Text(stringResource(R.string.diary_delete_confirm_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteEntry(targetId)
            pendingDeleteEntryId = null
          },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("diary_delete_confirm_button"),
        ) {
          Text(
            text = stringResource(R.string.diary_delete_confirm_action),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingDeleteEntryId = null },
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("diary_delete_cancel_button"),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
      },
      modifier = Modifier.testTag("diary_delete_confirm_dialog"),
    )
  }
}
