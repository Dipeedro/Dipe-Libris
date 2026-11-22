package com.example.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeTypography
import com.example.core.designsystem.WindowWidthClass
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.designsystem.components.BookSpineTile
import com.example.core.designsystem.components.CompactEditorialSearchPill
import com.example.core.designsystem.components.EditorialEmptyState
import com.example.core.designsystem.components.EditorialMasthead
import com.example.core.designsystem.components.EditorialMetadataStrip
import com.example.core.designsystem.components.EditorialProgressBar
import com.example.core.designsystem.components.EditorialSurfaceCard
import com.example.core.designsystem.components.HairlineDivider
import com.example.core.designsystem.components.MicroMetadataItem
import com.example.core.designsystem.components.MinimalLanguageBadge
import com.example.core.designsystem.components.readingStatusAccentColor
import com.example.core.designsystem.components.readingStatusLabel
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.CatalogBook
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus
import kotlin.math.roundToInt

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
  onGenreFilterChange: (Long?) -> Unit = {},
  onCheckUpdates: () -> Unit = {},
) {
  val language = LocalAppLanguage.current
  val gridState = rememberLazyGridState()
  val listState = rememberLazyListState()
  var showFiltersSheet by remember { mutableStateOf(false) }

  val mastheadHeightPx = remember { mutableFloatStateOf(0f) }
  val mastheadOffsetPx = remember { mutableFloatStateOf(0f) }
  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val maxCollapse = -mastheadHeightPx.floatValue
        if (maxCollapse >= 0f) return Offset.Zero
        val prev = mastheadOffsetPx.floatValue
        val next = (prev + available.y).coerceIn(maxCollapse, 0f)
        val consumed = next - prev
        mastheadOffsetPx.floatValue = next
        return Offset(0f, consumed)
      }
    }
  }

  val countForPlural = if (uiState.books.size == 1) 1 else 2
  val subtitle =
    if (uiState.totalCatalogCount > 0) {
      pluralStringResource(R.plurals.volumes_count, countForPlural, uiState.books.size)
    } else {
      stringResource(R.string.library_empty_body)
    }

  val activeGenre =
    remember(uiState.genreFilter, uiState.availableGenres) {
      uiState.availableGenres.firstOrNull { it.id == uiState.genreFilter }
    }
  val hasActiveSheetBadge =
    uiState.genreFilter != null ||
      uiState.sortOrder != LibrarySortOrder.RECENTLY_ADDED ||
      uiState.totalPendingSuggestionsCount > 0

  BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)
    val contentMaxWidth = minOf(maxWidth, 960.dp) - (horizontalPadding * 2)
    val shelfInnerWidth = (contentMaxWidth - (DipeSpacing.xs * 2)).coerceAtLeast(48.dp)
    val spinesPerShelf =
      remember(shelfInnerWidth) {
        (((shelfInnerWidth + DipeSpacing.xs) / (48.dp + DipeSpacing.xs)).toInt()).coerceAtLeast(1)
      }

    Column(
      modifier =
        Modifier.widthIn(max = 960.dp)
          .fillMaxSize()
          .nestedScroll(nestedScrollConnection),
    ) {
      // Collapsing editorial masthead aligned with Home ("Leitura")
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              val offset = mastheadOffsetPx.floatValue.roundToInt()
              val visibleHeight = (placeable.height + offset).coerceAtLeast(0)
              layout(placeable.width, visibleHeight) {
                placeable.place(0, offset)
              }
            }
            .onSizeChanged { mastheadHeightPx.floatValue = it.height.toFloat() }
            .padding(horizontal = horizontalPadding),
      ) {
        EditorialMasthead(
          title = stringResource(R.string.nav_library),
          subtitle = subtitle,
          trailingContent = {
            Button(
              onClick = onOpenAddBook,
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("library_add_book_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(DipeSpacing.xs))
              Text(
                text = stringResource(R.string.library_add_book),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
              )
            }
          },
        )
      }

      // Fixed sticky search + compact icon tools + single status chip row
      LibraryStickyHeader(
        horizontalPadding = horizontalPadding,
        searchQuery = uiState.searchQuery,
        statusFilter = uiState.statusFilter,
        activeGenreName = activeGenre?.localizedName(language),
        viewMode = uiState.viewMode,
        hasActiveSheetBadge = hasActiveSheetBadge,
        onSearchChange = onSearchChange,
        onStatusFilterChange = onStatusFilterChange,
        onClearGenreFilter = { onGenreFilterChange(null) },
        onViewModeChange = onViewModeChange,
        onOpenFiltersSheet = { showFiltersSheet = true },
      )

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
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("library_grid"),
          contentPadding =
            PaddingValues(
              start = horizontalPadding,
              end = horizontalPadding,
              top = DipeSpacing.md,
              bottom = DipeSpacing.xxl,
            ),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("library_lazy_column"),
          contentPadding =
            PaddingValues(
              start = horizontalPadding,
              end = horizontalPadding,
              top = DipeSpacing.md,
              bottom = DipeSpacing.xxl,
            ),
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
        ) {
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
                }
              }
            }
          }
        }
      }
    }
  }

  if (showFiltersSheet) {
    LibraryFiltersSheet(
      totalCatalogCount = uiState.totalCatalogCount,
      genreFilter = uiState.genreFilter,
      availableGenres = uiState.availableGenres,
      sortOrder = uiState.sortOrder,
      isCheckingUpdates = uiState.isCheckingUpdates,
      totalPendingSuggestionsCount = uiState.totalPendingSuggestionsCount,
      onGenreFilterChange = onGenreFilterChange,
      onSortOrderChange = onSortOrderChange,
      onCheckUpdates = onCheckUpdates,
      onClearFilters = {
        onGenreFilterChange(null)
        onSortOrderChange(LibrarySortOrder.RECENTLY_ADDED)
      },
      onDismiss = { showFiltersSheet = false },
    )
  }
}

@Composable
private fun LibraryStickyHeader(
  horizontalPadding: androidx.compose.ui.unit.Dp,
  searchQuery: String,
  statusFilter: ReadingStatus?,
  activeGenreName: String?,
  viewMode: LibraryViewMode,
  hasActiveSheetBadge: Boolean,
  onSearchChange: (String) -> Unit,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onClearGenreFilter: () -> Unit,
  onViewModeChange: (LibraryViewMode) -> Unit,
  onOpenFiltersSheet: () -> Unit,
) {
  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  Surface(color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = DipeSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
      ) {
        // Compact search pill + View Mode icon + Filters/Tools icon
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CompactEditorialSearchPill(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = stringResource(R.string.library_search_placeholder),
            clearContentDescription = stringResource(R.string.library_clear_search),
            inputTestTag = "library_search_input",
            imeAction = ImeAction.Done,
            modifier = Modifier.weight(1f),
          )

          LibraryViewModeTrigger(
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
          )

          Box(contentAlignment = Alignment.TopEnd) {
            Surface(
              onClick = onOpenFiltersSheet,
              shape = CircleShape,
              color =
                if (hasActiveSheetBadge) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
              contentColor =
                if (hasActiveSheetBadge) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .size(44.dp)
                  .testTag("library_sort_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.Tune,
                  contentDescription = stringResource(R.string.library_filters_cd),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            if (hasActiveSheetBadge) {
              Box(
                modifier =
                  Modifier.padding(top = 6.dp, end = 6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
              )
            }
          }
        }

        // Single clean horizontal status row (+ dismissible genre pill if active)
        val statusRowState = rememberLazyListState()
        LaunchedEffect(activeGenreName) {
          if (activeGenreName != null) {
            statusRowState.scrollToItem(0)
          }
        }
        LazyRow(
          state = statusRowState,
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (activeGenreName != null) {
            item(key = "active_genre_chip", contentType = "genre_active") {
              FilterChip(
                selected = true,
                onClick = onClearGenreFilter,
                label = { Text(activeGenreName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.library_filters_clear),
                    modifier = Modifier.size(14.dp),
                  )
                },
                shape = CircleShape,
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.secondary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.secondary,
                  ),
                border = null,
                modifier = Modifier.testTag("library_active_genre_chip"),
              )
            }
          }

          item(key = "filter_all", contentType = "status_filter") {
            FilterChip(
              selected = statusFilter == null,
              onClick = { onStatusFilterChange(null) },
              label = { Text(stringResource(R.string.library_filter_all)) },
              shape = CircleShape,
              colors = chipColors,
              border = null,
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
              border = null,
              modifier = Modifier.testTag("library_filter_${status.name.lowercase()}"),
            )
          }
        }
      }
      HairlineDivider()
    }
  }
}

@Composable
private fun LibraryViewModeTrigger(
  viewMode: LibraryViewMode,
  onViewModeChange: (LibraryViewMode) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Box {
    Surface(
      onClick = { menuExpanded = true },
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .size(44.dp)
          .testTag("library_view_mode_trigger"),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = viewModeIcon(viewMode),
          contentDescription = stringResource(R.string.library_view_mode_cd),
          modifier = Modifier.size(20.dp),
        )
      }
    }

    DropdownMenu(
      expanded = menuExpanded,
      onDismissRequest = { menuExpanded = false },
    ) {
      LibraryViewMode.entries.forEach { mode ->
        val selected = viewMode == mode
        DropdownMenuItem(
          leadingIcon = {
            Icon(
              imageVector = viewModeIcon(mode),
              contentDescription = null,
              tint =
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          },
          text = {
            Text(
              text = viewModeLabel(mode),
              color =
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface,
            )
          },
          trailingIcon =
            if (selected) {
              {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp),
                )
              }
            } else {
              null
            },
          onClick = {
            onViewModeChange(mode)
            menuExpanded = false
          },
          modifier = Modifier.testTag("library_view_mode_${mode.name.lowercase()}"),
        )
      }
    }
  }
}

@Composable
private fun viewModeIcon(mode: LibraryViewMode) =
  when (mode) {
    LibraryViewMode.GRID -> Icons.Outlined.GridView
    LibraryViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
    LibraryViewMode.SHELF -> Icons.Outlined.ViewColumn
    LibraryViewMode.GALLERY -> Icons.Outlined.PhotoLibrary
  }

@Composable
private fun viewModeLabel(mode: LibraryViewMode): String =
  when (mode) {
    LibraryViewMode.GRID -> stringResource(R.string.library_view_grid)
    LibraryViewMode.LIST -> stringResource(R.string.library_view_list)
    LibraryViewMode.SHELF -> stringResource(R.string.library_view_shelf)
    LibraryViewMode.GALLERY -> stringResource(R.string.library_view_gallery)
  }

@Composable
private fun LibraryGridCard(
  book: CatalogBook,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val statusColor = readingStatusAccentColor(book.status)
  val showActiveProgress =
    (book.status == ReadingStatus.READING || book.status == ReadingStatus.PAUSED) &&
      book.activeProgressPercent != null

  Column(
    modifier =
      modifier
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("library_grid_book_${book.id}"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BookCoverArtwork(
      title = book.title,
      author = book.author,
      isFavorite = book.isFavorite,
      coverUri = book.coverUri,
      elevated = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (showActiveProgress) {
      EditorialProgressBar(progressPercent = book.activeProgressPercent!!, height = 2.dp)
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
      Spacer(modifier = Modifier.height(DipeSpacing.xxs))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
          val statusText =
            if (showActiveProgress) {
              "${readingStatusLabel(book.status)} · ${LocaleManager.formatPercent(book.activeProgressPercent!!, language)}"
            } else {
              readingStatusLabel(book.status)
            }
          Text(
            text = statusText,
            style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        MinimalLanguageBadge(languageTag = book.primaryEdition?.language)
      }

      if (book.editionCount > 1 || book.pendingSuggestionsCount > 0) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (book.editionCount > 1) {
            MicroMetadataItem(
              icon = Icons.Outlined.Layers,
              text = pluralStringResource(R.plurals.editions_short_count, book.editionCount, book.editionCount),
            )
          }
          if (book.pendingSuggestionsCount > 0) {
            Text(
              text = "+${book.pendingSuggestionsCount}",
              style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.sp),
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
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
  val language = LocalAppLanguage.current
  val statusColor = readingStatusAccentColor(book.status)
  val showActiveProgress =
    (book.status == ReadingStatus.READING || book.status == ReadingStatus.PAUSED) &&
      book.activeProgressPercent != null

  EditorialSurfaceCard(
    modifier = Modifier.testTag("library_list_book_${book.id}"),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    shape = MaterialTheme.shapes.medium,
    contentPadding = PaddingValues(horizontal = DipeSpacing.md, vertical = DipeSpacing.compact),
    onClick = onClick,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.sm)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookCoverArtwork(
          title = book.title,
          author = book.author,
          isFavorite = book.isFavorite,
          coverUri = book.coverUri,
          compact = true,
          elevated = true,
          modifier = Modifier.width(50.dp),
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = book.title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            MinimalLanguageBadge(languageTag = book.primaryEdition?.language)
          }
          Text(
            text = book.author,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Row(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
              val statusStr =
                if (showActiveProgress) {
                  "${readingStatusLabel(book.status)} · ${LocaleManager.formatPercent(book.activeProgressPercent!!, language)}"
                } else {
                  readingStatusLabel(book.status)
                }
              Text(
                text = statusStr,
                style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            book.totalPages?.let { pages ->
              MicroMetadataItem(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                text = "${LocaleManager.formatInteger(pages, language)} p.",
              )
            }

            if (book.editionCount > 1) {
              MicroMetadataItem(
                icon = Icons.Outlined.Layers,
                text = pluralStringResource(R.plurals.editions_short_count, book.editionCount, book.editionCount),
              )
            }
          }
        }
        IconButton(
          onClick = onToggleFavorite,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("library_favorite_${book.id}"),
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

      if (showActiveProgress) {
        EditorialProgressBar(progressPercent = book.activeProgressPercent!!, height = 2.dp)
      }
    }
  }
}

@Composable
private fun LibraryShelfRow(
  shelfBooks: List<CatalogBook>,
  onOpenBook: (Long) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = DipeSpacing.md, bottom = DipeSpacing.sm),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = DipeSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      verticalAlignment = Alignment.Bottom,
    ) {
      shelfBooks.forEach { book ->
        val spineHeight =
          remember(book.title) { (164 + ((book.title.hashCode() and Int.MAX_VALUE) % 32)).dp }
        val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
        BookSpineTile(
          title = book.title,
          author = book.author,
          isFavorite = book.isFavorite,
          height = spineHeight,
          hasActiveReading = book.status == ReadingStatus.READING,
          hasMultipleCopies = book.copyCount > 1,
          modifier =
            Modifier.clickable(onClick = onBookClick)
              .testTag("library_shelf_book_${book.id}"),
        )
      }
    }
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(6.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(3.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerLow),
    )
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
  val showActiveProgress =
    (book.status == ReadingStatus.READING || book.status == ReadingStatus.PAUSED) &&
      book.activeProgressPercent != null

  EditorialSurfaceCard(
    modifier = Modifier.testTag("library_gallery_book_${book.id}"),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.xl),
    onClick = onClick,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(DipeSpacing.lg),
    ) {
      BookCoverArtwork(
        title = book.title,
        author = book.author,
        isFavorite = book.isFavorite,
        coverUri = book.coverUri,
        elevated = true,
        modifier = Modifier.width(196.dp),
      )
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
      ) {
        Text(
          text = book.title,
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Text(
          text = book.author,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MinimalLanguageBadge(languageTag = book.primaryEdition?.language)
          if (book.editionCount > 1) {
            MicroMetadataItem(
              icon = Icons.Outlined.Layers,
              text =
                pluralStringResource(
                  R.plurals.editions_short_count,
                  book.editionCount,
                  book.editionCount,
                ),
            )
          }
        }
      }

      if (showActiveProgress) {
        Column(
          modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
        ) {
          EditorialProgressBar(progressPercent = book.activeProgressPercent!!, height = 3.dp)
          Text(
            text = LocaleManager.formatPercent(book.activeProgressPercent!!, language),
            style = LocalDipeTypography.current.monoMetadata,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      EditorialMetadataStrip(
        items =
          listOf(
            stringResource(R.string.book_detail_status_label) to statusValue,
            stringResource(R.string.book_detail_pages_label) to pagesValue,
            stringResource(R.string.book_detail_year_label) to yearValue,
          ),
        modifier = Modifier.widthIn(max = 420.dp),
      )
    }
  }
}
