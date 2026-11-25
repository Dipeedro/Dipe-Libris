package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.CatalogBook
import com.example.core.model.ContributorRole
import com.example.core.model.Genre
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LanguageTier
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.ReadingStatus
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.core.model.Work
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryAndSearchRedesignTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun bookSearch_compactTopBarUsesImeSearchCameraPillAndFooterManualCatalog() {
    var query by mutableStateOf("")
    var submittedCount = 0
    var scannerOpened = false
    var manualOpened = false
    var selectedEditionId: String? = null

    val edition =
      SearchEditionCandidate(
        candidateId = "ed_rocco",
        workTitle = "Harry Potter e o Prisioneiro de Azkaban",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Editora Rocco Ltda.",
        publicationYear = 2017,
        totalPages = 288,
        isbn13 = "9788532530783",
        language = "pt-br",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val group =
      SearchWorkGroup(
        groupId = "g_hp3",
        canonicalTitle = "Harry Potter e o Prisioneiro de Azkaban",
        primaryAuthors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        editions = listOf(edition),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    var showResults by mutableStateOf(false)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              if (showResults) {
                BookSearchUiState(
                  query = query,
                  hasSearched = true,
                  expandedGroupIds = setOf("g_hp3"),
                  response =
                    HybridSearchResponse(
                      query = query,
                      isIsbnQuery = false,
                      isFromCache = false,
                      isOffline = false,
                      workGroups = listOf(group),
                      sourceStatuses =
                        listOf(
                          SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1),
                        ),
                    ),
                )
              } else {
                BookSearchUiState(query = query)
              },
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = { query = it },
            onSubmitSearch = {
              submittedCount++
              showResults = true
            },
            onExpandSecondarySources = {},
            onOpenScanner = { scannerOpened = true },
            onManualCatalog = { manualOpened = true },
            onToggleGroupExpanded = {},
            onLoadMoreEditions = {},
            onSelectEdition = { selectedEditionId = it.candidateId },
            onOpenExistingBook = {},
            onDismissDuplicate = {},
            onDuplicateAddCopy = { _, _, _ -> },
            onDuplicateAddEdition = { _, _ -> },
            onDuplicateSaveSeparately = {},
            onNavigateBack = {},
          )
        }
      }
    }

    // No dedicated search submit button; circular camera button and initial manual catalog are present
    composeTestRule.onNodeWithTag("search_submit_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_scan_isbn_button").assertIsDisplayed().performClick()
    assertTrue(scannerOpened)
    composeTestRule.onNodeWithTag("search_manual_button").assertIsDisplayed()

    // Typing shows clear button; IME Search submits query
    composeTestRule.onNodeWithTag("search_query_input").performTextInput("Harry Potter")
    composeTestRule.onNodeWithTag("search_query_input_clear").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_query_input").performImeAction()
    assertEquals(1, submittedCount)

    // Results show editorial work card and 4-column edition gallery tile (publisher, pages, year; no ISBN/p./repeated title)
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Selecionar e revisar").assertDoesNotExist()
    composeTestRule.onNodeWithText("9788532530783").assertDoesNotExist()
    composeTestRule.onNodeWithText("288 p.").assertDoesNotExist()
    composeTestRule
      .onNode(
        hasText("Editora Rocco Ltda.") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")),
        useUnmergedTree = true,
      )
      .assertIsDisplayed()
    composeTestRule
      .onNode(
        hasText("288") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")),
        useUnmergedTree = true,
      )
      .assertIsDisplayed()
    composeTestRule
      .onNode(
        hasText("2017") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")),
        useUnmergedTree = true,
      )
      .assertIsDisplayed()
    composeTestRule
      .onNode(
        hasText("Harry Potter e o Prisioneiro de Azkaban") and
          hasAnyAncestor(hasTestTag("search_edition_select_0_0")),
        useUnmergedTree = true,
      )
      .assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performClick()
    assertEquals("ed_rocco", selectedEditionId)

    // Manual catalog button is cleanly placed in the results footer
    composeTestRule
      .onNodeWithTag("book_search_screen")
      .performScrollToNode(hasTestTag("search_manual_button"))
    composeTestRule.onNodeWithTag("search_manual_button").performClick()
    assertTrue(manualOpened)

    // Clear button resets query
    composeTestRule.onNodeWithTag("search_query_input_clear").performClick()
    assertEquals("", query)
  }

  @Test
  fun libraryScreen_viewModeToggleFiltersSheetAndListMenuWorkCohesively() {
    val fantasy =
      Genre(
        id = 10L,
        slug = "fantasy",
        nameEn = "Fantasy",
        namePt = "Fantasia",
      )
    val book =
      CatalogBook(
        work =
          Work(
            id = 1L,
            title = "O Hobbit",
            author = "J. R. R. Tolkien",
            status = ReadingStatus.READING,
            isFavorite = true,
            typographyPresetOverride = null,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
          ),
        primaryEdition = null,
        primaryCopy = null,
        primaryGenre = fantasy,
      )

    var viewMode by mutableStateOf(LibraryViewMode.GRID)
    var sortOrder by mutableStateOf(LibrarySortOrder.RECENTLY_ADDED)
    var genreFilter by mutableStateOf<Long?>(null)
    var statusFilter by mutableStateOf<ReadingStatus?>(null)
    var updatedStatus: ReadingStatus? = null
    var deletedBookId: Long? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          LibraryScreen(
            uiState =
              LibraryUiState(
                books =
                  if (statusFilter == ReadingStatus.ABANDONED) emptyList() else listOf(book),
                totalCatalogCount = 1,
                readingCount = 1,
                availableGenres = listOf(fantasy),
                statusFilter = statusFilter,
                genreFilter = genreFilter,
                sortOrder = sortOrder,
                viewMode = viewMode,
                totalPendingSuggestionsCount = 2,
              ),
            widthClass = WindowWidthClass.COMPACT,
            onSearchChange = {},
            onStatusFilterChange = { statusFilter = it },
            onSortOrderChange = { sortOrder = it },
            onViewModeChange = { viewMode = it },
            onOpenAddBook = {},
            onOpenBook = {},
            onGenreFilterChange = { genreFilter = it },
            onUpdateBookStatus = { _, status -> updatedStatus = status },
            onDeleteBook = { deletedBookId = it },
          )
        }
      }
    }

    // View mode toggle cycles directly GRID -> LIST and displays transient pill
    composeTestRule.onNodeWithTag("library_view_mode_trigger").assertIsDisplayed().performClick()
    assertEquals(LibraryViewMode.LIST, viewMode)
    composeTestRule.onNodeWithTag("library_list_book_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_view_mode_pill").assertIsDisplayed()

    // List row 3-dot menu exposes Mudar status, Ver detalhes, Remover
    composeTestRule.onNodeWithTag("library_list_more_1").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("library_list_action_status_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_list_action_details_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_list_action_remove_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_list_action_status_1").performClick()
    composeTestRule.onNodeWithTag("library_status_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_status_choice_finished").performClick()
    assertEquals(ReadingStatus.FINISHED, updatedStatus)

    // Filters sheet consolidates Genre, Sort (including Progress/Pages/Year), and fixed footer without Maintenance
    composeTestRule.onNodeWithTag("library_sort_button").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("library_filters_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_check_updates_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("library_genre_filter_fantasy").performClick()
    assertEquals(10L, genreFilter)
    composeTestRule.onNodeWithTag("library_sort_option_progress_desc").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_sort_option_pages_desc").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_sort_option_year_desc").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_sort_option_title_asc").performClick()
    assertEquals(LibrarySortOrder.TITLE_ASC, sortOrder)

    // Fixed footer shows Limpar + Mostrar N livros and closes sheet on tap
    composeTestRule.onNodeWithTag("library_filters_clear_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_filters_show_button").assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("library_filters_sheet")).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule
      .onNodeWithTag("library_active_genre_chip")
      .assertIsDisplayed()
      .performClick()
    assertEquals(null, genreFilter)

    // Filtered empty state shows "Nada por aqui" + "Ver todos" clearing filters
    composeTestRule.onNodeWithTag("library_filter_abandoned").performClick()
    composeTestRule.onNodeWithTag("library_filtered_empty_stage").assertIsDisplayed()
    composeTestRule.onNodeWithTag("library_empty_show_all_button").assertIsDisplayed().performClick()
    assertEquals(null, statusFilter)
    assertEquals(null, deletedBookId)
  }
}
