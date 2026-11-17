package com.example.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.BookSpineTile
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialMetadataStrip
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.readingStatusAccentColor
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.CatalogBook
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus

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
  val gridState = rememberLazyGridState()
  val listState = rememberLazyListState()

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val horizontalPadding =
      if (widthClass == WindowWidthClass.COMPACT) DipeSpacing.md else DipeSpacing.xl
    val contentMaxWidth = minOf(maxWidth, 960.dp) - (horizontalPadding * 2)
    val shelfInnerWidth = (contentMaxWidth - (DipeSpacing.xs * 2)).coerceAtLeast(48.dp)
    val spinesPerShelf =
      remember(shelfInnerWidth) {
        (((shelfInnerWidth + DipeSpacing.xs) / (48.dp + DipeSpacing.xs)).toInt()).coerceAtLeast(1)
      }

    if (uiState.viewMode == LibraryViewMode.GRID && uiState.books.isNotEmpty()) {
      val columns =
        when (widthClass) {
          WindowWidthClass.COMPACT -> 2
          WindowWidthClass.MEDIUM -> 3
          WindowWidthClass.EXPANDED -> 4
        }

      LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("library_grid"),
        contentPadding =
          PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
      ) {
        item(
          key = "library_controls",
          span = { GridItemSpan(maxLineSpan) },
          contentType = { "controls" },
        ) {
          LibraryControlsBlock(
            totalCatalogCount = uiState.totalCatalogCount,
            filteredCount = uiState.books.size,
            searchQuery = uiState.searchQuery,
            statusFilter = uiState.statusFilter,
            viewMode = uiState.viewMode,
            onSearchChange = onSearchChange,
            onStatusFilterChange = onStatusFilterChange,
            onSortOrderChange = onSortOrderChange,
            onViewModeChange = onViewModeChange,
            onOpenAddBook = onOpenAddBook,
          )
        }

        items(
          items = uiState.books,
          key = { it.id },
          contentType = { "grid_book" },
        ) { book ->
          val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
          LibraryGridCard(book = book, onClick = onBookClick)
        }
      }
    } else {
      val shelfRows =
        remember(uiState.books, spinesPerShelf, uiState.viewMode) {
          if (uiState.viewMode == LibraryViewMode.SHELF) uiState.books.chunked(spinesPerShelf)
          else emptyList()
        }

      LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("library_lazy_column"),
        contentPadding =
          PaddingValues(horizontal = horizontalPadding, vertical = DipeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        item(key = "library_controls", contentType = "controls") {
          LibraryControlsBlock(
            totalCatalogCount = uiState.totalCatalogCount,
            filteredCount = uiState.books.size,
            searchQuery = uiState.searchQuery,
            statusFilter = uiState.statusFilter,
            viewMode = uiState.viewMode,
            onSearchChange = onSearchChange,
            onStatusFilterChange = onStatusFilterChange,
            onSortOrderChange = onSortOrderChange,
            onViewModeChange = onViewModeChange,
            onOpenAddBook = onOpenAddBook,
          )
        }

        if (uiState.totalCatalogCount == 0) {
          item(key = "library_empty", contentType = "empty_state") {
            EditorialEmptyState(
              icon = Icons.AutoMirrored.Outlined.LibraryBooks,
              title = stringResource(R.string.library_empty_title),
              body = stringResource(R.string.library_empty_body),
              actionLabel = stringResource(R.string.library_add_book),
              actionTestTag = "library_empty_add_button",
              onAction = onOpenAddBook,
            )
          }
        } else if (uiState.books.isEmpty()) {
          item(key = "library_filtered_empty", contentType = "empty_state") {
            EditorialEmptyState(
              icon = Icons.Outlined.Search,
              title = stringResource(R.string.library_empty_filtered_title),
              body = stringResource(R.string.library_empty_filtered_body),
            )
          }
        } else {
          when (uiState.viewMode) {
            LibraryViewMode.GRID -> Unit
            LibraryViewMode.LIST -> {
              items(
                items = uiState.books,
                key = { it.id },
                contentType = { "list_book" },
              ) { book ->
                val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                val onFavClick = remember(book, onToggleFavorite) { { onToggleFavorite(book) } }
                LibraryListRow(
                  book = book,
                  onClick = onBookClick,
                  onToggleFavorite = onFavClick,
                )
                HairlineDivider()
              }
            }

            LibraryViewMode.SHELF -> {
              items(
                items = shelfRows,
                key = { row -> row.first().id },
                contentType = { "shelf_row" },
              ) { shelfBooks ->
                LibraryShelfRow(
                  shelfBooks = shelfBooks,
                  onOpenBook = onOpenBook,
                )
              }
            }

            LibraryViewMode.GALLERY -> {
              items(
                items = uiState.books,
                key = { it.id },
                contentType = { "gallery_book" },
              ) { book ->
                val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                LibraryGalleryItem(book = book, onClick = onBookClick)
                HairlineDivider()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryControlsBlock(
  totalCatalogCount: Int,
  filteredCount: Int,
  searchQuery: String,
  statusFilter: ReadingStatus?,
  viewMode: LibraryViewMode,
  onSearchChange: (String) -> Unit,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onSortOrderChange: (LibrarySortOrder) -> Unit,
  onViewModeChange: (LibraryViewMode) -> Unit,
  onOpenAddBook: () -> Unit,
) {
  val countForPlural = if (filteredCount == 1) 1 else 2
  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surface,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text =
          if (totalCatalogCount > 0) {
            pluralStringResource(R.plurals.volumes_count, countForPlural, filteredCount).uppercase()
          } else {
            ""
          },
        style = LocalDipeTypography.current.editorialKicker,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(
        onClick = onOpenAddBook,
        shape = CircleShape,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.xs),
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
    }

    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
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
      shape = CircleShape,
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
      modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
    )

    // Quiet editorial view-mode strip + sort trigger
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LazyRow(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        items(LibraryViewMode.entries, key = { it.name }, contentType = { "view_mode_tab" }) { mode
          ->
          val selected = viewMode == mode
          Column(
            modifier =
              Modifier.defaultMinSize(minHeight = 48.dp)
                .clickable { onViewModeChange(mode) }
                .padding(horizontal = DipeSpacing.xxs, vertical = DipeSpacing.xs)
                .testTag("library_view_mode_${mode.name.lowercase()}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            ) {
              Icon(
                imageVector =
                  when (mode) {
                    LibraryViewMode.GRID -> Icons.Outlined.GridView
                    LibraryViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                    LibraryViewMode.SHELF -> Icons.Outlined.ViewColumn
                    LibraryViewMode.GALLERY -> Icons.Outlined.PhotoLibrary
                  },
                contentDescription = null,
                tint =
                  if (selected) MaterialTheme.colorScheme.onBackground
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text =
                  when (mode) {
                    LibraryViewMode.GRID -> stringResource(R.string.library_view_grid)
                    LibraryViewMode.LIST -> stringResource(R.string.library_view_list)
                    LibraryViewMode.SHELF -> stringResource(R.string.library_view_shelf)
                    LibraryViewMode.GALLERY -> stringResource(R.string.library_view_gallery)
                  },
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (selected) MaterialTheme.colorScheme.onBackground
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.height(DipeSpacing.xs))
            Box(
              modifier =
                Modifier.width(18.dp)
                  .height(2.dp)
                  .clip(CircleShape)
                  .background(
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                  ),
            )
          }
        }
      }

      LibrarySortControl(onSortOrderChange = onSortOrderChange)
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs)) {
      item(key = "filter_all", contentType = "status_filter") {
        FilterChip(
          selected = statusFilter == null,
          onClick = { onStatusFilterChange(null) },
          label = { Text(stringResource(R.string.library_filter_all)) },
          shape = CircleShape,
          colors = chipColors,
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = statusFilter == null,
              borderColor = MaterialTheme.colorScheme.outlineVariant,
              borderWidth = 0.75.dp,
            ),
          modifier = Modifier.testTag("library_filter_all"),
        )
      }
      items(ReadingStatus.entries, key = { it.name }, contentType = { "status_filter" }) { status ->
        val selected = statusFilter == status
        FilterChip(
          selected = selected,
          onClick = { onStatusFilterChange(if (selected) null else status) },
          label = { Text(readingStatusLabel(status)) },
          shape = CircleShape,
          colors = chipColors,
          border =
            FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = selected,
              borderColor = MaterialTheme.colorScheme.outlineVariant,
              borderWidth = 0.75.dp,
            ),
          modifier = Modifier.testTag("library_filter_${status.name.lowercase()}"),
        )
      }
    }

    HairlineDivider()
  }
}

@Composable
private fun LibrarySortControl(onSortOrderChange: (LibrarySortOrder) -> Unit) {
  var sortMenuExpanded by remember { mutableStateOf(false) }

  Box {
    TextButton(
      onClick = { sortMenuExpanded = true },
      modifier = Modifier.minimumInteractiveComponentSize().testTag("library_sort_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Sort,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(DipeSpacing.xs))
      Text(
        text = stringResource(R.string.library_sort_label),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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

@Composable
private fun LibraryGridCard(
  book: CatalogBook,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val statusColor = readingStatusAccentColor(book.status)
  Column(
    modifier = modifier.clickable(onClick = onClick).testTag("library_grid_book_${book.id}"),
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      elevated = true,
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
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
        Text(
          text = readingStatusLabel(book.status),
          style = LocalDipeTypography.current.monoMetadata,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LibraryListRow(
  book: CatalogBook,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
) {
  val statusColor = readingStatusAccentColor(book.status)
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
      elevated = true,
      modifier = Modifier.width(56.dp),
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
      Row(
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
        Text(
          text = readingStatusLabel(book.status),
          style = LocalDipeTypography.current.monoMetadata,
          color = statusColor,
        )
        book.totalPages?.let { pages ->
          Text(
            text = "·",
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              pluralStringResource(
                R.plurals.pages_count,
                if (pages == 1) 1 else 2,
                pages,
              ),
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun LibraryShelfRow(
  shelfBooks: List<CatalogBook>,
  onOpenBook: (Long) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.sm),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      verticalAlignment = Alignment.Bottom,
    ) {
      shelfBooks.forEach { book ->
        val spineHeight =
          remember(book.title) { (138 + ((book.title.hashCode() and Int.MAX_VALUE) % 34)).dp }
        val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
        BookSpineTile(
          title = book.title,
          author = book.author,
          isFavorite = book.isFavorite,
          height = spineHeight,
          modifier =
            Modifier.clickable(onClick = onBookClick)
              .testTag("library_shelf_book_${book.id}"),
        )
      }
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(5.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
    HairlineDivider()
  }
}

@Composable
private fun LibraryGalleryItem(
  book: CatalogBook,
  onClick: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val pagesValue =
    book.totalPages?.let { LocaleManager.formatInteger(it, language) }
      ?: stringResource(R.string.book_detail_unspecified)
  val yearValue = book.publicationYear?.toString() ?: stringResource(R.string.book_detail_unspecified)
  val statusValue = readingStatusLabel(book.status)
  val stripItems =
    remember(statusValue, pagesValue, yearValue) {
      listOf(
        "Status" to statusValue,
        "Pág." to pagesValue,
        "Ano" to yearValue,
      )
    }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = DipeSpacing.lg)
        .testTag("library_gallery_book_${book.id}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      elevated = true,
      modifier = Modifier.width(204.dp),
    )
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
    ) {
      Text(
        text = book.title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
      Text(
        text = book.author,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    EditorialMetadataStrip(
      items = listOf(
        stringResource(R.string.book_detail_status_label) to statusValue,
        stringResource(R.string.book_detail_pages_label) to pagesValue,
        stringResource(R.string.book_detail_year_label) to yearValue,
      ),
      modifier = Modifier.widthIn(max = 420.dp).padding(top = DipeSpacing.xs),
    )
  }
}
