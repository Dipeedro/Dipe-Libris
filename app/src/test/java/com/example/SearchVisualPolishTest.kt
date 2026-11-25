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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LanguageTier
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import com.example.feature.library.formatEditorialDisplayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchVisualPolishTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun sampleWorkGroup(
    groupId: String = "work_1",
    title: String = "A Hora da Estrela",
    subtitle: String? = null,
    author: String = "Clarice Lispector",
  ): SearchWorkGroup {
    val edition =
      SearchEditionCandidate(
        candidateId = "${groupId}_ed_1",
        workTitle = title,
        subtitle = subtitle,
        contributors = listOf(CandidateContributor(author, ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2020,
        totalPages = 88,
        isbn13 = "9788532531568",
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    return SearchWorkGroup(
      groupId = groupId,
      canonicalTitle = title,
      subtitle = subtitle,
      primaryAuthors = listOf(CandidateContributor(author, ContributorRole.AUTHOR)),
      editions = listOf(edition),
      sources = listOf(MetadataSource.GOOGLE_BOOKS),
      matchTierName = MatchTier.EXACT_WORK.name,
    )
  }

  @Test
  fun formatEditorialDisplayTitle_normalizesAllCapsAndAllLowercasePreservingMixedCase() {
    assertEquals("O Cortiço", formatEditorialDisplayTitle("O CORTIÇO"))
    assertEquals("Dom Casmurro", formatEditorialDisplayTitle("dom casmurro"))
    assertEquals(
      "Dom Casmurro: História do Século XIX",
      formatEditorialDisplayTitle("DOM CASMURRO: HISTÓRIA DO SÉCULO XIX"),
    )
    assertEquals(
      "Vou te Receitar um Gato",
      formatEditorialDisplayTitle("Vou te Receitar um Gato"),
    )
    assertEquals("Clean Code", formatEditorialDisplayTitle("Clean Code"))
  }

  @Test
  fun bookSearchScreen_removesTopSourceChipsAndShowsSubordinateBottomSourceStatusBar() {
    var retried = false
    val group = sampleWorkGroup()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Clarice",
                hasSearched = true,
                response =
                  HybridSearchResponse(
                    query = "Clarice",
                    isIsbnQuery = false,
                    isFromCache = true,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses =
                      listOf(
                        SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 4),
                        SourceQueryStatus(
                          MetadataSource.OPEN_LIBRARY,
                          ProviderOutcomeState.ERROR,
                          message = "Batch deadline exceeded",
                        ),
                        SourceQueryStatus(MetadataSource.APPLE_BOOKS, ProviderOutcomeState.EMPTY, 0),
                        SourceQueryStatus(MetadataSource.BRASIL_API, ProviderOutcomeState.SUCCESS, 1),
                      ),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = { retried = true },
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = {},
            onLoadMoreEditions = {},
            onSelectEdition = {},
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

    composeTestRule.waitForIdle()
    // Old top source chips and cache pill are completely gone
    composeTestRule.onNodeWithTag("search_cache_banner").assertDoesNotExist()
    composeTestRule.onNodeWithText("Google Books (4)").assertDoesNotExist()
    composeTestRule.onNodeWithText("Open Library (timed out)").assertDoesNotExist()
    composeTestRule.onNodeWithText("Apple Books (0)").assertDoesNotExist()

    // Scroll bottom source strip fully into viewport below manual registration
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_sources_status_toggle"))
    composeTestRule.onNodeWithTag("search_sources_status_toggle").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_manual_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_bottom_source_google_books", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_bottom_source_open_library", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_bottom_source_apple_books", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_bottom_source_brasil_api", useUnmergedTree = true).assertIsDisplayed()

    // Friendly retry prompt triggers retry action
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_sources_retry_hint"))
    composeTestRule.onNodeWithTag("search_sources_retry_hint").performClick()
    assertTrue(retried)

    // Expanding source details shows human-friendly per-source rows without technical header labels
    composeTestRule.onNodeWithTag("search_sources_status_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_sources_status_details"))
    composeTestRule.onNodeWithTag("search_sources_status_details").assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Google Books") and hasAnyAncestor(hasTestTag("search_sources_status_details")))
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Open Library") and hasAnyAncestor(hasTestTag("search_sources_status_details")))
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Apple Books") and hasAnyAncestor(hasTestTag("search_sources_status_details")))
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("BrasilAPI / CBL") and hasAnyAncestor(hasTestTag("search_sources_status_details")))
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Status das fontes", substring = true).assertDoesNotExist()
  }

  @Test
  fun bookSearchScreen_preservesPreviousResultsSmoothlyDuringFollowUpSearchRefresh() {
    val firstGroup = sampleWorkGroup(groupId = "g1", title = "A Hora da Estrela")
    var state by mutableStateOf(
      BookSearchUiState(
        query = "Clarice",
        hasSearched = true,
        isSearching = false,
        response =
          HybridSearchResponse(
            query = "Clarice",
            isIsbnQuery = false,
            isFromCache = false,
            isOffline = false,
            workGroups = listOf(firstGroup),
            sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1)),
          ),
      )
    )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState = state,
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = {},
            onLoadMoreEditions = {},
            onSelectEdition = {},
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

    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()

    // Start a follow-up query where response clears temporarily while isSearching = true
    state = state.copy(query = "Clarice nova", isSearching = true, response = null)
    composeTestRule.waitForIdle()

    // Previous result stays calmly visible instead of flashing into an empty state
    composeTestRule.onNodeWithTag("search_progressive_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()

    // When search completes truly empty, empty state appears cleanly
    state =
      state.copy(
        isSearching = false,
        response =
          HybridSearchResponse(
            query = "Clarice nova",
            isIsbnQuery = false,
            isFromCache = false,
            isOffline = false,
            workGroups = emptyList(),
            sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.EMPTY, 0)),
          ),
      )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_work_card_0").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_empty_manual_button").assertIsDisplayed()
  }

  @Test
  fun bookSearchScreen_rendersAllCapsTitleSubtitleDrawersAndPaginationCleanly() {
    val primary =
      sampleWorkGroup(
        groupId = "caps_1",
        title = "DOM CASMURRO",
        subtitle = "EDIÇÃO COMENTADA E ILUSTRADA DE COLECIONADOR",
        author = "Machado de Assis, Academia Brasileira de Letras, Prefácio Especial",
      )
    val secondary = sampleWorkGroup(groupId = "sec_1", title = "Dom Casmurro (English)")
    val derivative = sampleWorkGroup(groupId = "deriv_1", title = "Análise Crítica de Dom Casmurro")

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Dom Casmurro",
                hasSearched = true,
                response =
                  HybridSearchResponse(
                    query = "Dom Casmurro",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(primary),
                    secondaryWorkGroups = listOf(secondary),
                    derivativeWorkGroups = listOf(derivative),
                    hasMoreResults = true,
                    expandedSecondarySources = false,
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 3)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = {},
            onLoadMoreEditions = {},
            onSelectEdition = {},
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

    composeTestRule.waitForIdle()
    // Visual title & subtitle normalization
    composeTestRule
      .onNode(hasText("Dom Casmurro") and hasAnyAncestor(hasTestTag("search_work_card_0")))
      .assertIsDisplayed()
    composeTestRule
      .onNode(
        hasText("Edição Comentada e Ilustrada de Colecionador") and
          hasAnyAncestor(hasTestTag("search_work_card_0"))
      )
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_secondary_languages_toggle"))
    composeTestRule.onNodeWithTag("search_secondary_languages_toggle").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_derivative_works_toggle"))
    composeTestRule.onNodeWithTag("search_derivative_works_toggle").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_load_more_results_button"))
    composeTestRule.onNodeWithTag("search_load_more_results_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_expand_sources_button"))
    composeTestRule.onNodeWithTag("search_expand_sources_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_manual_button"))
    composeTestRule.onNodeWithTag("search_manual_button").assertIsDisplayed()
  }
}
