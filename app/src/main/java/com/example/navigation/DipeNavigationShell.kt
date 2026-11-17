package com.example.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.core.designsystem.components.HairlineDivider
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
          HomeViewModel(it.libraryRepository, it.diaryRepository, it.statisticsRepository)
        }
    )
  val libraryViewModel: LibraryViewModel =
    viewModel(
      factory =
        dipeFactory(container) {
          LibraryViewModel(it.libraryRepository, it.diaryRepository, it.preferencesRepository)
        }
    )
  val diaryViewModel: DiaryViewModel =
    viewModel(
      factory = dipeFactory(container) { DiaryViewModel(it.diaryRepository, it.libraryRepository) }
    )
  val statisticsViewModel: StatisticsViewModel =
    viewModel(factory = dipeFactory(container) { StatisticsViewModel(it.statisticsRepository) })
  val profileViewModel: ProfileViewModel =
    viewModel(
      factory =
        dipeFactory(container) { ProfileViewModel(it.preferencesRepository, it.libraryRepository) }
    )

  var showAddBookSheet by remember { mutableStateOf(false) }
  var showNewDiarySheet by remember { mutableStateOf(false) }
  var showEditProfileSheet by remember { mutableStateOf(false) }

  val isTopLevel =
    currentDestination?.let { dest ->
      TopLevelDestination.entries.any { top -> dest.hasRoute(top.route::class) }
    } ?: true

  fun navigateToTopLevel(destination: TopLevelDestination) {
    navController.navigate(destination.route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val widthClass = WindowWidthClass.fromWidth(maxWidth)
    val useRail = widthClass != WindowWidthClass.COMPACT

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (isTopLevel && !useRail) {
          Column(modifier = Modifier.fillMaxWidth()) {
            HairlineDivider()
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              tonalElevation = 0.dp,
            ) {
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
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.onBackground,
                      selectedTextColor = MaterialTheme.colorScheme.onBackground,
                      indicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                  modifier = Modifier.testTag("nav_tab_${dest.routeKey}"),
                )
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isTopLevel && useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxHeight().testTag("dipe_navigation_rail"),
          ) {
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
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                modifier =
                  Modifier.padding(vertical = DipeSpacing.xs).testTag("nav_rail_${dest.routeKey}"),
              )
            }
          }
          VerticalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          val enterSpec = motion.standardTween<Float>(180)
          val exitSpec = motion.standardTween<Float>(160)

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
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenLibrary = { navigateToTopLevel(TopLevelDestination.LIBRARY) },
                onCatalogVolume = { showAddBookSheet = true },
                onWriteDiaryEntry = { showNewDiarySheet = true },
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
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

            composable<DiaryRoute> {
              val diaryState by diaryViewModel.uiState.collectAsStateWithLifecycle()
              DiaryScreen(
                uiState = diaryState,
                widthClass = widthClass,
                onOpenNewEntry = { showNewDiarySheet = true },
                onDeleteEntry = diaryViewModel::deleteEntry,
                onOpenBook = { workId -> navController.navigate(BookDetailRoute(workId)) },
              )
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
                      )
                    },
                )
              val bookDetailState by bookDetailViewModel.uiState.collectAsStateWithLifecycle()
              BookDetailScreen(
                uiState = bookDetailState,
                widthClass = widthClass,
                onToggleFavorite = bookDetailViewModel::toggleFavorite,
                onUpdateStatus = bookDetailViewModel::updateStatus,
                onConfirmDelete = {
                  bookDetailViewModel.deleteBook { navController.popBackStack() }
                },
                onNavigateBack = { navController.popBackStack() },
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
      onSave = { title, author, pages, year, editionNote, status ->
        libraryViewModel.addBook(
          title = title,
          author = author,
          totalPages = pages,
          publicationYear = year,
          editionNote = editionNote,
          status = status,
        ) {
          showAddBookSheet = false
        }
      },
    )
  }

  if (showNewDiarySheet) {
    val diaryState by diaryViewModel.uiState.collectAsStateWithLifecycle()
    NewDiaryEntrySheet(
      availableBooks = diaryState.availableBooks,
      onDismiss = { showNewDiarySheet = false },
      onSave = { content, workId ->
        diaryViewModel.addEntry(content = content, workId = workId) {
          showNewDiarySheet = false
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
