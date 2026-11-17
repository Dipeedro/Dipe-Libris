package com.example.feature.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeBrandTokens
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
import com.example.core.model.CatalogBook

@Composable
fun HomeScreen(
  uiState: HomeUiState,
  widthClass: WindowWidthClass,
  onOpenSettings: () -> Unit,
  onOpenLibrary: () -> Unit,
  onCatalogVolume: () -> Unit,
  onWriteDiaryEntry: () -> Unit,
  onOpenBook: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val brand = LocalDipeBrandTokens.current

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 880.dp).fillMaxSize().testTag("home_lazy_column"),
      contentPadding =
        PaddingValues(
          horizontal = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xl),
    ) {
      // Editorial Masthead (Monochrome brand identity independent of reading theme)
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.home_editorial_kicker),
                style = LocalDipeTypography.current.editorialKicker,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(DipeSpacing.xs))
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                color = brand.badgeContainer,
                contentColor = brand.badgeContent,
                shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, brand.badgeBorder),
                modifier = Modifier.testTag("home_brand_badge"),
              ) {
                Text(
                  text = "DL",
                  style = LocalDipeTypography.current.editorialKicker,
                  modifier = Modifier.padding(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xs),
                )
              }
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("home_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.cd_open_settings),
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(DipeSpacing.xs))
          Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(DipeSpacing.md))
          HairlineDivider()
        }
      }

      // Reading overview (calm factual counts, zero goals or gamification)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.home_section_reading_notes))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
          ) {
            OverviewFactColumn(
              label =
                pluralStringResource(
                  R.plurals.home_overview_works_label,
                  if (uiState.snapshot.totalWorks == 1) 1 else 2,
                ),
              value = LocaleManager.formatInteger(uiState.snapshot.totalWorks, language),
              modifier = Modifier.weight(1f),
            )
            OverviewFactColumn(
              label =
                pluralStringResource(
                  R.plurals.home_overview_reading_label,
                  if (uiState.snapshot.readingCount == 1) 1 else 2,
                ),
              value = LocaleManager.formatInteger(uiState.snapshot.readingCount, language),
              modifier = Modifier.weight(1f),
            )
            OverviewFactColumn(
              label =
                pluralStringResource(
                  R.plurals.home_overview_diary_label,
                  if (uiState.snapshot.diaryEntriesCount == 1) 1 else 2,
                ),
              value = LocaleManager.formatInteger(uiState.snapshot.diaryEntriesCount, language),
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Currently reading
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(
            title = stringResource(R.string.home_section_currently_reading),
            actionLabel = stringResource(R.string.home_shortcut_open_library),
            actionTag = "home_open_library_header_action",
            onAction = onOpenLibrary,
          )
          if (uiState.currentlyReading.isEmpty()) {
            EditorialEmptyState(
              icon = Icons.Outlined.AutoStories,
              title = stringResource(R.string.home_empty_reading_title),
              body = stringResource(R.string.home_empty_reading_body),
              actionLabel = stringResource(R.string.home_shortcut_add_volume),
              actionTestTag = "home_empty_catalog_button",
              onAction = onCatalogVolume,
            )
          } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
              items(uiState.currentlyReading, key = { it.id }) { book ->
                CurrentlyReadingPlate(book = book, onClick = { onOpenBook(book.id) })
              }
            }
          }
        }
      }

      // Shortcuts to Library & Diary
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.home_section_shortcuts))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          ) {
            OutlinedButton(
              onClick = onOpenLibrary,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier.weight(1f).minimumInteractiveComponentSize().testTag("home_shortcut_library"),
            ) {
              Icon(
                imageVector = Icons.Outlined.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(
                text = stringResource(R.string.home_shortcut_open_library),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            OutlinedButton(
              onClick = onWriteDiaryEntry,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier.weight(1f).minimumInteractiveComponentSize().testTag("home_shortcut_diary"),
            ) {
              Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(
                text = stringResource(R.string.home_shortcut_write_entry),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // Recent activity
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.home_section_recent_activity))
          if (uiState.recentActivity.isEmpty()) {
            EditorialEmptyState(
              icon = Icons.Outlined.EditNote,
              title = stringResource(R.string.home_empty_activity_title),
              body = stringResource(R.string.home_empty_activity_body),
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
              uiState.recentActivity.forEach { item ->
                when (item) {
                  is RecentActivityItem.BookCataloged -> {
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .defaultMinSize(minHeight = 48.dp)
                          .clickable { onOpenBook(item.book.id) }
                          .padding(vertical = DipeSpacing.sm),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = item.book.title,
                          style = MaterialTheme.typography.titleMedium,
                          color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                          text = "${item.book.author} · ${readingStatusLabel(item.book.status)}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = LocaleManager.formatShortDate(item.timestampMillis, language),
                        style = LocalDipeTypography.current.monoMetadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    HairlineDivider()
                  }
                  is RecentActivityItem.DiaryRecorded -> {
                    val linkedWorkId = item.entry.workId
                    Column(
                      modifier =
                        Modifier.fillMaxWidth()
                          .defaultMinSize(minHeight = 48.dp)
                          .let { base ->
                            if (linkedWorkId != null) {
                              base
                                .clickable { onOpenBook(linkedWorkId) }
                                .testTag("home_diary_entry_${item.entry.id}")
                            } else {
                              base.testTag("home_diary_entry_${item.entry.id}")
                            }
                          }
                          .padding(vertical = DipeSpacing.sm),
                      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text =
                            item.entry.linkedBookTitle
                              ?: stringResource(R.string.diary_general_note_tag),
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                          text = LocaleManager.formatShortDate(item.timestampMillis, language),
                          style = LocalDipeTypography.current.monoMetadata,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = item.entry.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    HairlineDivider()
                  }
                }
              }
            }
          }
        }
      }

      // Featured shelves & Calendar foundations (clearly prepared for Phase 2+)
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.home_section_featured_shelves))
          EditorialPlannedBlock(
            title = stringResource(R.string.home_empty_shelves_title),
            description = stringResource(R.string.home_empty_shelves_body),
            icon = Icons.Outlined.BookmarkBorder,
          )
        }
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.home_section_reading_calendar))
          EditorialPlannedBlock(
            title = stringResource(R.string.home_calendar_planned_title),
            description = stringResource(R.string.home_calendar_planned_body),
            icon = Icons.Outlined.CalendarMonth,
          )
        }
      }
    }
  }
}

@Composable
private fun OverviewFactColumn(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun CurrentlyReadingPlate(
  book: CatalogBook,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.width(136.dp)
        .clickable(onClick = onClick)
        .testTag("home_reading_book_${book.id}"),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = book.author,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
