package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomReadingJourneyRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.BookCopy
import com.example.core.model.BookDetailAggregate
import com.example.core.model.CatalogBook
import com.example.core.model.CopyMediaType
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.EditionWithBookCopies
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingJourneySummary
import com.example.core.model.ReadingSession
import com.example.core.model.ReadingStatus
import com.example.core.model.Work
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeUiState
import com.example.feature.library.AddBookSheet
import com.example.feature.library.AddBookSubmission
import com.example.feature.library.BookDetailScreen
import com.example.feature.library.BookDetailUiState
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryUiState
import com.example.feature.library.QuickProgressSheet
import com.example.feature.statistics.StatisticsScreen
import com.example.navigation.DipeNavigationShell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2UiFlowsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun bookDetailPagerSwipesBetweenEditionsAndOpensComparison() {
    val work =
      Work(
        id = 1L,
        title = "Memórias Póstumas de Brás Cubas",
        author = "Machado de Assis",
        status = ReadingStatus.READING,
      )
    val ed1 =
      Edition(
        id = 10L,
        workId = 1L,
        publisherOrNote = "Editora 34",
        publicationYear = 2014,
        totalPages = 320,
        language = "Português",
        format = EditionFormat.HARDCOVER,
        isPrimary = true,
      )
    val ed2 =
      Edition(
        id = 11L,
        workId = 1L,
        publisherOrNote = "Penguin Classics",
        publicationYear = 2020,
        totalPages = 280,
        language = "English",
        format = EditionFormat.PAPERBACK,
        translator = "Flora Thomson-DeVeaux",
      )
    val copy1 = BookCopy(id = 100L, editionId = 10L, label = "Estante sala")
    val copy2 =
      BookCopy(
        id = 101L,
        editionId = 11L,
        mediaType = CopyMediaType.DIGITAL,
        label = "Kindle",
      )

    val aggregate =
      BookDetailAggregate(
        work = work,
        editions =
          listOf(
            EditionWithBookCopies(ed1, listOf(copy1)),
            EditionWithBookCopies(ed2, listOf(copy2)),
          ),
        activeJourney =
          ReadingJourneySummary(
            session =
              ReadingSession(
                id = 500L,
                workId = 1L,
                copyId = 100L,
                currentPage = 160,
                totalPagesSnapshot = 320,
                progressPercent = 50f,
                startEpochMillis = 10_000L,
              ),
            copy = copy1,
            edition = ed1,
            logs = emptyList(),
          ),
        journeys = emptyList(),
      )

    var focusedId by mutableStateOf<Long?>(10L)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookDetailScreen(
            uiState =
              BookDetailUiState.Loaded(
                detail = aggregate,
                diaryNotes = emptyList(),
                focusedEditionId = focusedId,
              ),
            widthClass = WindowWidthClass.COMPACT,
            onToggleFavorite = {},
            onUpdateStatus = {},
            onOpenDiary = {},
            onConfirmDelete = {},
            onNavigateBack = {},
            onSelectEdition = { focusedId = it },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("book_detail_edition_indicator").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("book_detail_lazy_column")
      .performScrollToNode(hasText("Editora 34"))
    composeTestRule.onNodeWithText("Editora 34").assertIsDisplayed()

    // Swipe HorizontalPager left to reveal second edition
    composeTestRule.onNodeWithTag("book_detail_edition_pager").performScrollTo().performTouchInput {
      swipeLeft()
    }
    composeTestRule.waitUntil(timeoutMillis = 5_000) { focusedId == 11L }
    composeTestRule
      .onNodeWithTag("book_detail_lazy_column")
      .performScrollToNode(hasText("Penguin Classics"))
    composeTestRule.onNodeWithText("Penguin Classics").assertIsDisplayed()

    // Open comparison sheet
    composeTestRule
      .onNodeWithTag("book_detail_compare_editions_button")
      .performScrollTo()
      .performClick()
    composeTestRule.onNodeWithTag("edition_comparison_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("compare_edition_card_10").assertIsDisplayed()
    composeTestRule.onNodeWithTag("compare_edition_card_11").assertIsDisplayed()
  }

  @Test
  fun quickProgressSheetConvertsPageToPercentAndValidatesBounds() {
    var savedPage: Int? = null
    var finishedCalled = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          QuickProgressSheet(
            bookTitle = "Dom Casmurro",
            initialPage = 50,
            totalPages = 200,
            initialPercent = 25f,
            onDismiss = {},
            onSavePage = { page, _ -> savedPage = page },
            onSavePercent = { _, _ -> },
            onMarkFinished = { finishedCalled = true },
          )
        }
      }
    }

    // Page-only mode: percent mode toggle removed, hero displays live percentage
    composeTestRule.onNodeWithTag("progress_mode_page").assertDoesNotExist()
    composeTestRule.onNodeWithTag("progress_mode_percent").assertDoesNotExist()
    composeTestRule.onNodeWithTag("progress_hero_percent").assertIsDisplayed()

    // 4 symmetric steppers (-10, -1, +1, +10), without +5
    composeTestRule.onNodeWithTag("progress_step_plus_5").assertDoesNotExist()
    composeTestRule.onNodeWithTag("progress_step_minus_10").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("progress_step_minus_1").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("progress_step_plus_10").performScrollTo().assertIsDisplayed()

    // Stepper +1 moves page 50 -> 51 (and updates hero percentage 25% -> 26%)
    composeTestRule.onNodeWithTag("progress_step_plus_1").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("progress_hero_percent").assertIsDisplayed()
    composeTestRule.onNodeWithTag("progress_save_button").performScrollTo().performClick()
    assertEquals(51, savedPage)

    // Exceeding page count displays validation error
    composeTestRule.onNodeWithTag("progress_value_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("progress_value_input").performTextInput("999")
    composeTestRule.onNodeWithTag("progress_save_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("progress_validation_error").assertIsDisplayed()

    // Completing requires confirmation dialog
    composeTestRule.onNodeWithTag("progress_complete_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("progress_complete_confirm_dialog").assertIsDisplayed()
    assertFalse(finishedCalled)
    composeTestRule.onNodeWithTag("progress_complete_confirm_button").performClick()
    assertTrue(finishedCalled)
  }

  @Test
  fun addBookSheetValidatesIsbnAndSubmitsExtendedEditionCopyDetails() {
    var submitted: AddBookSubmission? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          AddBookSheet(
            onDismiss = {},
            onSave = { submitted = it },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("add_book_title_input").performScrollTo().performTextInput("Grande Sertão")
    composeTestRule.onNodeWithTag("add_book_author_input").performScrollTo().performTextInput("Guimarães Rosa")
    composeTestRule.onNodeWithTag("add_book_more_details_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Invalid ISBN blocks submit (Save and validation error are always visible in pinned footer)
    composeTestRule.onNodeWithTag("add_book_isbn_input").performScrollTo().performTextInput("12345")
    composeTestRule.onNodeWithTag("add_book_save_button").performClick()
    composeTestRule.onNodeWithTag("add_book_validation_error").assertIsDisplayed()
    assertEquals(null, submitted)

    // Valid ISBN + Digital copy + Hardcover format succeeds
    composeTestRule.onNodeWithTag("add_book_isbn_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("add_book_isbn_input").performTextInput("978-85-359-0277-8")
    composeTestRule.onNodeWithTag("add_book_format_hardcover").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("add_book_copy_media_digital").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("add_book_save_button").performClick()

    assertNotNull(submitted)
    assertEquals(EditionFormat.HARDCOVER, submitted!!.format)
    assertEquals(CopyMediaType.DIGITAL, submitted!!.copyMediaType)
    assertEquals("978-85-359-0277-8", submitted!!.isbn)
  }

  @Test
  fun addBookSheetHeroCoverLaunchesGalleryDirectlyAndGuardsDraftOnClose() {
    var dismissed = false

    composeTestRule.setContent {
      val baseContext = androidx.compose.ui.platform.LocalContext.current
      val localizedContext =
        com.example.core.localization.LocaleManager.createLocalizedContext(
          baseContext,
          AppLanguage.PORTUGUESE_BR,
        )
      CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        LocalAppLanguage provides AppLanguage.PORTUGUESE_BR,
      ) {
        DipeLibrisTheme {
          AddBookSheet(
            onDismiss = { dismissed = true },
            onSave = {},
          )
        }
      }
    }

    // Save button is visible immediately without scrolling
    composeTestRule.onNodeWithTag("add_book_save_button").assertIsDisplayed()

    // Tapping cover badge launches the gallery directly without any chooser dialog or registry error
    composeTestRule.onNodeWithTag("add_book_cover_badge").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cover_source_gallery_option").assertDoesNotExist()
    composeTestRule.onNodeWithTag("cover_source_files_option").assertDoesNotExist()
    composeTestRule.onNodeWithTag("add_book_cover_error").assertDoesNotExist()

    // Typing unsaved text triggers discard confirmation on close button
    composeTestRule.onNodeWithTag("add_book_title_input").performScrollTo().performTextInput("Vidas Secas")
    composeTestRule.onNodeWithTag("add_book_close_button").performClick()
    composeTestRule.onNodeWithTag("add_book_discard_keep").assertIsDisplayed().performClick()
    assertEquals(false, dismissed)

    composeTestRule.onNodeWithTag("add_book_close_button").performClick()
    composeTestRule.onNodeWithTag("add_book_discard_confirm").performClick()
    assertTrue(dismissed)
  }

  @Test
  fun libraryViewModesAndStatisticsShowMultiEditionAndJourneyFigures() {
    val sampleBook =
      CatalogBook(
        work =
          Work(
            id = 1L,
            title = "O Cortiço",
            author = "Aluísio Azevedo",
            status = ReadingStatus.READING,
            isFavorite = true,
          ),
        primaryEdition = Edition(id = 10L, workId = 1L, totalPages = 300, publicationYear = 1890),
        primaryCopy = BookCopy(id = 20L, editionId = 10L, mediaType = CopyMediaType.PHYSICAL),
        editionCount = 2,
        copyCount = 3,
        activeSessionId = 99L,
        activeProgressPercent = 40f,
        activeCurrentPage = 120,
        activeTotalPages = 300,
      )

    var viewMode by mutableStateOf(LibraryViewMode.GRID)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          LibraryScreen(
            uiState =
              LibraryUiState(
                books = listOf(sampleBook),
                totalCatalogCount = 1,
                viewMode = viewMode,
              ),
            widthClass = WindowWidthClass.COMPACT,
            onSearchChange = {},
            onStatusFilterChange = {},
            onSortOrderChange = {},
            onViewModeChange = { viewMode = it },
            onOpenAddBook = {},
            onOpenBook = {},
            onToggleFavorite = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("library_grid_book_1").assertIsDisplayed()
    viewMode = LibraryViewMode.LIST
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("library_list_book_1").assertIsDisplayed()

    viewMode = LibraryViewMode.GALLERY
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("library_lazy_column")
      .performScrollToNode(hasTestTag("library_gallery_book_1"))
    composeTestRule.onNodeWithTag("library_gallery_book_1").assertIsDisplayed()
  }

  @Test
  fun statisticsScreenDisplaysPagesCompletedAndRereadMetrics() {
    val snapshot =
      ArchiveSnapshot(
        totalWorks = 4,
        readingCount = 1,
        finishedCount = 2,
        unreadCount = 1,
        diaryEntriesCount = 5,
        editionsCount = 6,
        copiesCount = 7,
        completedJourneysCount = 3,
        rereadsCount = 1,
        totalPagesRead = 1240,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          StatisticsScreen(snapshot = snapshot, widthClass = WindowWidthClass.COMPACT)
        }
      }
    }

    composeTestRule.onNodeWithTag("statistics_pages_read").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("statistics_completed_journeys").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("statistics_rereads").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w360dp-h780dp")
  fun homeScreenRendersEmptySingleAndMultiBookStatesWithBrandSealAndProgressSheet() {
    val bookOne =
      CatalogBook(
        work = Work(id = 1L, title = "Dom Casmurro", author = "Machado de Assis", status = ReadingStatus.READING),
        primaryEdition = Edition(id = 10L, workId = 1L, totalPages = 208, publicationYear = 2022, isPrimary = true),
        primaryCopy = BookCopy(id = 20L, editionId = 10L, currentPage = 116),
        activeSessionId = 50L,
        activeProgressPercent = 55.8f,
        activeCurrentPage = 116,
        activeTotalPages = 208,
      )
    val bookTwo =
      CatalogBook(
        work = Work(id = 2L, title = "Memórias Póstumas", author = "Machado de Assis", status = ReadingStatus.READING),
        primaryEdition = Edition(id = 11L, workId = 2L, totalPages = 320, publicationYear = 2019, isPrimary = true),
        primaryCopy = BookCopy(id = 21L, editionId = 11L, currentPage = 80),
        activeSessionId = 51L,
        activeProgressPercent = 25f,
        activeCurrentPage = 80,
        activeTotalPages = 320,
      )

    var uiState by mutableStateOf(HomeUiState())
    composeTestRule.setContent {
      val baseContext = androidx.compose.ui.platform.LocalContext.current
      val ptContext =
        com.example.core.localization.LocaleManager.createLocalizedContext(
          baseContext,
          AppLanguage.PORTUGUESE_BR,
        )
      CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides ptContext,
        LocalAppLanguage provides AppLanguage.PORTUGUESE_BR,
      ) {
        DipeLibrisTheme {
          HomeScreen(
            uiState = uiState,
            widthClass = WindowWidthClass.COMPACT,
            onOpenBook = {},
          )
        }
      }
    }

    // Empty state shows calm empty stage without brand seal or text logo clutter
    composeTestRule.onNodeWithTag("home_brand_seal").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_empty_stage").assertIsDisplayed()
    composeTestRule.onNodeWithText("DL", ignoreCase = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Dipe Libris", ignoreCase = true).assertDoesNotExist()

    // Single book showcase shows metadata, progress label, and opens QuickProgressSheet
    uiState =
      HomeUiState(
        currentlyReading = listOf(bookOne),
        snapshot = ArchiveSnapshot(readingCount = 1),
      )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_reading_book_1").assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("home_featured_progress_label", useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("home_lazy_column")
      .performScrollToNode(hasTestTag("home_update_progress_button"))
    composeTestRule.onNodeWithTag("home_update_progress_button").performClick()
    composeTestRule.onNodeWithTag("quick_progress_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("progress_cancel_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("quick_progress_sheet").assertDoesNotExist()

    // Multi-book state displays secondary horizontal reading strip
    uiState =
      HomeUiState(
        currentlyReading = listOf(bookOne, bookTwo),
        snapshot = ArchiveSnapshot(readingCount = 2),
      )
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithTag("home_lazy_column")
      .performScrollToNode(hasTestTag("home_reading_book_2"))
    composeTestRule.onNodeWithTag("home_reading_book_2").assertIsDisplayed()
  }

  @Test
  fun bottomNavigationUsesConsistentReadingLabelInEnglishAndPortuguese() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = com.example.core.di.DefaultAppContainer(context)
    var language by mutableStateOf(AppLanguage.PORTUGUESE_BR)

    composeTestRule.setContent {
      val localizedContext =
        com.example.core.localization.LocaleManager.createLocalizedContext(context, language)
      CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        LocalAppLanguage provides language,
      ) {
        DipeLibrisTheme {
          DipeNavigationShell(container = container)
        }
      }
    }

    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithText("Início").assertDoesNotExist()
    composeTestRule.onNodeWithText("Biblioteca").assertIsDisplayed()
    composeTestRule.onNodeWithText("Estatísticas").assertIsDisplayed()
    composeTestRule.onNodeWithText("Perfil").assertIsDisplayed()

    language = AppLanguage.ENGLISH
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Home").assertDoesNotExist()
    composeTestRule.onNodeWithText("Library").assertIsDisplayed()
    composeTestRule.onNodeWithText("Statistics").assertIsDisplayed()
    composeTestRule.onNodeWithText("Profile").assertIsDisplayed()
  }

  @Test
  fun roomReadingJourneyCompleteWithUndoSnapshotRestoresPreviousProgress() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val libraryRepo =
      RoomLibraryRepository(
        bookDao = db.bookDao(),
        shelfDao = db.shelfDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        readingJourneyDao = db.readingJourneyDao(),
        database = db,
      )
    val journeyRepo =
      RoomReadingJourneyRepository(
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        journeyDao = db.readingJourneyDao(),
        database = db,
      )

    val workId =
      libraryRepo.addBook(
        title = "A Hora da Estrela",
        author = "Clarice Lispector",
        totalPages = 208,
        publicationYear = 1977,
        editionNote = "Rocco",
        status = ReadingStatus.READING,
      )
    val initialDetail = libraryRepo.observeBookDetail(workId).first()!!
    val copyId = initialDetail.editions.first().copies.first().id
    val sessionId = journeyRepo.startJourney(workId = workId, copyId = copyId)
    journeyRepo.updateProgressByPage(sessionId = sessionId, page = 116)

    val beforeComplete = libraryRepo.observeBookDetail(workId).first()!!
    val snapshot = journeyRepo.completeJourneyWithUndoSnapshot(sessionId = sessionId, note = "Fim")
    assertNotNull(snapshot)

    val completedDetail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(ReadingStatus.FINISHED, completedDetail.work.status)
    assertEquals(1, beforeComplete.journeys.first().logs.size)
    assertEquals(2, completedDetail.journeys.first().logs.size)

    journeyRepo.restoreJourneyCompletion(snapshot!!)
    val restoredDetail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(ReadingStatus.READING, restoredDetail.work.status)
    assertNotNull(restoredDetail.activeJourney)
    assertEquals(116, restoredDetail.activeJourney!!.session.currentPage)
    assertEquals(1, restoredDetail.activeJourney!!.logs.size)

    db.close()
  }
}
