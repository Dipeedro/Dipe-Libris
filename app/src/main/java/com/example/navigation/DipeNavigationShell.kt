package com.example.navigation

import android.app.Activity
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.designsystem.DipeElevation
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.DipeTheme
import com.example.core.designsystem.TopSquircleShape
import com.example.core.designsystem.WindowWidthClass
import com.example.core.di.AppContainer
import com.example.core.ui.dipeFactory
import com.example.feature.diary.DiaryScreen
import com.example.feature.diary.DiaryViewModel
import com.example.feature.diary.NewDiaryEntrySheet
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.library.AddBookSheet
import com.example.feature.library.BookDetailScreen
import com.example.feature.library.BookDetailViewModel
import com.example.feature.library.BookReviewScreen
import com.example.feature.library.BookReviewViewModel
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchViewModel
import com.example.feature.library.IsbnScannerScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryViewModel
import com.example.feature.profile.EditProfileSheet
import com.example.feature.profile.ProfileScreen
import com.example.feature.profile.ProfileViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.statistics.StatisticsScreen
import com.example.feature.statistics.StatisticsViewModel
import kotlinx.coroutines.launch

@Composable
fun DipeNavigationShell(
  container: AppContainer,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val motion = DipeTheme.motion
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val progressUpdatedMessage = stringResource(R.string.progress_updated_snackbar)
  val undoLabel = stringResource(R.string.action_undo)

  val showProgressUpdatedSnackbar: () -> Unit = {
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(
        message = progressUpdatedMessage,
        duration = SnackbarDuration.Short,
      )
    }
  }

  val showCompletionUndoSnackbar: (bookTitle: String, onUndo: () -> Unit) -> Unit =
    { bookTitle, onUndo ->
      val message = context.resources.getString(R.string.progress_completed_snackbar, bookTitle)
      scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val result =
          snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
          )
        if (result == SnackbarResult.ActionPerformed) {
          onUndo()
        }
      }
    }

  val homeViewModel: HomeViewModel =
    viewModel(
      factory =
        dipeFactory(container) {
          HomeViewModel(
            libraryRepository = it.libraryRepository,
            statisticsRepository = it.statisticsRepository,
            readingJourneyRepository = it.readingJourneyRepository,
          )
        }
    )
  val libraryViewModel: LibraryViewModel =
    viewModel(
      factory =
        dipeFactory(container) {
          LibraryViewModel(
            libraryRepository = it.libraryRepository,
            preferencesRepository = it.preferencesRepository,
            coverStorageManager = it.coverStorageManager,
            metadataUpdateRepository = it.metadataUpdateRepository,
          )
        }
    )
  val statisticsViewModel: StatisticsViewModel =
    viewModel(factory = dipeFactory(container) { StatisticsViewModel(it.statisticsRepository) })
  val profileViewModel: ProfileViewModel =
    viewModel(
      factory =
        dipeFactory(container) { ProfileViewModel(it.preferencesRepository, it.libraryRepository) }
    )

  var showAddBookSheet by remember { mutableStateOf(false) }
  var showEditProfileSheet by remember { mutableStateOf(false) }

  val isTopLevel =
    currentDestination?.let { dest ->
      TopLevelDestination.entries.any { top -> dest.hasRoute(top.route::class) }
    } ?: true

  val navigateToTopLevel: (TopLevelDestination) -> Unit =
    remember(navController) {
      { destination ->
        navController.navigate(destination.route) {
          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }
    }

  val enterSpec = remember(motion) { motion.standardTween<Float>(160) }
  val exitSpec = remember(motion) { motion.standardTween<Float>(140) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val widthClass = WindowWidthClass.fromWidth(maxWidth)
    val useRail = widthClass != WindowWidthClass.COMPACT
    val isHomeRoute = currentDestination?.hasRoute(HomeRoute::class) ?: true
    val hasTerracottaBottomBar = isTopLevel && !useRail
    val view = LocalView.current
    val navBarContainerColor = DipeTheme.colors.navBarContainer
    val canvasColor = DipeTheme.colors.canvas
    val isDarkCanvas = canvasColor.luminance() < 0.5f

    if (!view.isInEditMode) {
      LaunchedEffect(view, hasTerracottaBottomBar, isDarkCanvas, canvasColor, navBarContainerColor) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
          val insetsController = WindowCompat.getInsetsController(window, view)
          insetsController.isAppearanceLightNavigationBars =
            if (hasTerracottaBottomBar) false else !isDarkCanvas
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
          } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor =
              (if (hasTerracottaBottomBar) navBarContainerColor else canvasColor).toArgb()
          }
        }
      }
    }

    Scaffold(
      containerColor = if (isHomeRoute) DipeTheme.colors.homeCanvas else DipeTheme.colors.canvas,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = {
        SnackbarHost(hostState = snackbarHostState) { data ->
          Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = DipeTheme.colors.inkActionContainer,
            contentColor = DipeTheme.colors.inkActionContent,
            actionColor = DipeTheme.colors.inkActionContent,
          )
        }
      },
      bottomBar = {
        if (isTopLevel && !useRail) {
          EditorialBottomNavBar(
            currentDestination = currentDestination,
            onNavigate = navigateToTopLevel,
          )
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isTopLevel && useRail) {
          NavigationRail(
            containerColor = DipeTheme.colors.navBarContainer,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxHeight().testTag("dipe_navigation_rail"),
          ) {
            Spacer(modifier = Modifier.height(DipeSpacing.md))
            TopLevelDestination.entries.forEach { dest ->
              val selected = currentDestination?.hasRoute(dest.route::class) == true
              val label = stringResource(dest.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTopLevel(dest) },
                icon = {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DipeSpacing.xs),
                  ) {
                    Box(
                      modifier =
                        Modifier.width(2.5.dp)
                          .height(18.dp)
                          .background(
                            color =
                              if (selected) DipeTheme.colors.navBarContent
                              else Color.Transparent,
                            shape = CircleShape,
                          )
                    )
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = label,
                      modifier = Modifier.size(22.dp),
                    )
                  }
                },
                label = {
                  Text(
                    text = label,
                    style =
                      if (selected) DipeTheme.typography.navBarLabelActive
                      else DipeTheme.typography.navBarLabel,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = DipeTheme.colors.navBarContent,
                    selectedTextColor = DipeTheme.colors.navBarContent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = DipeTheme.colors.navBarContentMuted,
                    unselectedTextColor = DipeTheme.colors.navBarContentMuted,
                  ),
                modifier =
                  Modifier.padding(vertical = DipeSpacing.xs).testTag("nav_rail_${dest.routeKey}"),
              )
            }
          }
          VerticalDivider(thickness = 0.5.dp, color = DipeTheme.colors.dividerHairline)
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { fadeIn(animationSpec = enterSpec) },
            exitTransition = { fadeOut(animationSpec = exitSpec) },
            popEnterTransition = { fadeIn(animationSpec = enterSpec) },
            popExitTransition = { fadeOut(animationSpec = exitSpec) },
          ) {
            composable<HomeRoute> {
              val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
              HomeScreen(
                uiState = homeState,
                widthClass = widthClass,
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
                onUpdateProgressByPage = { book, page, note ->
                  homeViewModel.updateProgressByPage(book = book, page = page, note = note)
                  showProgressUpdatedSnackbar()
                },
                onUpdateProgressByPercent = { book, percent, note ->
                  homeViewModel.updateProgressByPercent(book = book, percent = percent, note = note)
                  showProgressUpdatedSnackbar()
                },
                onCompleteReading = { book -> homeViewModel.completeReading(book) },
                onCompleteReadingWithUndo = { book, note ->
                  homeViewModel.completeReadingWithUndo(book = book, note = note) { snapshot ->
                    showCompletionUndoSnackbar(book.title) {
                      homeViewModel.undoCompletion(snapshot)
                    }
                  }
                },
              )
            }

            composable<LibraryRoute> {
              val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
              LibraryScreen(
                uiState = libraryState,
                widthClass = widthClass,
                onSearchChange = libraryViewModel::setSearchQuery,
                onStatusFilterChange = libraryViewModel::setStatusFilter,
                onGenreFilterChange = libraryViewModel::setGenreFilter,
                onSortOrderChange = libraryViewModel::setSortOrder,
                onViewModeChange = libraryViewModel::setViewMode,
                onOpenAddBook = { navController.navigate(BookSearchRoute()) },
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
                onToggleFavorite = libraryViewModel::toggleFavorite,
                onCheckUpdates = {
                  libraryViewModel.checkLibraryMetadataUpdates { summary ->
                    val message =
                      if (summary.newSuggestionsCount > 0) {
                        context.resources.getString(
                          R.string.library_updates_found,
                          summary.newSuggestionsCount,
                        )
                      } else {
                        context.resources.getString(
                          R.string.library_updates_checked_none,
                          summary.booksChecked,
                        )
                      }
                    scope.launch {
                      snackbarHostState.currentSnackbarData?.dismiss()
                      snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short,
                      )
                    }
                  }
                },
              )
            }

            composable<BookSearchRoute> { backStackEntry ->
              val route = backStackEntry.toRoute<BookSearchRoute>()
              val searchViewModel: BookSearchViewModel =
                viewModel(
                  factory =
                    dipeFactory(container) {
                      BookSearchViewModel(
                        initialQuery = route.initialQuery,
                        orchestrator = it.hybridSearchOrchestrator,
                        libraryRepository = it.libraryRepository,
                        preferencesRepository = it.preferencesRepository,
                        draftStore = it.bibliographicDraftStore,
                      )
                    }
                )
              androidx.compose.runtime.LaunchedEffect(navBackStackEntry) {
                searchViewModel.onResumeCheckScannedIsbn()
              }
              val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
              BookSearchScreen(
                uiState = searchState,
                widthClass = widthClass,
                onQueryChange = searchViewModel::setQuery,
                onSubmitSearch = { searchViewModel.submitSearch(expandSecondarySources = false) },
                onExpandSecondarySources = {
                  searchViewModel.submitSearch(expandSecondarySources = true)
                },
                onOpenScanner = { navController.navigate(IsbnScannerRoute) },
                onManualCatalog = {
                  searchViewModel.startManualRegistration {
                    navController.navigate(BookReviewRoute(mode = "manual"))
                  }
                },
                onToggleGroupExpanded = searchViewModel::toggleGroupExpanded,
                onLoadMoreEditions = searchViewModel::loadMoreEditionsForWork,
                onSelectEdition = { candidate ->
                  searchViewModel.selectEditionCandidate(candidate) {
                    navController.navigate(BookReviewRoute(mode = "imported"))
                  }
                },
                onOpenExistingBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
                onDismissDuplicate = searchViewModel::dismissDuplicatePrompt,
                onDuplicateAddCopy = { candidate, editionId, workId ->
                  searchViewModel.proceedAddCopyToExistingEdition(
                    candidate = candidate,
                    existingEditionId = editionId,
                    existingWorkId = workId,
                  ) {
                    navController.navigate(BookReviewRoute(mode = "add_copy", targetWorkId = workId))
                  }
                },
                onDuplicateAddEdition = { candidate, workId ->
                  searchViewModel.proceedAddEditionToExistingWork(
                    candidate = candidate,
                    existingWorkId = workId,
                  ) {
                    navController.navigate(
                      BookReviewRoute(mode = "add_edition", targetWorkId = workId)
                    )
                  }
                },
                onDuplicateSaveSeparately = { candidate ->
                  searchViewModel.proceedSaveAsSeparateWork(candidate) {
                    navController.navigate(BookReviewRoute(mode = "imported"))
                  }
                },
                onNavigateBack = { navController.popBackStack() },
                onLoadMoreResults = searchViewModel::loadNextPage,
                onToggleSecondaryLanguages = searchViewModel::toggleSecondaryLanguagesExpanded,
                onToggleDerivativeWorks = searchViewModel::toggleDerivativeWorksExpanded,
                onToggleGroupSecondaryEditions = searchViewModel::toggleGroupSecondaryEditionsExpanded,
                onShowMoreLocalEditions = searchViewModel::expandLocalEditionsWindow,
              )
            }

            composable<IsbnScannerRoute> {
              IsbnScannerScreen(
                widthClass = widthClass,
                onIsbnDetected = { detectedIsbn ->
                  container.bibliographicDraftStore.setScannedIsbn(detectedIsbn)
                  navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() },
              )
            }

            composable<BookReviewRoute> { backStackEntry ->
              val route = backStackEntry.toRoute<BookReviewRoute>()
              val reviewViewModel: BookReviewViewModel =
                viewModel(
                  factory =
                    dipeFactory(container) {
                      BookReviewViewModel(
                        modeArg = route.mode,
                        targetWorkIdArg = route.targetWorkId.takeIf { it > 0L },
                        prefillArg = route.prefill,
                        libraryRepository = it.libraryRepository,
                        coverStorageManager = it.coverStorageManager,
                        preferencesRepository = it.preferencesRepository,
                        draftStore = it.bibliographicDraftStore,
                      )
                    }
                )
              val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
              BookReviewScreen(
                uiState = reviewState,
                widthClass = widthClass,
                viewModel = reviewViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSavedWork = { savedWorkId ->
                  navController.navigate(BookDetailRoute(savedWorkId)) {
                    popUpTo(LibraryRoute) { inclusive = false }
                    launchSingleTop = true
                  }
                },
              )
            }

            composable<BookDiaryRoute> { backStackEntry ->
              val route = backStackEntry.toRoute<BookDiaryRoute>()
              val diaryViewModel: DiaryViewModel =
                viewModel(
                  key = "book_diary_${route.workId}",
                  factory =
                    dipeFactory(container) {
                      DiaryViewModel(
                        workId = route.workId,
                        diaryRepository = it.diaryRepository,
                        libraryRepository = it.libraryRepository,
                      )
                    },
                )
              val diaryState by diaryViewModel.uiState.collectAsStateWithLifecycle()
              var showNewDiarySheet by remember { mutableStateOf(false) }

              DiaryScreen(
                uiState = diaryState,
                widthClass = widthClass,
                onOpenNewEntry = { showNewDiarySheet = true },
                onDeleteEntry = diaryViewModel::deleteEntry,
                onNavigateBack = { navController.popBackStack() },
              )

              val loadedBookTitle =
                (diaryState as? com.example.feature.diary.DiaryUiState.Loaded)?.book?.title
              if (showNewDiarySheet && loadedBookTitle != null) {
                NewDiaryEntrySheet(
                  bookTitle = loadedBookTitle,
                  onDismiss = { showNewDiarySheet = false },
                  onSave = { content ->
                    diaryViewModel.addEntry(content = content) {
                      showNewDiarySheet = false
                    }
                  },
                )
              }
            }

            composable<StatisticsRoute> {
              val snapshot by statisticsViewModel.snapshot.collectAsStateWithLifecycle()
              StatisticsScreen(
                snapshot = snapshot,
                widthClass = widthClass,
              )
            }

            composable<ProfileRoute> {
              val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
              ProfileScreen(
                uiState = profileState,
                widthClass = widthClass,
                onEditIdentity = { showEditProfileSheet = true },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
              )
            }

            composable<SettingsRoute> {
              val settingsViewModel: SettingsViewModel =
                viewModel(
                  factory = dipeFactory(container) { SettingsViewModel(it.preferencesRepository) }
                )
              val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()
              SettingsScreen(
                preferences = prefs,
                widthClass = widthClass,
                onSelectLanguage = settingsViewModel::setLanguage,
                onSelectTheme = settingsViewModel::setThemeMode,
                onSelectTypography = settingsViewModel::setTypographyPreset,
                onSelectDefaultLibraryView = settingsViewModel::setDefaultLibraryViewMode,
                onToggleReduceMotion = settingsViewModel::setReduceMotion,
                onNavigateBack = { navController.popBackStack() },
              )
            }

            composable<BookDetailRoute> { backStackEntry ->
              val route = backStackEntry.toRoute<BookDetailRoute>()
              val bookDetailViewModel: BookDetailViewModel =
                viewModel(
                  key = "book_detail_${route.workId}",
                  factory =
                    dipeFactory(container) {
                      BookDetailViewModel(
                        workId = route.workId,
                        libraryRepository = it.libraryRepository,
                        diaryRepository = it.diaryRepository,
                        readingJourneyRepository = it.readingJourneyRepository,
                        coverStorageManager = it.coverStorageManager,
                        metadataUpdateRepository = it.metadataUpdateRepository,
                        preferencesRepository = it.preferencesRepository,
                      )
                    },
                )
              val bookDetailState by bookDetailViewModel.uiState.collectAsStateWithLifecycle()
              BookDetailScreen(
                uiState = bookDetailState,
                widthClass = widthClass,
                onToggleFavorite = bookDetailViewModel::toggleFavorite,
                onUpdateStatus = bookDetailViewModel::updateStatus,
                onOpenDiary = { navController.navigate(BookDiaryRoute(route.workId)) },
                onConfirmDelete = {
                  bookDetailViewModel.deleteBook { navController.popBackStack() }
                },
                onNavigateBack = { navController.popBackStack() },
                onSelectEdition = bookDetailViewModel::selectEdition,
                onUpdateWorkMetadata = { newTitle, newAuthor ->
                  bookDetailViewModel.updateWorkMetadata(title = newTitle, author = newAuthor)
                },
                onAddEdition = { sub ->
                  bookDetailViewModel.addEdition(
                    publisherOrNote = sub.publisherOrNote,
                    publicationYear = sub.publicationYear,
                    totalPages = sub.totalPages,
                    isbn = sub.isbn,
                    pickedCoverUri = sub.pickedCoverUri,
                    language = sub.language,
                    format = sub.format,
                    translator = sub.translator,
                    setAsPrimary = sub.isPrimary,
                    alsoCreateCopy = true,
                    copyMediaType = sub.initialCopyMediaType,
                    copyLabel = sub.initialCopyLabel,
                  )
                },
                onUpdateEdition = { edition, pickedCoverUri, removeCover ->
                  bookDetailViewModel.updateEdition(
                    edition = edition,
                    pickedCoverUri = pickedCoverUri,
                    removeCover = removeCover,
                  )
                },
                onSetPrimaryEdition = bookDetailViewModel::setPrimaryEdition,
                onDeleteEdition = { editionId -> bookDetailViewModel.deleteEdition(editionId) },
                onAddCopy = { editionId, sub ->
                  bookDetailViewModel.addCopy(
                    editionId = editionId,
                    mediaType = sub.mediaType,
                    label = sub.label,
                    physicalFormat = sub.physicalFormat,
                    locationOrPlatform = sub.locationOrPlatform,
                    personalNote = sub.personalNote,
                    setAsActiveReadingCopy = sub.setAsActiveReadingCopy,
                  )
                },
                onUpdateCopy = { copy, setAsActive ->
                  bookDetailViewModel.updateCopy(copy = copy, setAsActiveReadingCopy = setAsActive)
                },
                onDeleteCopy = { copyId -> bookDetailViewModel.deleteCopy(copyId) },
                onStartJourney = { copyId, note ->
                  bookDetailViewModel.startJourney(copyId = copyId, notes = note)
                },
                onStartReread = { copyId, note ->
                  bookDetailViewModel.startReread(copyId = copyId, notes = note)
                },
                onUpdateProgressByPage = { sessionId, page, note ->
                  bookDetailViewModel.updateProgressByPage(
                    sessionId = sessionId,
                    page = page,
                    note = note,
                  )
                },
                onUpdateProgressByPercent = { sessionId, percent, note ->
                  bookDetailViewModel.updateProgressByPercent(
                    sessionId = sessionId,
                    percent = percent,
                    note = note,
                  )
                },
                onPauseJourney = bookDetailViewModel::pauseJourney,
                onResumeJourney = bookDetailViewModel::resumeJourney,
                onCompleteJourney = bookDetailViewModel::completeJourney,
                onCompleteJourneyWithUndo = { sessionId, note ->
                  val loadedTitle =
                    (bookDetailState as? com.example.feature.library.BookDetailUiState.Loaded)
                      ?.detail
                      ?.work
                      ?.title
                      .orEmpty()
                  bookDetailViewModel.completeJourneyWithUndo(sessionId = sessionId, note = note) {
                    snapshot ->
                    showCompletionUndoSnackbar(loadedTitle) {
                      bookDetailViewModel.restoreCompletion(snapshot)
                    }
                  }
                },
                onProgressUpdated = showProgressUpdatedSnackbar,
                onSwitchActiveCopy = bookDetailViewModel::switchActiveCopy,
                onCheckMetadataUpdates = {
                  bookDetailViewModel.checkMetadataUpdates { foundCount ->
                    val message =
                      if (foundCount > 0) {
                        context.resources.getString(R.string.library_updates_found, foundCount)
                      } else {
                        context.resources.getString(R.string.library_updates_checked_none, 1)
                      }
                    scope.launch {
                      snackbarHostState.currentSnackbarData?.dismiss()
                      snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Short,
                      )
                    }
                  }
                },
                onAcceptSuggestion = bookDetailViewModel::acceptMetadataSuggestion,
                onRejectSuggestion = bookDetailViewModel::rejectMetadataSuggestion,
              )
            }
          }
        }
      }
    }
  }

  AnimatedVisibility(
    visible = showAddBookSheet,
    enter = fadeIn(animationSpec = enterSpec),
    exit = fadeOut(animationSpec = exitSpec),
  ) {
    AddBookSheet(
      onDismiss = { showAddBookSheet = false },
      onSave = { submission ->
        libraryViewModel.addBook(
          title = submission.title,
          author = submission.author,
          totalPages = submission.totalPages,
          publicationYear = submission.publicationYear,
          editionNote = submission.editionNote,
          status = submission.status,
          isbn = submission.isbn,
          pickedCoverUri = submission.pickedCoverUri,
          language = submission.language,
          format = submission.format,
          translator = submission.translator,
          copyMediaType = submission.copyMediaType,
          copyLabel = submission.copyLabel,
          copyLocation = submission.copyLocation,
        ) {
          showAddBookSheet = false
        }
      },
    )
  }

  if (showEditProfileSheet) {
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    EditProfileSheet(
      initialName = profileState.preferences.readerName,
      initialBio = profileState.preferences.readerBio,
      onDismiss = { showEditProfileSheet = false },
      onSave = { name, bio ->
        profileViewModel.updateIdentity(name = name, bio = bio) {
          showEditProfileSheet = false
        }
      },
    )
  }
}

@Composable
private fun EditorialBottomNavBar(
  currentDestination: NavDestination?,
  onNavigate: (TopLevelDestination) -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = DipeTheme.motion
  val squircleTopShape = remember { TopSquircleShape(cornerRadius = 24.dp) }
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = squircleTopShape,
    color = DipeTheme.colors.navBarContainer,
    tonalElevation = DipeElevation.flat,
    shadowElevation = DipeElevation.flat,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .windowInsetsPadding(
            WindowInsets.navigationBars.union(
              WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
          )
          .padding(
            start = DipeSpacing.md,
            end = DipeSpacing.md,
            top = DipeSpacing.md,
            bottom = DipeSpacing.compact,
          )
          .heightIn(min = 58.dp)
          .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TopLevelDestination.entries.forEach { dest ->
        val selected = currentDestination?.hasRoute(dest.route::class) == true
        val label = stringResource(dest.labelRes)
        val indicatorFraction by
          animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motion.standardTween(180),
            label = "nav_tab_indicator_${dest.routeKey}",
          )
        val contentColor =
          if (selected) DipeTheme.colors.navBarContent else DipeTheme.colors.navBarContentMuted
        val labelStyle =
          if (selected) DipeTheme.typography.navBarLabelActive
          else DipeTheme.typography.navBarLabel

        Column(
          modifier =
            Modifier.weight(1f)
              .defaultMinSize(minWidth = 48.dp, minHeight = 58.dp)
              .clip(MaterialTheme.shapes.medium)
              .selectable(
                selected = selected,
                onClick = { onNavigate(dest) },
                role = Role.Tab,
              )
              .padding(horizontal = DipeSpacing.xs, vertical = DipeSpacing.xs)
              .testTag("nav_tab_${dest.routeKey}"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        ) {
          Box(
            modifier = Modifier.height(2.5.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            if (indicatorFraction > 0.01f) {
              Box(
                modifier =
                  Modifier.width(26.dp * indicatorFraction)
                    .height(2.5.dp)
                    .background(
                      color = DipeTheme.colors.navBarContent,
                      shape = CircleShape,
                    )
              )
            }
          }
          Icon(
            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
          )
          Text(
            text = label,
            style = labelStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
