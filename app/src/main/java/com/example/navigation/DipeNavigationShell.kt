package com.example.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.designsystem.DipeSpacing
import com.example.core.designsystem.LocalDipeMotion
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
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryViewModel
import com.example.feature.profile.EditProfileSheet
import com.example.feature.profile.ProfileScreen
import com.example.feature.profile.ProfileViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.statistics.StatisticsScreen
import com.example.feature.statistics.StatisticsViewModel

@Composable
fun DipeNavigationShell(
  container: AppContainer,
  modifier: Modifier = Modifier,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val motion = LocalDipeMotion.current

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

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (isTopLevel && !useRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
          ) {
            val navLabelStyle = com.example.core.designsystem.LocalDipeTypography.current.navBarLabel
            TopLevelDestination.entries.forEach { dest ->
              val selected = currentDestination?.hasRoute(dest.route::class) == true
              val label = stringResource(dest.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTopLevel(dest) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = {
                  Text(
                    text = label,
                    style = navLabelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                  )
                },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                  ),
                modifier = Modifier.testTag("nav_tab_${dest.routeKey}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isTopLevel && useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxHeight().testTag("dipe_navigation_rail"),
          ) {
            val navLabelStyle = com.example.core.designsystem.LocalDipeTypography.current.navBarLabel
            TopLevelDestination.entries.forEach { dest ->
              val selected = currentDestination?.hasRoute(dest.route::class) == true
              val label = stringResource(dest.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTopLevel(dest) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = {
                  Text(
                    text = label,
                    style = navLabelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                  )
                },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                  ),
                modifier =
                  Modifier.padding(vertical = DipeSpacing.xs).testTag("nav_rail_${dest.routeKey}"),
              )
            }
          }
          VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                },
                onUpdateProgressByPercent = { book, percent, note ->
                  homeViewModel.updateProgressByPercent(book = book, percent = percent, note = note)
                },
                onCompleteReading = { book -> homeViewModel.completeReading(book) },
              )
            }

            composable<LibraryRoute> {
              val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
              LibraryScreen(
                uiState = libraryState,
                widthClass = widthClass,
                onSearchChange = libraryViewModel::setSearchQuery,
                onStatusFilterChange = libraryViewModel::setStatusFilter,
                onSortOrderChange = libraryViewModel::setSortOrder,
                onViewModeChange = libraryViewModel::setViewMode,
                onOpenAddBook = { showAddBookSheet = true },
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
                onToggleFavorite = libraryViewModel::toggleFavorite,
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
                onSwitchActiveCopy = bookDetailViewModel::switchActiveCopy,
              )
            }
          }
        }
      }
    }
  }

  if (showAddBookSheet) {
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
