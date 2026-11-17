package com.example.feature.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
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
  val listState = rememberLazyListState()
  val horizontalPadding =
    if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
  val featuredReading = uiState.currentlyReading.firstOrNull()
  val secondaryReading =
    remember(uiState.currentlyReading) {
      if (uiState.currentlyReading.size > 1) uiState.currentlyReading.drop(1) else emptyList()
    }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.widthIn(max = 880.dp).fillMaxSize().testTag("home_lazy_column"),
      contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      // Discreet editorial summary bar + settings action
      item(key = "home_overview_header", contentType = "overview_header") {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OverviewFactInline(
                value = LocaleManager.formatInteger(uiState.snapshot.totalWorks, language),
                label =
                  pluralStringResource(
                    R.plurals.home_overview_works_label,
                    if (uiState.snapshot.totalWorks == 1) 1 else 2,
                  ),
                modifier = Modifier.weight(1f),
              )
              OverviewFactInline(
                value = LocaleManager.formatInteger(uiState.snapshot.readingCount, language),
                label =
                  pluralStringResource(
                    R.plurals.home_overview_reading_label,
                    if (uiState.snapshot.readingCount == 1) 1 else 2,
                  ),
                modifier = Modifier.weight(1f),
              )
              OverviewFactInline(
                value = LocaleManager.formatInteger(uiState.snapshot.diaryEntriesCount, language),
                label =
                  pluralStringResource(
                    R.plurals.home_overview_diary_label,
                    if (uiState.snapshot.diaryEntriesCount == 1) 1 else 2,
                  ),
                modifier = Modifier.weight(1f),
              )
            }

            IconButton(
              onClick = onOpenSettings,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("home_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_open_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(modifier = Modifier.height(DipeSpacing.sm))
          HairlineDivider()
        }
      }

      // Currently reading: hero book showcase + secondary horizontal covers
      item(key = "home_currently_reading", contentType = "currently_reading") {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(
            title = stringResource(R.string.home_section_currently_reading),
            actionLabel = stringResource(R.string.home_shortcut_open_library),
            actionTag = "home_open_library_header_action",
            onAction = onOpenLibrary,
          )
          if (featuredReading == null) {
            EditorialEmptyState(
              icon = Icons.Outlined.AutoStories,
              title = stringResource(R.string.home_empty_reading_title),
              body = stringResource(R.string.home_empty_reading_body),
              actionLabel = stringResource(R.string.home_shortcut_add_volume),
              actionTestTag = "home_empty_catalog_button",
              onAction = onCatalogVolume,
            )
          } else {
            val onFeaturedClick =
              remember(featuredReading.id, onOpenBook) { { onOpenBook(featuredReading.id) } }
            FeaturedReadingPlate(
              book = featuredReading,
              language = language,
              onClick = onFeaturedClick,
            )
            if (secondaryReading.isNotEmpty()) {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
                items(
                  items = secondaryReading,
                  key = { it.id },
                  contentType = { "reading_plate" },
                ) { book ->
                  val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                  CurrentlyReadingPlate(book = book, onClick = onBookClick)
                }
              }
            }
          }
        }
      }

      // Direct actions
      item(key = "home_quick_actions", contentType = "quick_actions") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
        ) {
          OutlinedButton(
            onClick = onOpenLibrary,
            shape = CircleShape,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
              Modifier.weight(1f).minimumInteractiveComponentSize().testTag("home_shortcut_library"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
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
            shape = CircleShape,
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

      // Recent activity header
      item(key = "home_recent_header", contentType = "section_header") {
        EditorialSectionHeader(title = stringResource(R.string.home_section_recent_activity))
      }

      if (uiState.recentActivity.isEmpty()) {
        item(key = "home_recent_empty", contentType = "empty_state") {
          EditorialEmptyState(
            icon = Icons.Outlined.EditNote,
            title = stringResource(R.string.home_empty_activity_title),
            body = stringResource(R.string.home_empty_activity_body),
          )
        }
      } else {
        items(
          items = uiState.recentActivity,
          key = { it.stableKey },
          contentType = { item ->
            when (item) {
              is RecentActivityItem.BookCataloged -> "activity_book"
              is RecentActivityItem.DiaryRecorded -> "activity_diary"
            }
          },
        ) { item ->
          when (item) {
            is RecentActivityItem.BookCataloged -> {
              val onBookClick = remember(item.book.id, onOpenBook) { { onOpenBook(item.book.id) } }
              RecentBookRow(
                book = item.book,
                timestampMillis = item.timestampMillis,
                language = language,
                onClick = onBookClick,
              )
              HairlineDivider()
            }
            is RecentActivityItem.DiaryRecorded -> {
              val linkedWorkId = item.entry.workId
              val onDiaryClick =
                remember(linkedWorkId, onOpenBook) {
                  linkedWorkId?.let { id -> { onOpenBook(id) } }
                }
              RecentDiaryRow(
                item = item,
                language = language,
                onClick = onDiaryClick,
              )
              HairlineDivider()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OverviewFactInline(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineMedium,
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
private fun FeaturedReadingPlate(
  book: CatalogBook,
  language: AppLanguage,
  onClick: () -> Unit,
) {
  val metadataLine =
    remember(book.totalPages, book.publicationYear, book.editionNote, language) {
      buildList {
        book.totalPages?.let { add(LocaleManager.formatInteger(it, language) + " p.") }
        book.publicationYear?.let { add(it.toString()) }
        book.editionNote?.takeIf { it.isNotBlank() }?.let { add(it) }
      }.joinToString(" · ")
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DipeSpacing.xs)
        .testTag("home_reading_book_${book.id}"),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      elevated = true,
      modifier = Modifier.width(126.dp),
    )

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Text(
        text = book.author,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = book.title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      if (metadataLine.isNotEmpty()) {
        Text(
          text = metadataLine,
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.height(DipeSpacing.xs))
      Button(
        onClick = onClick,
        shape = CircleShape,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text(
          text = stringResource(R.string.home_open_book),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun CurrentlyReadingPlate(
  book: CatalogBook,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.width(124.dp)
        .clickable(onClick = onClick)
        .testTag("home_reading_book_${book.id}"),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      compact = true,
      elevated = true,
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

@Composable
private fun RecentBookRow(
  book: CatalogBook,
  timestampMillis: Long,
  language: AppLanguage,
  onClick: () -> Unit,
) {
  val formattedDate =
    remember(timestampMillis, language) { LocaleManager.formatShortDate(timestampMillis, language) }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .clickable(onClick = onClick)
        .padding(vertical = DipeSpacing.sm),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      compact = true,
      modifier = Modifier.width(38.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${book.author} · ${readingStatusLabel(book.status)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = formattedDate,
      style = LocalDipeTypography.current.monoMetadata,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun RecentDiaryRow(
  item: RecentActivityItem.DiaryRecorded,
  language: AppLanguage,
  onClick: (() -> Unit)?,
) {
  val formattedDate =
    remember(item.timestampMillis, language) {
      LocaleManager.formatShortDate(item.timestampMillis, language)
    }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(IntrinsicSize.Min)
        .defaultMinSize(minHeight = 48.dp)
        .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
        .testTag("home_diary_entry_${item.entry.id}")
        .padding(vertical = DipeSpacing.sm),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
  ) {
    Box(
      modifier =
        Modifier.width(2.dp)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = item.entry.linkedBookTitle ?: stringResource(R.string.diary_general_note_tag),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f).padding(end = DipeSpacing.sm),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = formattedDate,
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
  }
}
