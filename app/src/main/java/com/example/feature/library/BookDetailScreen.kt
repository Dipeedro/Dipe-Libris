package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTypographyScope
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialMetadataStrip
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.EditorialSubscreenTopBar
import com.example.core.designsystem.components.EditorialSurfaceCard
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
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
  var showDeleteConfirmation by remember { mutableStateOf(false) }

  when (uiState) {
    BookDetailUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier.widthIn(max = 820.dp)
              .fillMaxSize()
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
        ) {
          EditorialSubscreenTopBar(
            title = null,
            backButtonTag = "book_detail_back_button",
            onNavigateBack = onNavigateBack,
            floatingButtonChrome = true,
          )
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
              .padding(horizontal = horizontalPadding, vertical = DipeSpacing.md),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
        ) {
          EditorialSubscreenTopBar(
            title = null,
            backButtonTag = "book_detail_back_button",
            onNavigateBack = onNavigateBack,
            floatingButtonChrome = true,
          )
          EditorialEmptyState(
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
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
      val pagesDisplay =
        currentBook.totalPages?.let { LocaleManager.formatInteger(it, language) }
          ?: stringResource(R.string.book_detail_unspecified)
      val yearDisplay =
        currentBook.publicationYear?.toString() ?: stringResource(R.string.book_detail_unspecified)
      val editionDisplay =
        currentBook.editionNote?.takeIf { it.isNotBlank() }
          ?: stringResource(R.string.book_detail_unspecified)

      val chipColors =
        FilterChipDefaults.filterChipColors(
          selectedContainerColor = MaterialTheme.colorScheme.primary,
          selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

      DipeTypographyScope(presetOverride = currentBook.typographyPresetOverride) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          LazyColumn(
            modifier =
              Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("book_detail_lazy_column"),
            contentPadding = PaddingValues(bottom = DipeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
          ) {
            // Soft-tinted full-bleed showcase stage
            item(key = "detail_hero", contentType = "hero") {
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
              ) {
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(
                        horizontal = horizontalPadding,
                        vertical = DipeSpacing.md,
                      ),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  EditorialSubscreenTopBar(
                    title = null,
                    backButtonTag = "book_detail_back_button",
                    onNavigateBack = onNavigateBack,
                    floatingButtonChrome = true,
                    trailingContent = {
                      Surface(
                        onClick = { onToggleFavorite(currentBook.isFavorite) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                        modifier =
                          Modifier.size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("book_detail_favorite_button"),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector =
                              if (currentBook.isFavorite) Icons.Filled.Bookmark
                              else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint =
                              if (currentBook.isFavorite) MaterialTheme.colorScheme.secondary
                              else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                          )
                        }
                      }
                    },
                  )

                  Spacer(modifier = Modifier.height(DipeSpacing.md))

                  BookCoverArtwork(
                    title = currentBook.title,
                    author = currentBook.author,
                    isFavorite = currentBook.isFavorite,
                    elevated = true,
                    modifier =
                      Modifier.width(if (widthClass == WindowWidthClass.COMPACT) 168.dp else 196.dp),
                  )

                  Spacer(modifier = Modifier.height(DipeSpacing.lg))

                  Text(
                    text = currentBook.title,
                    style = LocalDipeTypography.current.editorialMasthead,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(modifier = Modifier.height(DipeSpacing.xs))
                  Text(
                    text = currentBook.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                  )

                  Spacer(modifier = Modifier.height(DipeSpacing.lg))

                  EditorialMetadataStrip(
                    items =
                      listOf(
                        stringResource(R.string.book_detail_pages_label) to pagesDisplay,
                        stringResource(R.string.book_detail_year_label) to yearDisplay,
                        stringResource(R.string.book_detail_edition_label) to editionDisplay,
                      ),
                    modifier = Modifier.widthIn(max = 540.dp),
                  )

                  Spacer(modifier = Modifier.height(DipeSpacing.md))
                }
              }
            }

            item(key = "detail_status", contentType = "status_selector") {
              Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                EditorialSectionHeader(title = stringResource(R.string.book_detail_status_label))
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                ) {
                  ReadingStatus.entries.forEach { status ->
                    val selected = currentBook.status == status
                    FilterChip(
                      selected = selected,
                      onClick = { onUpdateStatus(status) },
                      label = { Text(readingStatusLabel(status)) },
                      shape = CircleShape,
                      colors = chipColors,
                      border = null,
                      modifier = Modifier.testTag("book_detail_status_${status.name.lowercase()}"),
                    )
                  }
                }
              }
            }

            item(key = "detail_notes_header", contentType = "section_header") {
              EditorialSectionHeader(
                title = stringResource(R.string.book_detail_notes_section),
                modifier = Modifier.padding(horizontal = horizontalPadding),
              )
            }

            if (diaryNotes.isEmpty()) {
              item(key = "detail_no_notes", contentType = "empty_notes") {
                EditorialSurfaceCard(
                  modifier = Modifier.padding(horizontal = horizontalPadding),
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
                  contentPadding = PaddingValues(DipeSpacing.lg),
                ) {
                  Text(
                    text = stringResource(R.string.book_detail_no_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              items(
                items = diaryNotes,
                key = { it.id },
                contentType = { "detail_note" },
              ) { note ->
                val formattedDate =
                  remember(note.createdAtEpochMillis, language) {
                    LocaleManager.formatShortDate(note.createdAtEpochMillis, language)
                  }
                EditorialSurfaceCard(
                  modifier = Modifier.padding(horizontal = horizontalPadding),
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                  shape = MaterialTheme.shapes.medium,
                  contentPadding = PaddingValues(DipeSpacing.md),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
                  ) {
                    Box(
                      modifier =
                        Modifier.width(3.dp)
                          .fillMaxHeight()
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    )
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                    ) {
                      Text(
                        text = formattedDate,
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                  }
                }
              }
            }

            item(key = "detail_delete", contentType = "delete_action") {
              Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                Spacer(modifier = Modifier.height(DipeSpacing.sm))
                OutlinedButton(
                  onClick = { showDeleteConfirmation = true },
                  enabled = !uiState.isDeleting,
                  shape = CircleShape,
                  border =
                    BorderStroke(
                      0.75.dp,
                      MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                    ),
                  contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.sm),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("book_detail_delete_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(DipeSpacing.sm))
                  Text(
                    text = stringResource(R.string.book_detail_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }

      if (showDeleteConfirmation) {
        AlertDialog(
          onDismissRequest = { showDeleteConfirmation = false },
          shape = MaterialTheme.shapes.large,
          containerColor = MaterialTheme.colorScheme.surface,
          title = {
            Text(
              text = stringResource(R.string.book_delete_confirm_title),
              style = MaterialTheme.typography.headlineSmall,
            )
          },
          text = {
            Text(
              text = stringResource(R.string.book_delete_confirm_message),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
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
