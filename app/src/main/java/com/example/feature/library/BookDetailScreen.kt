package com.example.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.example.core.designsystem.DipeTypographyScope
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialPlannedBlock
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.ReadingStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
  uiState: BookDetailUiState,
  widthClass: WindowWidthClass,
  onToggleFavorite: (Boolean) -> Unit,
  onUpdateStatus: (ReadingStatus) -> Unit,
  onConfirmDelete: () -> Unit,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val horizontalPadding = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
  var showDeleteConfirmation by remember { mutableStateOf(false) }

  when (uiState) {
    BookDetailUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.lg),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("book_detail_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
            Spacer(modifier = Modifier.width(DipeSpacing.xs))
            Text(
              text = stringResource(R.string.book_detail_kicker),
              style = LocalDipeTypography.current.editorialKicker,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HairlineDivider()
          Box(
            modifier = Modifier.fillMaxSize().testTag("book_detail_loading"),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
            ) {
              CircularProgressIndicator(strokeWidth = 2.dp)
              Text(
                text = stringResource(R.string.book_detail_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    BookDetailUiState.NotFound -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.lg),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("book_detail_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
              )
            }
            Spacer(modifier = Modifier.width(DipeSpacing.xs))
            Text(
              text = stringResource(R.string.book_detail_kicker),
              style = LocalDipeTypography.current.editorialKicker,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HairlineDivider()
          EditorialEmptyState(
            icon = Icons.Outlined.LibraryBooks,
            title = stringResource(R.string.book_detail_not_found_title),
            body = stringResource(R.string.book_detail_not_found_body),
            actionLabel = stringResource(R.string.book_detail_not_found_action),
            actionTestTag = "book_detail_not_found_back_button",
            onAction = onNavigateBack,
            modifier = Modifier.testTag("book_detail_not_found"),
          )
        }
      }
    }

    is BookDetailUiState.Loaded -> {
      val currentBook = uiState.book
      val diaryNotes = uiState.diaryNotes

      DipeTypographyScope(presetOverride = currentBook.typographyPresetOverride) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          LazyColumn(
            modifier =
              Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("book_detail_lazy_column"),
            contentPadding =
              PaddingValues(
                horizontal = horizontalPadding,
                vertical = DipeSpacing.lg,
              ),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
          ) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IconButton(
                  onClick = onNavigateBack,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("book_detail_back_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                  )
                }
                Text(
                  text = stringResource(R.string.book_detail_kicker),
                  style = LocalDipeTypography.current.editorialKicker,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                  onClick = { onToggleFavorite(currentBook.isFavorite) },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("book_detail_favorite_button"),
                ) {
                  Icon(
                    imageVector =
                      if (currentBook.isFavorite) Icons.Filled.Bookmark
                      else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(R.string.cd_toggle_favorite),
                    tint =
                      if (currentBook.isFavorite) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
              HairlineDivider()
            }

            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
                verticalAlignment = Alignment.Top,
              ) {
                BookCoverArtwork(
                  title = currentBook.title,
                  author = currentBook.author,
                  isFavorite = currentBook.isFavorite,
                  modifier =
                    Modifier.width(if (widthClass == WindowWidthClass.COMPACT) 120.dp else 156.dp),
                )
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  Text(
                    text = currentBook.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                  Text(
                    text = currentBook.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.height(DipeSpacing.xs))
                  Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.lg)) {
                    Column {
                      Text(
                        text = stringResource(R.string.book_detail_pages_label),
                        style = LocalDipeTypography.current.editorialKicker,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text =
                          currentBook.totalPages?.let { LocaleManager.formatInteger(it, language) }
                            ?: stringResource(R.string.book_detail_unspecified),
                        style = LocalDipeTypography.current.monoMetadata,
                      )
                    }
                    Column {
                      Text(
                        text = stringResource(R.string.book_detail_year_label),
                        style = LocalDipeTypography.current.editorialKicker,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text =
                          currentBook.publicationYear?.toString()
                            ?: stringResource(R.string.book_detail_unspecified),
                        style = LocalDipeTypography.current.monoMetadata,
                      )
                    }
                  }
                  if (!currentBook.editionNote.isNullOrBlank()) {
                    Text(
                      text = currentBook.editionNote ?: "",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            item {
              Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
                EditorialSectionHeader(title = stringResource(R.string.book_detail_status_label))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  ReadingStatus.entries.forEach { status ->
                    FilterChip(
                      selected = currentBook.status == status,
                      onClick = { onUpdateStatus(status) },
                      label = { Text(readingStatusLabel(status)) },
                      modifier = Modifier.testTag("book_detail_status_${status.name.lowercase()}"),
                    )
                  }
                }
              }
            }

            item {
              Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
                EditorialSectionHeader(title = stringResource(R.string.book_detail_edition_section))
                EditorialPlannedBlock(
                  title = stringResource(R.string.settings_section_typography),
                  description = stringResource(R.string.book_detail_typography_note),
                  icon = Icons.Outlined.TextFields,
                )
              }
            }

            item {
              EditorialSectionHeader(title = stringResource(R.string.book_detail_notes_section))
            }

            if (diaryNotes.isEmpty()) {
              item {
                Text(
                  text = stringResource(R.string.book_detail_no_notes),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              items(diaryNotes, key = { it.id }) { note ->
                Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = DipeSpacing.xs),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  Text(
                    text = LocaleManager.formatShortDate(note.createdAtEpochMillis, language),
                    style = LocalDipeTypography.current.monoMetadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                HairlineDivider()
              }
            }

            item {
              Spacer(modifier = Modifier.height(DipeSpacing.sm))
              OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !uiState.isDeleting,
                shape = MaterialTheme.shapes.small,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("book_detail_delete_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
                Text(
                  text = stringResource(R.string.book_detail_delete),
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }

      if (showDeleteConfirmation) {
        AlertDialog(
          onDismissRequest = { showDeleteConfirmation = false },
          title = { Text(stringResource(R.string.book_delete_confirm_title)) },
          text = { Text(stringResource(R.string.book_delete_confirm_message)) },
          confirmButton = {
            TextButton(
              onClick = {
                showDeleteConfirmation = false
                onConfirmDelete()
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("book_delete_confirm_button"),
            ) {
              Text(
                text = stringResource(R.string.book_delete_confirm_action),
                color = MaterialTheme.colorScheme.error,
              )
            }
          },
          dismissButton = {
            TextButton(
              onClick = { showDeleteConfirmation = false },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("book_delete_cancel_button"),
            ) {
              Text(stringResource(R.string.book_cancel_action))
            }
          },
          modifier = Modifier.testTag("book_delete_confirm_dialog"),
        )
      }
    }
  }
}
