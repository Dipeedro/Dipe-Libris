package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.core.designsystem.components.BookSpineTile
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialPageHeader
import com.example.core.designsystem.components.EditorialPlannedBlock
import com.example.core.designsystem.components.EditorialSectionHeader
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.model.CatalogBook
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
  uiState: LibraryUiState,
  widthClass: WindowWidthClass,
  onSearchChange: (String) -> Unit,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onSortOrderChange: (LibrarySortOrder) -> Unit,
  onViewModeChange: (LibraryViewMode) -> Unit,
  onOpenAddBook: () -> Unit,
  onOpenBook: (Long) -> Unit,
  onToggleFavorite: (CatalogBook) -> Unit,
  modifier: Modifier = Modifier,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val horizontalPadding = if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
    val contentMaxWidth = minOf(maxWidth, 960.dp) - (horizontalPadding * 2)
    val shelfInnerWidth = (contentMaxWidth - (DipeSpacing.xs * 2)).coerceAtLeast(48.dp)
    val spineWidth = 48.dp
    val spineGap = DipeSpacing.xs
    val spinesPerShelf =
      (((shelfInnerWidth + spineGap) / (spineWidth + spineGap)).toInt()).coerceAtLeast(1)

    LazyColumn(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("library_lazy_column"),
      contentPadding =
        PaddingValues(
          horizontal = horizontalPadding,
          vertical = DipeSpacing.lg,
        ),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      item {
        EditorialPageHeader(
          kicker = stringResource(R.string.home_editorial_kicker),
          title = stringResource(R.string.library_title),
          subtitle = stringResource(R.string.library_subtitle),
          trailingContent = {
            OutlinedButton(
              onClick = onOpenAddBook,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("library_add_book_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(stringResource(R.string.library_add_book))
            }
          },
        )
      }

      // Search & Layout View Modes + Sort
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
            leadingIcon = {
              Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
              if (uiState.searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { onSearchChange("") },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.library_clear_search),
                  )
                }
              }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LazyRow(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              items(LibraryViewMode.entries, key = { it.name }) { mode ->
                val selected = uiState.viewMode == mode
                FilterChip(
                  selected = selected,
                  onClick = { onViewModeChange(mode) },
                  leadingIcon = {
                    Icon(
                      imageVector =
                        when (mode) {
                          LibraryViewMode.GRID -> Icons.Outlined.GridView
                          LibraryViewMode.LIST -> Icons.Outlined.ViewList
                          LibraryViewMode.SHELF -> Icons.Outlined.ViewColumn
                          LibraryViewMode.GALLERY -> Icons.Outlined.PhotoLibrary
                        },
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = {
                    Text(
                      when (mode) {
                        LibraryViewMode.GRID -> stringResource(R.string.library_view_grid)
                        LibraryViewMode.LIST -> stringResource(R.string.library_view_list)
                        LibraryViewMode.SHELF -> stringResource(R.string.library_view_shelf)
                        LibraryViewMode.GALLERY -> stringResource(R.string.library_view_gallery)
                      }
                    )
                  },
                  modifier = Modifier.testTag("library_view_mode_${mode.name.lowercase()}"),
                )
              }
            }

            Box {
              TextButton(
                onClick = { sortMenuExpanded = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("library_sort_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Sort,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(DipeSpacing.xs))
                Text(stringResource(R.string.library_sort_label))
              }
              DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
              ) {
                LibrarySortOrder.entries.forEach { order ->
                  DropdownMenuItem(
                    text = {
                      Text(
                        when (order) {
                          LibrarySortOrder.RECENTLY_ADDED -> stringResource(R.string.library_sort_recent)
                          LibrarySortOrder.TITLE_ASC -> stringResource(R.string.library_sort_title)
                          LibrarySortOrder.AUTHOR_ASC -> stringResource(R.string.library_sort_author)
                        }
                      )
                    },
                    onClick = {
                      onSortOrderChange(order)
                      sortMenuExpanded = false
                    },
                  )
                }
              }
            }
          }

          // Status filters
          LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
            item {
              FilterChip(
                selected = uiState.statusFilter == null,
                onClick = { onStatusFilterChange(null) },
                label = { Text(stringResource(R.string.library_filter_all)) },
                modifier = Modifier.testTag("library_filter_all"),
              )
            }
            items(ReadingStatus.entries, key = { it.name }) { status ->
              FilterChip(
                selected = uiState.statusFilter == status,
                onClick = {
                  onStatusFilterChange(if (uiState.statusFilter == status) null else status)
                },
                label = { Text(readingStatusLabel(status)) },
                modifier = Modifier.testTag("library_filter_${status.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Catalog Content
      if (uiState.totalCatalogCount == 0) {
        item {
          EditorialEmptyState(
            icon = Icons.Outlined.LibraryBooks,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            actionLabel = stringResource(R.string.library_add_book),
            actionTestTag = "library_empty_add_button",
            onAction = onOpenAddBook,
          )
        }
      } else if (uiState.books.isEmpty()) {
        item {
          EditorialEmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.library_empty_filtered_title),
            body = stringResource(R.string.library_empty_filtered_body),
          )
        }
      } else {
        when (uiState.viewMode) {
          LibraryViewMode.GRID -> {
            val columns =
              when (widthClass) {
                WindowWidthClass.COMPACT -> 2
                WindowWidthClass.MEDIUM -> 3
                WindowWidthClass.EXPANDED -> 4
              }
            val rows = uiState.books.chunked(columns)
            items(rows, key = { row -> row.first().id }) { rowBooks ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                rowBooks.forEach { book ->
                  LibraryGridCard(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    modifier = Modifier.weight(1f),
                  )
                }
                repeat(columns - rowBooks.size) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }

          LibraryViewMode.LIST -> {
            items(uiState.books, key = { it.id }) { book ->
              LibraryListRow(
                book = book,
                onClick = { onOpenBook(book.id) },
                onToggleFavorite = { onToggleFavorite(book) },
              )
              HairlineDivider()
            }
          }

          LibraryViewMode.SHELF -> {
            val shelfRows = uiState.books.chunked(spinesPerShelf)
            items(shelfRows, key = { row -> row.first().id }) { shelfBooks ->
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.xs),
                  horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  verticalAlignment = Alignment.Bottom,
                ) {
                  shelfBooks.forEach { book ->
                    val spineHeight = (136 + (abs(book.title.hashCode()) % 36)).dp
                    BookSpineTile(
                      title = book.title,
                      author = book.author,
                      isFavorite = book.isFavorite,
                      height = spineHeight,
                      modifier =
                        Modifier.clickable { onOpenBook(book.id) }
                          .testTag("library_shelf_book_${book.id}"),
                    )
                  }
                }
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(6.dp)
                      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
              }
            }
          }

          LibraryViewMode.GALLERY -> {
            items(uiState.books, key = { it.id }) { book ->
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onOpenBook(book.id) }
                    .padding(vertical = DipeSpacing.sm)
                    .testTag("library_gallery_book_${book.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
              ) {
                BookCoverArtwork(
                  title = book.title,
                  author = book.author,
                  isFavorite = book.isFavorite,
                  modifier = Modifier.width(210.dp),
                )
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                ) {
                  Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                  Text(
                    text = "${book.author} · ${readingStatusLabel(book.status)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              HairlineDivider()
            }
          }
        }
      }

      // Custom shelves & future catalog tools note
      item {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.md)) {
          EditorialSectionHeader(title = stringResource(R.string.library_shelves_label))
          EditorialPlannedBlock(
            title = stringResource(R.string.library_shelves_label),
            description = stringResource(R.string.library_shelves_planned_note),
            icon = Icons.Outlined.LibraryBooks,
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryGridCard(
  book: CatalogBook,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.clickable(onClick = onClick).testTag("library_grid_book_${book.id}"),
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
        style = MaterialTheme.typography.titleMedium,
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
      Text(
        text = readingStatusLabel(book.status).uppercase(),
        style = LocalDipeTypography.current.editorialKicker,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
  }
}

@Composable
private fun LibraryListRow(
  book: CatalogBook,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DipeSpacing.sm)
        .testTag("library_list_book_${book.id}"),
    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      compact = true,
      modifier = Modifier.width(64.dp),
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = book.author,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.extraSmall,
          border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Text(
            text = readingStatusLabel(book.status),
            style = LocalDipeTypography.current.monoMetadata,
            modifier = Modifier.padding(horizontal = DipeSpacing.sm, vertical = 2.dp),
          )
        }
        book.totalPages?.let { pages ->
          Text(
            text =
              pluralStringResource(
                R.plurals.pages_count,
                if (pages == 1) 1 else 2,
                pages,
              ),
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
        }
      }
    }
    IconButton(
      onClick = onToggleFavorite,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("library_favorite_${book.id}"),
    ) {
      Icon(
        imageVector =
          if (book.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription = stringResource(R.string.cd_toggle_favorite),
        tint =
          if (book.isFavorite) MaterialTheme.colorScheme.secondary
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
