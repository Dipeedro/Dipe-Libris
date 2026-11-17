package com.example.feature.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
import com.example.core.model.DiaryEntry

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
  val listState = rememberLazyListState()
  var pendingDeleteEntryId by remember { mutableStateOf<Long?>(null) }
  val horizontalPadding =
    if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
  val entriesCount = uiState.entries.size

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("diary_lazy_column"),
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    ) {
      item(key = "diary_toolbar", contentType = "toolbar") {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DipeSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (entriesCount > 0) {
                  pluralStringResource(
                    R.plurals.diary_entries_count,
                    if (entriesCount == 1) 1 else 2,
                    entriesCount,
                  ).uppercase()
                } else {
                  ""
                },
              style = LocalDipeTypography.current.editorialKicker,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = onOpenNewEntry,
              shape = CircleShape,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
              contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
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
          }
          HairlineDivider()
        }
      }

      if (uiState.entries.isEmpty()) {
        item(key = "diary_empty", contentType = "empty_state") {
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
        items(
          items = uiState.entries,
          key = { it.id },
          contentType = { "diary_entry" },
        ) { entry ->
          val onRequestDelete = remember(entry.id) { { pendingDeleteEntryId = entry.id } }
          DiaryEntryRow(
            entry = entry,
            language = language,
            onOpenBook = onOpenBook,
            onRequestDelete = onRequestDelete,
          )
          HairlineDivider()
        }
      }
    }
  }

  pendingDeleteEntryId?.let { targetId ->
    AlertDialog(
      onDismissRequest = { pendingDeleteEntryId = null },
      shape = MaterialTheme.shapes.medium,
      containerColor = MaterialTheme.colorScheme.surface,
      title = {
        Text(
          text = stringResource(R.string.diary_delete_confirm_title),
          style = MaterialTheme.typography.headlineSmall,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.diary_delete_confirm_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
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

@Composable
private fun DiaryEntryRow(
  entry: DiaryEntry,
  language: AppLanguage,
  onOpenBook: (Long) -> Unit,
  onRequestDelete: () -> Unit,
) {
  val formattedDate =
    remember(entry.createdAtEpochMillis, language) {
      LocaleManager.formatShortDate(entry.createdAtEpochMillis, language)
    }

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
          text = formattedDate,
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val linkedTitle = entry.linkedBookTitle
        val workId = entry.workId
        if (linkedTitle != null && workId != null) {
          val onBookClick = remember(workId, onOpenBook) { { onOpenBook(workId) } }
          Row(
            modifier =
              Modifier.defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onBookClick)
                .testTag("diary_entry_book_${entry.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          ) {
            Icon(
              imageVector = Icons.Outlined.AutoStories,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text =
                if (entry.linkedBookAuthor != null) "$linkedTitle — ${entry.linkedBookAuthor}"
                else linkedTitle,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
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
        onClick = onRequestDelete,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("diary_delete_${entry.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = stringResource(R.string.diary_delete_entry),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Box(
        modifier =
          Modifier.width(2.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
      )
      Text(
        text = entry.content,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
