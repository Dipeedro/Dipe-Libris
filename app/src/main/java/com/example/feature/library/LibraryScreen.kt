package com.example.feature.library

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
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
import kotlinx.coroutines.delay

private val EditorialCollapseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val HEADER_COLLAPSE_ANIM_MS = 220
private const val VIEW_MODE_PILL_DURATION_MS = 1200L

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
  onToggleFavorite: (CatalogBook) -> Unit = {},
  modifier: Modifier = Modifier,
  onGenreFilterChange: (Long?) -> Unit = {},
  onUpdateBookStatus: (Long, ReadingStatus) -> Unit = { _, _ -> },
  onDeleteBook: (Long) -> Unit = {},
) {
  val language = LocalAppLanguage.current
  val density = LocalDensity.current
  val reduceMotion = DipeTheme.motion.reduceMotion
  val configuration = LocalConfiguration.current
  val gridState = rememberLazyGridState()
  val listState = rememberLazyListState()

  var showFiltersSheet by remember { mutableStateOf(false) }
  var containerWidthPx by remember { mutableIntStateOf(0) }
  var isHeaderCollapsed by remember { mutableStateOf(false) }

  var statusDialogBook by remember { mutableStateOf<CatalogBook?>(null) }
  var deleteConfirmBook by remember { mutableStateOf<CatalogBook?>(null) }

  var modePillMode by remember { mutableStateOf<LibraryViewMode?>(null) }
  var modePillTrigger by remember { mutableIntStateOf(0) }

  LaunchedEffect(modePillTrigger) {
    if (modePillTrigger > 0) {
      delay(VIEW_MODE_PILL_DURATION_MS)
      modePillMode = null
    }
  }

  val isGridActive = uiState.viewMode == LibraryViewMode.GRID && uiState.books.isNotEmpty()
  val collapseStepPx = remember(density) { with(density) { 12.dp.roundToPx() } }
  val topSnapThresholdPx = remember(density) { with(density) { 16.dp.roundToPx() } }

  LaunchedEffect(
    isGridActive,
    uiState.books.isEmpty(),
    uiState.statusFilter,
    uiState.genreFilter,
    uiState.searchQuery,
  ) {
    if (uiState.books.isEmpty()) {
      isHeaderCollapsed = false
      return@LaunchedEffect
    }
    var lastIndex =
      if (isGridActive) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
    var lastOffset =
      if (isGridActive) gridState.firstVisibleItemScrollOffset
      else listState.firstVisibleItemScrollOffset
    var anchorIndex = lastIndex
    var anchorOffset = lastOffset
    var lastDirection = 0

    snapshotFlow {
        if (isGridActive) {
          gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        } else {
          listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
      }
      .collect { (index, offset) ->
        if (index == 0 && offset <= topSnapThresholdPx) {
          isHeaderCollapsed = false
          anchorIndex = index
          anchorOffset = offset
          lastDirection = 0
        } else {
          val deltaDir =
            when {
              index > lastIndex || (index == lastIndex && offset > lastOffset) -> 1
              index < lastIndex || (index == lastIndex && offset < lastOffset) -> -1
              else -> 0
            }
          if (deltaDir != 0) {
            if (deltaDir != lastDirection) {
              anchorIndex = lastIndex
              anchorOffset = lastOffset
              lastDirection = deltaDir
            }
            if (deltaDir == 1) {
              if (index > anchorIndex || (index == anchorIndex && offset - anchorOffset >= collapseStepPx)) {
                isHeaderCollapsed = true
                anchorIndex = index
                anchorOffset = offset
              }
            } else {
              if (index < anchorIndex || (index == anchorIndex && anchorOffset - offset >= collapseStepPx)) {
                isHeaderCollapsed = false
                anchorIndex = index
                anchorOffset = offset
              }
            }
          }
        }
        lastIndex = index
        lastOffset = offset
      }
  }

  val collapseFraction by
    animateFloatAsState(
      targetValue = if (isHeaderCollapsed) 1f else 0f,
      animationSpec =
        if (reduceMotion) snap()
        else tween(durationMillis = HEADER_COLLAPSE_ANIM_MS, easing = EditorialCollapseEasing),
      label = "library_header_collapse",
    )

  val activeReadingCount =
    remember(uiState.readingCount, uiState.books, uiState.totalCatalogCount) {
      if (uiState.readingCount > 0) {
        uiState.readingCount
      } else if (uiState.statusFilter == null && uiState.genreFilter == null && uiState.searchQuery.isBlank()) {
        uiState.books.count { it.status == ReadingStatus.READING }
      } else {
        0
      }
    }

  val subtitle =
    if (uiState.totalCatalogCount > 0) {
      val volumesCount = uiState.totalCatalogCount
      val volumesPlural = if (volumesCount == 1) 1 else 2
      val volumesText = pluralStringResource(R.plurals.volumes_count, volumesPlural, volumesCount)
      if (activeReadingCount > 0) {
        stringResource(R.string.library_subtitle_with_reading, volumesText, activeReadingCount)
      } else {
        volumesText
      }
    } else {
      stringResource(R.string.library_empty_body)
    }

  val activeGenre =
    remember(uiState.genreFilter, uiState.availableGenres) {
      uiState.availableGenres.firstOrNull { it.id == uiState.genreFilter }
    }
  val hasActiveSheetBadge =
    uiState.genreFilter != null || uiState.sortOrder != LibrarySortOrder.RECENTLY_ADDED
  val horizontalPadding = DipeSpacing.horizontalMargin(widthClass)

  val spinesPerShelf =
    remember(containerWidthPx, horizontalPadding, density) {
      if (containerWidthPx <= 0) {
        6
      } else {
        val availableWidthDp =
          with(density) { containerWidthPx.toDp() }.coerceAtMost(960.dp) - (horizontalPadding * 2)
        val shelfInnerWidth = (availableWidthDp - (DipeSpacing.xs * 2)).coerceAtLeast(48.dp)
        (((shelfInnerWidth + DipeSpacing.xs) / (48.dp + DipeSpacing.xs)).toInt()).coerceAtLeast(1)
      }
    }

  val handleCycleViewMode: () -> Unit = {
    val nextMode = nextViewMode(uiState.viewMode)
    onViewModeChange(nextMode)
    modePillMode = nextMode
    modePillTrigger++
  }

  val handleClearAllFilters: () -> Unit = {
    onSearchChange("")
    onStatusFilterChange(null)
    onGenreFilterChange(null)
  }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().onSizeChanged { containerWidthPx = it.width },
    contentAlignment = Alignment.TopCenter,
  ) {
    val isLandscape =
      (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && maxWidth >= 380.dp) ||
        (maxWidth > maxHeight && maxWidth >= 440.dp)

    Box(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
      LibraryHeaderReserveScaffold(
        header = {
          if (isLandscape) {
            LibraryLandscapeHeader(
              horizontalPadding = horizontalPadding,
              searchQuery = uiState.searchQuery,
              statusFilter = uiState.statusFilter,
              activeGenreName = activeGenre?.localizedName(language),
              viewMode = uiState.viewMode,
              hasActiveSheetBadge = hasActiveSheetBadge,
              collapseFraction = collapseFraction,
              onSearchChange = onSearchChange,
              onStatusFilterChange = onStatusFilterChange,
              onClearGenreFilter = { onGenreFilterChange(null) },
              onCycleViewMode = handleCycleViewMode,
              onOpenFiltersSheet = { showFiltersSheet = true },
              onOpenAddBook = onOpenAddBook,
            )
          } else {
            LibraryPortraitHeader(
              horizontalPadding = horizontalPadding,
              subtitle = subtitle,
              searchQuery = uiState.searchQuery,
              statusFilter = uiState.statusFilter,
              activeGenreName = activeGenre?.localizedName(language),
              viewMode = uiState.viewMode,
              hasActiveSheetBadge = hasActiveSheetBadge,
              collapseFraction = collapseFraction,
              onSearchChange = onSearchChange,
              onStatusFilterChange = onStatusFilterChange,
              onClearGenreFilter = { onGenreFilterChange(null) },
              onCycleViewMode = handleCycleViewMode,
              onOpenFiltersSheet = { showFiltersSheet = true },
              onOpenAddBook = onOpenAddBook,
            )
          }
        },
        body = { reservedTopHeight ->
          val listTopPadding = reservedTopHeight + DipeSpacing.sm
          when {
            uiState.totalCatalogCount == 0 -> {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                      start = horizontalPadding,
                      end = horizontalPadding,
                      top = listTopPadding,
                      bottom = DipeSpacing.xxl,
                    ),
                contentAlignment = Alignment.Center,
              ) {
                EditorialEmptyState(
                  icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                  title = stringResource(R.string.library_empty_title),
                  body = stringResource(R.string.library_empty_body),
                  actionLabel = stringResource(R.string.library_add_book),
                  actionTestTag = "library_empty_add_button",
                  onAction = onOpenAddBook,
                )
              }
            }

            uiState.books.isEmpty() -> {
              LibraryFilteredEmptyStage(
                isLandscape = isLandscape,
                topReservedPadding = reservedTopHeight,
                horizontalPadding = horizontalPadding,
                onShowAll = handleClearAllFilters,
              )
            }

            uiState.viewMode == LibraryViewMode.GRID -> {
              val columns =
                when (widthClass) {
                  WindowWidthClass.COMPACT -> 2
                  WindowWidthClass.MEDIUM -> 3
                  WindowWidthClass.EXPANDED -> 4
                }

              LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().testTag("library_grid"),
                contentPadding =
                  PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = listTopPadding,
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
                  LibraryGridCard(
                    book = book,
                    onClick = onBookClick,
                  )
                }
              }
            }

            else -> {
              val shelfRows =
                remember(uiState.books, spinesPerShelf, uiState.viewMode) {
                  if (uiState.viewMode == LibraryViewMode.SHELF) uiState.books.chunked(spinesPerShelf)
                  else emptyList()
                }

              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("library_lazy_column"),
                contentPadding =
                  PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = listTopPadding,
                    bottom = DipeSpacing.xxl,
                  ),
                verticalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
              ) {
                when (uiState.viewMode) {
                  LibraryViewMode.GRID -> Unit
                  LibraryViewMode.LIST -> {
                    items(
                      items = uiState.books,
                      key = { it.id },
                      contentType = { "list_book" },
                    ) { book ->
                      val onBookClick = remember(book.id, onOpenBook) { { onOpenBook(book.id) } }
                      LibraryListRow(
                        book = book,
                        onClick = onBookClick,
                        onChangeStatus = { statusDialogBook = book },
                        onViewDetails = onBookClick,
                        onRemove = { deleteConfirmBook = book },
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
                      LibraryGalleryItem(
                        book = book,
                        onClick = onBookClick,
                      )
                    }
                  }
                }
              }
            }
          }
        },
      )

      AnimatedVisibility(
        visible = modePillMode != null,
        enter =
          if (reduceMotion) fadeIn(snap())
          else fadeIn(tween(160, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        exit =
          if (reduceMotion) fadeOut(snap())
          else fadeOut(tween(180, easing = FastOutSlowInEasing)),
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(bottom = DipeSpacing.md),
      ) {
        modePillMode?.let { activeMode ->
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 2.dp,
            modifier =
              Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("library_view_mode_pill"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = DipeSpacing.md, vertical = DipeSpacing.sm),
              horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = viewModeIcon(activeMode),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = viewModePillLabel(activeMode),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
              )
            }
          }
        }
      }
    }
  }

  if (showFiltersSheet) {
    LibraryFiltersSheet(
      filteredCount = uiState.books.size,
      genreFilter = uiState.genreFilter,
      availableGenres = uiState.availableGenres,
      sortOrder = uiState.sortOrder,
      onGenreFilterChange = onGenreFilterChange,
      onSortOrderChange = onSortOrderChange,
      onClearFilters = {
        onGenreFilterChange(null)
        onSortOrderChange(LibrarySortOrder.RECENTLY_ADDED)
      },
      onDismiss = { showFiltersSheet = false },
    )
  }

  statusDialogBook?.let { target ->
    AlertDialog(
      onDismissRequest = { statusDialogBook = null },
      shape = MaterialTheme.shapes.large,
      containerColor = DipeTheme.colors.surfaceElevated,
      title = {
        Text(
          text = stringResource(R.string.library_status_dialog_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs)) {
          ReadingStatus.entries.forEach { status ->
            val selected = target.status == status
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.small)
                  .clickable {
                    onUpdateBookStatus(target.id, status)
                    statusDialogBook = null
                  }
                  .minimumInteractiveComponentSize()
                  .padding(horizontal = DipeSpacing.sm, vertical = DipeSpacing.xs)
                  .testTag("library_status_choice_${status.name.lowercase()}"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(8.dp)
                      .clip(CircleShape)
                      .background(readingStatusAccentColor(status)),
                )
                Text(
                  text = readingStatusLabel(status),
                  style = MaterialTheme.typography.bodyLarge,
                  color =
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                )
              }
              if (selected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { statusDialogBook = null }) {
          Text(stringResource(R.string.book_cancel_action))
        }
      },
      modifier = Modifier.testTag("library_status_dialog"),
    )
  }

  deleteConfirmBook?.let { target ->
    AlertDialog(
      onDismissRequest = { deleteConfirmBook = null },
      shape = MaterialTheme.shapes.large,
      containerColor = DipeTheme.colors.surfaceElevated,
      title = {
        Text(
          text = stringResource(R.string.book_delete_confirm_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
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
        Button(
          onClick = {
            onDeleteBook(target.id)
            deleteConfirmBook = null
          },
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.heightIn(min = 48.dp).testTag("library_delete_confirm_button"),
        ) {
          Text(stringResource(R.string.book_delete_confirm_action))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { deleteConfirmBook = null },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(stringResource(R.string.book_cancel_action))
        }
      },
      modifier = Modifier.testTag("library_delete_confirm_dialog"),
    )
  }
}

@Composable
private fun LibraryHeaderReserveScaffold(
  header: @Composable () -> Unit,
  body: @Composable (reservedHeaderHeight: Dp) -> Unit,
) {
  val density = LocalDensity.current
  SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val headerPlaceables = subcompose("header", header).map { it.measure(looseConstraints) }
    val headerHeightPx = headerPlaceables.maxOfOrNull { it.height } ?: 0
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    val bodyPlaceables =
      subcompose("body") { body(headerHeightDp) }.map { it.measure(constraints) }

    layout(constraints.maxWidth, constraints.maxHeight) {
      bodyPlaceables.forEach { it.placeRelative(0, 0) }
      headerPlaceables.forEach { it.placeRelative(0, 0) }
    }
  }
}

@Composable
private fun LibraryPortraitHeader(
  horizontalPadding: Dp,
  subtitle: String,
  searchQuery: String,
  statusFilter: ReadingStatus?,
  activeGenreName: String?,
  viewMode: LibraryViewMode,
  hasActiveSheetBadge: Boolean,
  collapseFraction: Float,
  onSearchChange: (String) -> Unit,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onClearGenreFilter: () -> Unit,
  onCycleViewMode: () -> Unit,
  onOpenFiltersSheet: () -> Unit,
  onOpenAddBook: () -> Unit,
) {
  val density = LocalDensity.current
  var topSectionHeightPx by remember { mutableIntStateOf(0) }
  val topOffsetPx = collapseFraction * topSectionHeightPx.toFloat()
  val topFadeAlpha = (1f - (collapseFraction * 1.45f)).coerceIn(0f, 1f)

  val buttonSizePx = remember(density) { with(density) { 48.dp.toPx() } }
  val buttonGapPx = remember(density) { with(density) { DipeSpacing.compact.toPx() } }
  val addSlotPx = buttonSizePx + buttonGapPx
  val twoButtonsSlotPx = (buttonSizePx * 2f) + (buttonGapPx * 2f)

  Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
    // Backdrop surface whose visual bounds move cleanly up with Sticky section (zero height reflow)
    Column(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer { translationY = -topOffsetPx }
          .background(MaterialTheme.colorScheme.background),
      verticalArrangement = Arrangement.Bottom,
    ) {
      HairlineDivider()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
      // Rows 1 & 2: Title + "+ Catalogar livro" button, and "N livros · M em leitura"
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .onSizeChanged { topSectionHeightPx = it.height }
            .graphicsLayer {
              translationY = -topOffsetPx
              alpha = topFadeAlpha
            }
            .padding(horizontal = horizontalPadding)
            .padding(bottom = DipeSpacing.xs),
      ) {
        EditorialMasthead(
          title = stringResource(R.string.nav_library),
          subtitle = subtitle,
          trailingContent = {
            Button(
              onClick = {
                if (collapseFraction < 0.85f) onOpenAddBook()
              },
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
                softWrap = false,
              )
            }
          },
        )
      }

      // Sticky Rows 3 & 4 (total height <= 112.dp when pinned at top)
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .graphicsLayer { translationY = -topOffsetPx }
            .padding(top = DipeSpacing.xs, bottom = DipeSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
      ) {
        // Row 3: Search pill + [Mode] + [Filters] (+ circular [+] appearing on collapse)
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = horizontalPadding),
          contentAlignment = Alignment.CenterStart,
        ) {
          CompactEditorialSearchPill(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = stringResource(R.string.library_search_placeholder),
            clearContentDescription = stringResource(R.string.library_clear_search),
            inputTestTag = "library_search_input",
            imeAction = ImeAction.Done,
            trailingInsetPx = { twoButtonsSlotPx + (collapseFraction * addSlotPx) },
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier =
              Modifier.align(Alignment.CenterEnd)
                .graphicsLayer {
                  translationX = (1f - collapseFraction) * addSlotPx
                },
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LibraryViewModeTrigger(
              viewMode = viewMode,
              onCycleViewMode = onCycleViewMode,
            )
            LibraryFiltersTrigger(
              hasActiveSheetBadge = hasActiveSheetBadge,
              onOpenFiltersSheet = onOpenFiltersSheet,
            )
            Spacer(modifier = Modifier.size(48.dp))
          }

          Box(
            modifier =
              Modifier.align(Alignment.CenterEnd)
                .graphicsLayer {
                  alpha = collapseFraction
                  val s = 0.72f + (0.28f * collapseFraction)
                  scaleX = s
                  scaleY = s
                },
          ) {
            LibraryCompactAddButton(
              onClick = {
                if (collapseFraction >= 0.15f) onOpenAddBook()
              },
              testTag = "library_compact_add_book_button",
            )
          }
        }

        // Row 4: Horizontal status filter chips
        LibraryStatusChipsRow(
          horizontalPadding = horizontalPadding,
          statusFilter = statusFilter,
          activeGenreName = activeGenreName,
          onStatusFilterChange = onStatusFilterChange,
          onClearGenreFilter = onClearGenreFilter,
        )
      }
    }
  }
}

@Composable
private fun LibraryLandscapeHeader(
  horizontalPadding: Dp,
  searchQuery: String,
  statusFilter: ReadingStatus?,
  activeGenreName: String?,
  viewMode: LibraryViewMode,
  hasActiveSheetBadge: Boolean,
  collapseFraction: Float,
  onSearchChange: (String) -> Unit,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onClearGenreFilter: () -> Unit,
  onCycleViewMode: () -> Unit,
  onOpenFiltersSheet: () -> Unit,
  onOpenAddBook: () -> Unit,
) {
  val density = LocalDensity.current
  val titleGapPx = remember(density) { with(density) { DipeSpacing.compact.toPx() } }
  var titleSlotWidthPx by remember { mutableIntStateOf(0) }
  val titleAlpha = (1f - (collapseFraction * 1.45f)).coerceIn(0f, 1f)

  Surface(color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Row 1 (48dp): Smaller serif "Biblioteca" left (hides on scroll), flexible search center, [Mode][Filters][+] right with uniform 12dp spacing
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
          horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
          ) {
            Text(
              text = stringResource(R.string.nav_library),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              softWrap = false,
              modifier =
                Modifier.onSizeChanged { titleSlotWidthPx = it.width + titleGapPx.toInt() }
                  .graphicsLayer {
                    alpha = titleAlpha
                    translationX = -collapseFraction * (titleSlotWidthPx * 0.55f)
                  },
            )

            CompactEditorialSearchPill(
              query = searchQuery,
              onQueryChange = onSearchChange,
              placeholder = stringResource(R.string.library_search_placeholder),
              clearContentDescription = stringResource(R.string.library_clear_search),
              inputTestTag = "library_search_input",
              imeAction = ImeAction.Done,
              leadingInsetPx = { (1f - collapseFraction) * titleSlotWidthPx.toFloat() },
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LibraryViewModeTrigger(
              viewMode = viewMode,
              onCycleViewMode = onCycleViewMode,
            )
            LibraryFiltersTrigger(
              hasActiveSheetBadge = hasActiveSheetBadge,
              onOpenFiltersSheet = onOpenFiltersSheet,
            )
            LibraryCompactAddButton(
              onClick = onOpenAddBook,
              testTag = "library_add_book_button",
            )
          }
        }

        // Row 2 (48dp): Horizontal status chips with trailing contentPadding (total sticky height = 96dp <= 96dp)
        LibraryStatusChipsRow(
          horizontalPadding = horizontalPadding,
          trailingExtraPadding = DipeSpacing.md,
          statusFilter = statusFilter,
          activeGenreName = activeGenreName,
          onStatusFilterChange = onStatusFilterChange,
          onClearGenreFilter = onClearGenreFilter,
        )
      }
      HairlineDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
  }
}

@Composable
private fun LibraryStatusChipsRow(
  horizontalPadding: Dp,
  statusFilter: ReadingStatus?,
  activeGenreName: String?,
  onStatusFilterChange: (ReadingStatus?) -> Unit,
  onClearGenreFilter: () -> Unit,
  trailingExtraPadding: Dp = 0.dp,
) {
  val chipColors =
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primary,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

  val statusRowState = rememberLazyListState()
  LaunchedEffect(activeGenreName) {
    if (activeGenreName != null) {
      statusRowState.scrollToItem(0)
    }
  }

  LazyRow(
    state = statusRowState,
    contentPadding =
      PaddingValues(
        start = horizontalPadding,
        end = horizontalPadding + trailingExtraPadding,
      ),
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
        label = { Text(stringResource(R.string.library_filter_all), maxLines = 1, softWrap = false) },
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
        label = { Text(readingStatusLabel(status), maxLines = 1, softWrap = false) },
        shape = CircleShape,
        colors = chipColors,
        border = null,
        modifier = Modifier.testTag("library_filter_${status.name.lowercase()}"),
      )
    }
  }
}

@Composable
private fun LibraryViewModeTrigger(
  viewMode: LibraryViewMode,
  onCycleViewMode: () -> Unit,
) {
  val nextMode = nextViewMode(viewMode)
  Surface(
    onClick = onCycleViewMode,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.size(48.dp)
        .testTag("library_view_mode_trigger"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = viewModeIcon(nextMode),
        contentDescription = stringResource(R.string.library_view_mode_cd),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun LibraryFiltersTrigger(
  hasActiveSheetBadge: Boolean,
  onOpenFiltersSheet: () -> Unit,
) {
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
        Modifier.size(48.dp)
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
          Modifier.padding(top = 8.dp, end = 8.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
      )
    }
  }
}

@Composable
private fun LibraryCompactAddButton(
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondary,
    contentColor = MaterialTheme.colorScheme.onSecondary,
    modifier =
      Modifier.size(48.dp)
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Outlined.Add,
        contentDescription = stringResource(R.string.library_add_book),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun LibraryFilteredEmptyStage(
  isLandscape: Boolean,
  topReservedPadding: Dp,
  horizontalPadding: Dp,
  onShowAll: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(
          start = horizontalPadding,
          end = horizontalPadding,
          top = topReservedPadding + DipeSpacing.md,
          bottom = DipeSpacing.xl,
        )
        .testTag("library_filtered_empty_stage"),
    contentAlignment = Alignment.Center,
  ) {
    if (isLandscape) {
      Row(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(DipeSpacing.lg, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(72.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(30.dp),
          )
        }
        Column(
          horizontalAlignment = Alignment.Start,
          verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
        ) {
          Text(
            text = stringResource(R.string.library_empty_filtered_new_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start,
          )
          Button(
            onClick = onShowAll,
            shape = CircleShape,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
            modifier = Modifier.heightIn(min = 48.dp).testTag("library_empty_show_all_button"),
          ) {
            Text(
              text = stringResource(R.string.library_empty_filtered_show_all),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DipeSpacing.md),
      ) {
        Box(
          modifier =
            Modifier.size(76.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(32.dp),
          )
        }
        Text(
          text = stringResource(R.string.library_empty_filtered_new_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Button(
          onClick = onShowAll,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          contentPadding = PaddingValues(horizontal = DipeSpacing.lg, vertical = DipeSpacing.compact),
          modifier = Modifier.heightIn(min = 48.dp).testTag("library_empty_show_all_button"),
        ) {
          Text(
            text = stringResource(R.string.library_empty_filtered_show_all),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
        }
      }
    }
  }
}

private fun nextViewMode(current: LibraryViewMode): LibraryViewMode =
  when (current) {
    LibraryViewMode.GRID -> LibraryViewMode.LIST
    LibraryViewMode.LIST -> LibraryViewMode.SHELF
    LibraryViewMode.SHELF -> LibraryViewMode.GALLERY
    LibraryViewMode.GALLERY -> LibraryViewMode.GRID
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
private fun viewModePillLabel(mode: LibraryViewMode): String =
  when (mode) {
    LibraryViewMode.GRID -> stringResource(R.string.library_view_grid)
    LibraryViewMode.LIST -> stringResource(R.string.library_view_list)
    LibraryViewMode.SHELF -> stringResource(R.string.library_view_shelf)
    LibraryViewMode.GALLERY -> stringResource(R.string.library_view_mode_pill_gallery)
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
      animateEntrance = true,
      entranceKey = "lib_grid_${book.id}",
      bottomProgressPercent = if (showActiveProgress) book.activeProgressPercent else null,
      widthHintDp = 160.dp,
      modifier = Modifier.fillMaxWidth(),
    )
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

      val statusText =
        if (showActiveProgress) {
          "${readingStatusLabel(book.status)} · ${LocaleManager.formatPercent(book.activeProgressPercent!!, language)}"
        } else {
          readingStatusLabel(book.status)
        }
      LibraryGridStatusAndLanguageRow(
        statusText = statusText,
        statusColor = statusColor,
        languageTag = book.primaryEdition?.language,
      )

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
private fun LibraryGridStatusAndLanguageRow(
  statusText: String,
  statusColor: androidx.compose.ui.graphics.Color,
  languageTag: String?,
) {
  val density = LocalDensity.current
  val gapPx = remember(density) { with(density) { 6.dp.roundToPx() } }

  SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val unboundedConstraints = Constraints(minWidth = 0, maxWidth = Constraints.Infinity)

    val statusUnbounded =
      subcompose("status_full") {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
          Text(
            text = statusText,
            style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
          )
        }
      }.first().measure(unboundedConstraints)

    val badgePlaceable =
      subcompose("badge") {
        MinimalLanguageBadge(languageTag = languageTag)
      }.firstOrNull()?.measure(looseConstraints)

    val fitsWithBadge =
      badgePlaceable != null &&
        statusUnbounded.width + gapPx + badgePlaceable.width <= constraints.maxWidth

    if (fitsWithBadge) {
      val rowHeight = maxOf(statusUnbounded.height, badgePlaceable!!.height)
      layout(constraints.maxWidth, rowHeight) {
        statusUnbounded.placeRelative(0, (rowHeight - statusUnbounded.height) / 2)
        badgePlaceable.placeRelative(
          x = constraints.maxWidth - badgePlaceable.width,
          y = (rowHeight - badgePlaceable.height) / 2,
        )
      }
    } else {
      val wrappedStatus =
        subcompose("status_wrapped") {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
            Text(
              text = statusText,
              style = LocalDipeTypography.current.monoMetadata.copy(fontSize = 11.5.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
            )
          }
        }.first().measure(looseConstraints)

      layout(constraints.maxWidth, wrappedStatus.height) {
        wrappedStatus.placeRelative(0, 0)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryListRow(
  book: CatalogBook,
  onClick: () -> Unit,
  onChangeStatus: () -> Unit,
  onViewDetails: () -> Unit,
  onRemove: () -> Unit,
) {
  val language = LocalAppLanguage.current
  val statusColor = readingStatusAccentColor(book.status)
  val showActiveProgress =
    (book.status == ReadingStatus.READING || book.status == ReadingStatus.PAUSED) &&
      book.activeProgressPercent != null
  var menuExpanded by remember { mutableStateOf(false) }

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
          animateEntrance = true,
          entranceKey = "lib_list_${book.id}",
          widthHintDp = 50.dp,
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

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DipeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DipeSpacing.xxs),
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
                maxLines = 2,
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
        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("library_list_more_${book.id}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.MoreVert,
              contentDescription = stringResource(R.string.cd_book_actions),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = DipeTheme.colors.surfaceElevated,
          ) {
            DropdownMenuItem(
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.SwapHoriz,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              text = { Text(stringResource(R.string.library_row_action_status)) },
              onClick = {
                menuExpanded = false
                onChangeStatus()
              },
              modifier = Modifier.testTag("library_list_action_status_${book.id}"),
            )
            DropdownMenuItem(
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              text = { Text(stringResource(R.string.library_row_action_details)) },
              onClick = {
                menuExpanded = false
                onViewDetails()
              },
              modifier = Modifier.testTag("library_list_action_details_${book.id}"),
            )
            DropdownMenuItem(
              leadingIcon = {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp),
                )
              },
              text = {
                Text(
                  text = stringResource(R.string.library_row_action_remove),
                  color = MaterialTheme.colorScheme.error,
                )
              },
              onClick = {
                menuExpanded = false
                onRemove()
              },
              modifier = Modifier.testTag("library_list_action_remove_${book.id}"),
            )
          }
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
          elevated = true,
          animateEntrance = true,
          entranceKey = "lib_shelf_${book.id}",
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
        animateEntrance = true,
        entranceKey = "lib_gallery_${book.id}",
        widthHintDp = 196.dp,
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
