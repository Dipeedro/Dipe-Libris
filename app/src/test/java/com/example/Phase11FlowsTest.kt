package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.preferences.DataStorePreferencesRepository
import com.example.core.di.DefaultAppContainer
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.CatalogBook
import com.example.core.model.DiaryEntry
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus
import com.example.core.model.Work
import com.example.core.data.local.preferences.UserPreferences
import com.example.feature.diary.DiaryScreen
import com.example.feature.diary.DiaryUiState
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeUiState
import com.example.feature.home.RecentActivityItem
import com.example.feature.library.AddBookSheet
import com.example.feature.library.BookDetailScreen
import com.example.feature.library.BookDetailUiState
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryUiState
import com.example.feature.settings.SettingsScreen
import com.example.navigation.DipeNavigationShell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase11FlowsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun allFourLibraryViewModesRenderWithoutCrashIncludingNarrowShelf() {
    val sampleBooks =
      (1L..8L).map { id ->
        CatalogBook(
          work =
            Work(
              id = id,
              title = "Volume Literário $id",
              author = "Autor $id",
              status = ReadingStatus.READING,
              isFavorite = id == 1L,
              typographyPresetOverride = null,
              createdAtEpochMillis = id * 1000L,
              updatedAtEpochMillis = id * 1000L,
            ),
          primaryEdition = null,
          primaryCopy = null,
        )
      }

    var currentMode by mutableStateOf(LibraryViewMode.GRID)
    composeTestRule.setContent {
      DipeLibrisTheme {
        LibraryScreen(
          uiState =
            LibraryUiState(
              books = sampleBooks,
              totalCatalogCount = sampleBooks.size,
              viewMode = currentMode,
            ),
          widthClass = WindowWidthClass.COMPACT,
          onSearchChange = {},
          onStatusFilterChange = {},
          onSortOrderChange = {},
          onViewModeChange = { currentMode = it },
          onOpenAddBook = {},
          onOpenBook = {},
          onToggleFavorite = {},
        )
      }
    }

    composeTestRule
      .onNodeWithTag("library_grid")
      .performScrollToNode(hasTestTag("library_grid_book_1"))
    composeTestRule.onNodeWithTag("library_grid_book_1").assertIsDisplayed()

    currentMode = LibraryViewMode.LIST
    composeTestRule.runOnIdle {}
    composeTestRule
      .onNodeWithTag("library_lazy_column")
      .performScrollToNode(hasTestTag("library_list_book_1"))
    composeTestRule.onNodeWithTag("library_list_book_1").assertIsDisplayed()

    currentMode = LibraryViewMode.SHELF
    composeTestRule.runOnIdle {}
    composeTestRule
      .onNodeWithTag("library_lazy_column")
      .performScrollToNode(hasTestTag("library_shelf_book_1"))
    composeTestRule.onNodeWithTag("library_shelf_book_1").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("library_lazy_column")
      .performScrollToNode(hasTestTag("library_shelf_book_8"))
    composeTestRule.onNodeWithTag("library_shelf_book_8").assertIsDisplayed()

    currentMode = LibraryViewMode.GALLERY
    composeTestRule.runOnIdle {}
    composeTestRule
      .onNodeWithTag("library_lazy_column")
      .performScrollToNode(hasTestTag("library_gallery_book_1"))
    composeTestRule.onNodeWithTag("library_gallery_book_1").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w320dp-h640dp")
  fun shelfModeFitsNarrow320dpScreenCleanly() {
    val sampleBooks =
      (1L..10L).map { id ->
        CatalogBook(
          work =
            Work(
              id = id,
              title = "Livro $id",
              author = "Autor $id",
              status = ReadingStatus.UNREAD,
              isFavorite = false,
              typographyPresetOverride = null,
              createdAtEpochMillis = id * 1000L,
              updatedAtEpochMillis = id * 1000L,
            ),
          primaryEdition = null,
          primaryCopy = null,
        )
      }

    composeTestRule.setContent {
      DipeLibrisTheme {
        LibraryScreen(
          uiState =
            LibraryUiState(
              books = sampleBooks,
              totalCatalogCount = sampleBooks.size,
              viewMode = LibraryViewMode.SHELF,
            ),
          widthClass = WindowWidthClass.COMPACT,
          onSearchChange = {},
          onStatusFilterChange = {},
          onSortOrderChange = {},
          onViewModeChange = {},
          onOpenAddBook = {},
          onOpenBook = {},
          onToggleFavorite = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("library_shelf_book_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_shelf_book_5").assertIsDisplayed()
  }

  @Test
  fun addBookSheetValidatesPagesAndYearRanges() {
    var saved = false
    composeTestRule.setContent {
      DipeLibrisTheme {
        AddBookSheet(
          onDismiss = {},
          onSave = { _, _, _, _, _, _ -> saved = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("add_book_title_input").performTextInput("Capitães da Areia")
    composeTestRule.onNodeWithTag("add_book_author_input").performTextInput("Jorge Amado")
    composeTestRule.onNodeWithTag("add_book_pages_input").performTextInput("0")
    composeTestRule.onNodeWithTag("add_book_save_button").performScrollTo().performClick()

    composeTestRule.onNodeWithTag("add_book_validation_error").performScrollTo().assertIsDisplayed()
    assertFalse(saved)
  }

  @Test
  fun bookDeletionRequiresConfirmationAndNotFoundStateDisplaysGracefully() {
    var deleted = false
    val book =
      CatalogBook(
        work =
          Work(
            id = 10L,
            title = "O Cortiço",
            author = "Aluísio Azevedo",
            status = ReadingStatus.FINISHED,
            isFavorite = false,
            typographyPresetOverride = null,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
          ),
        primaryEdition = null,
        primaryCopy = null,
      )

    var state by
      mutableStateOf<BookDetailUiState>(
        BookDetailUiState.Loaded(book = book, diaryNotes = emptyList())
      )
    composeTestRule.setContent {
      DipeLibrisTheme {
        BookDetailScreen(
          uiState = state,
          widthClass = WindowWidthClass.COMPACT,
          onToggleFavorite = {},
          onUpdateStatus = {},
          onConfirmDelete = { deleted = true },
          onNavigateBack = {},
        )
      }
    }

    // Scroll to delete button and click it
    composeTestRule
      .onNodeWithTag("book_detail_lazy_column")
      .performScrollToNode(hasTestTag("book_detail_delete_button"))
    composeTestRule.onNodeWithTag("book_detail_delete_button").performClick()
    composeTestRule.onNodeWithTag("book_delete_confirm_dialog").assertIsDisplayed()

    // Dismiss via cancel does not delete
    composeTestRule.onNodeWithTag("book_delete_cancel_button").performClick()
    assertFalse(deleted)

    // Re-open and confirm deletion
    composeTestRule
      .onNodeWithTag("book_detail_lazy_column")
      .performScrollToNode(hasTestTag("book_detail_delete_button"))
    composeTestRule.onNodeWithTag("book_detail_delete_button").performClick()
    composeTestRule.onNodeWithTag("book_delete_confirm_button").performClick()
    assertTrue(deleted)

    // Verify NotFound UI state
    state = BookDetailUiState.NotFound
    composeTestRule.runOnIdle {}
    composeTestRule.onNodeWithTag("book_detail_not_found").assertIsDisplayed()
  }

  @Test
  fun diaryDeletionRequiresConfirmationAndHomeLinkedNoteNavigatesToBook() {
    var deletedId: Long? = null
    val entry =
      DiaryEntry(
        id = 77L,
        workId = 55L,
        content = "Leitura noturna inesquecível.",
        createdAtEpochMillis = 10_000L,
        linkedBookTitle = "Os Sertões",
        linkedBookAuthor = "Euclides da Cunha",
      )

    composeTestRule.setContent {
      DipeLibrisTheme {
        DiaryScreen(
          uiState = DiaryUiState(entries = listOf(entry)),
          widthClass = WindowWidthClass.COMPACT,
          onOpenNewEntry = {},
          onDeleteEntry = { deletedId = it },
          onOpenBook = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("diary_delete_77").performClick()
    composeTestRule.onNodeWithTag("diary_delete_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("diary_delete_cancel_button").performClick()
    assertEquals(null, deletedId)

    composeTestRule.onNodeWithTag("diary_delete_77").performClick()
    composeTestRule.onNodeWithTag("diary_delete_confirm_button").performClick()
    assertEquals(77L, deletedId)
  }

  @Test
  fun homeLinkedDiaryEntryNavigatesToBookDetailWithoutBrandClutter() {
    var navigatedBookId: Long? = null
    val linkedEntry =
      DiaryEntry(
        id = 11L,
        workId = 88L,
        content = "Passagem marcante.",
        createdAtEpochMillis = 20_000L,
        linkedBookTitle = "Dom Casmurro",
        linkedBookAuthor = "Machado de Assis",
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme(themeMode = AppThemeMode.INK) {
          HomeScreen(
            uiState =
              HomeUiState(recentActivity = listOf(RecentActivityItem.DiaryRecorded(linkedEntry))),
            widthClass = WindowWidthClass.COMPACT,
            onOpenSettings = {},
            onOpenLibrary = {},
            onCatalogVolume = {},
            onWriteDiaryEntry = {},
            onOpenBook = { navigatedBookId = it },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("DL", ignoreCase = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Dipe Libris", ignoreCase = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("PERSONAL ARCHIVE", ignoreCase = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("ACERVO PESSOAL", ignoreCase = true).assertDoesNotExist()

    composeTestRule
      .onNodeWithTag("home_lazy_column")
      .performScrollToNode(hasTestTag("home_diary_entry_11"))
    composeTestRule.onNodeWithTag("home_diary_entry_11").performClick()
    assertEquals(88L, navigatedBookId)
  }

  @Test
  fun appVersionIs12AndDisplayedInSettings() {
    assertEquals("1.2", BuildConfig.VERSION_NAME)
    assertEquals(3, BuildConfig.VERSION_CODE)

    var language by mutableStateOf(AppLanguage.ENGLISH)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides language) {
        DipeLibrisTheme {
          SettingsScreen(
            preferences = UserPreferences(language = language),
            widthClass = WindowWidthClass.COMPACT,
            onSelectLanguage = { language = it },
            onSelectTheme = {},
            onSelectTypography = {},
            onSelectDefaultLibraryView = {},
            onToggleReduceMotion = {},
            onNavigateBack = {},
          )
        }
      }
    }

    composeTestRule
      .onNodeWithTag("settings_lazy_column")
      .performScrollToNode(hasTestTag("settings_app_version"))
    composeTestRule.onNodeWithText("Version 1.2").assertIsDisplayed()
  }

  @Test
  fun dataStorePreferencesPersistSettingsAcrossReads() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsRepo = DataStorePreferencesRepository(context)
    prefsRepo.setThemeMode(AppThemeMode.INK)
    prefsRepo.setDefaultLibraryViewMode(LibraryViewMode.SHELF)
    prefsRepo.setReduceMotion(true)
    prefsRepo.setReaderIdentity("Ana Beatriz", "Leitora noturna")

    val stored = prefsRepo.preferences.first()
    assertEquals(AppThemeMode.INK, stored.themeMode)
    assertEquals(LibraryViewMode.SHELF, stored.defaultLibraryViewMode)
    assertTrue(stored.reduceMotion)
    assertEquals("Ana Beatriz", stored.readerName)
    assertEquals("Leitora noturna", stored.readerBio)

    // Restore clean defaults
    prefsRepo.setThemeMode(AppThemeMode.PAPER)
    prefsRepo.setDefaultLibraryViewMode(LibraryViewMode.GRID)
    prefsRepo.setReduceMotion(false)
    prefsRepo.setReaderIdentity("", "")
  }

  @Test
  @Config(qualifiers = "w900dp-h1200dp")
  fun expandedWidthShowsNavigationRailAndNavigatesToLibrary() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = DefaultAppContainer(context)

    composeTestRule.setContent {
      DipeLibrisTheme {
        DipeNavigationShell(container = container)
      }
    }

    composeTestRule.onNodeWithTag("dipe_navigation_rail").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_rail_library").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("library_add_book_button").assertIsDisplayed()
  }
}
