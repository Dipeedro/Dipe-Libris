package com.example.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTypographyScope
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
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
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val listState = rememberLazyListState()
  var pendingDeleteEntryId by remember { mutableStateOf<Long?>(null) }
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)

  when (uiState) {
    DiaryUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
        ) {
          EditorialSubscreenTopBar(
            title = stringResource(R.string.nav_diary),
            backButtonTag = "diary_back_button",
            onNavigateBack = onNavigateBack,
          )
          Box(
            modifier = Modifier.fillMaxSize().testTag("diary_loading"),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
          }
        }
      }
    }

    DiaryUiState.NotFound -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
        ) {
          EditorialSubscreenTopBar(
            title = stringResource(R.string.nav_diary),
            backButtonTag = "diary_back_button",
            onNavigateBack = onNavigateBack,
          )
          EditorialEmptyState(
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
            title = stringResource(R.string.book_detail_not_found_title),
            body = stringResource(R.string.book_detail_not_found_body),
            actionLabel = stringResource(R.string.book_detail_not_found_action),
            actionTestTag = "diary_not_found_back_button",
            onAction = onNavigateBack,
            modifier = Modifier.testTag("diary_not_found"),
          )
        }
      }
    }

    is DiaryUiState.Loaded -> {
      val book = uiState.book
      val entries = uiState.entries
      val entriesCount = entries.size
      val countLabel =
        pluralStringResource(
          R.plurals.diary_entries_count,
          if (entriesCount == 1) 1 else 2,
          entriesCount,
        )

      DipeTypographyScope(presetOverride = book.typographyPresetOverride) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("diary_lazy_column"),
            contentPadding =
              PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = DipeSpacing.md,
                bottom = DipeSpacing.xxl,
              ),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
          ) {
            item(key = "diary_toolbar", contentType = "toolbar") {
              EditorialSubscreenTopBar(
                title = stringResource(R.string.nav_diary),
                backButtonTag = "diary_back_button",
                onNavigateBack = onNavigateBack,
                trailingContent = {
                  Button(
                    onClick = onOpenNewEntry,
                    shape = CircleShape,
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                      ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier =
                      Modifier.minimumInteractiveComponentSize().testTag("diary_new_entry_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Add,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(DipeSpacing.xs))
                    Text(
                      text = stringResource(R.string.diary_new_entry),
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                    )
                  }
                },
              )
            }

            item(key = "diary_book_context", contentType = "book_context") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = DipeSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              ) {
                Text(
                  text = book.title,
                  style = LocalDipeTypography.current.editorialMasthead,
                  color = MaterialTheme.colorScheme.onBackground,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${book.author} · $countLabel",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            if (entries.isEmpty()) {
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
                items = entries,
                key = { it.id },
                contentType = { "diary_entry" },
              ) { entry ->
                val onRequestDelete = remember(entry.id) { { pendingDeleteEntryId = entry.id } }
                DiaryEntryRow(
                  entry = entry,
                  language = language,
                  onRequestDelete = onRequestDelete,
                )
              }
            }
          }
        }
      }
    }
  }

  pendingDeleteEntryId?.let { targetId ->
    AlertDialog(
      onDismissRequest = { pendingDeleteEntryId = null },
      shape = MaterialTheme.shapes.large,
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
  onRequestDelete: () -> Unit,
) {
  val formattedDate =
    remember(entry.createdAtEpochMillis, language) {
      LocaleManager.formatShortDate(entry.createdAtEpochMillis, language)
    }

  EditorialSurfaceCard(
    modifier = Modifier.testTag("diary_entry_${entry.id}"),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(DipeSpacing.lg),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = formattedDate,
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        Box(
          modifier =
            Modifier.width(3.dp)
              .fillMaxHeight()
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
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
}
