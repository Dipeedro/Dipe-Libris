package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.Genre
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
    // Closed card keeps normalized title & concise primary authors while hiding noisy subtitle/secondary credits
    composeTestRule
      .onNode(hasText("Dom Casmurro") and hasAnyAncestor(hasTestTag("search_work_card_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(
        hasText("Edição Comentada e Ilustrada de Colecionador") and
          hasAnyAncestor(hasTestTag("search_work_card_0")),
        useUnmergedTree = true,
      )
      .assertDoesNotExist()
    composeTestRule
      .onNode(
        hasText("Machado de Assis", substring = true) and
          hasAnyAncestor(hasTestTag("search_work_card_0")),
        useUnmergedTree = true,
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

  @Test
  fun closedEditorialCard_showsOnlyTitleAuthorYearGenreAndExpandButton() {
    val group =
      SearchWorkGroup(
        groupId = "g_closed",
        canonicalTitle = "O Senhor dos Anéis",
        subtitle = "Volume Único de Luxo",
        primaryAuthors =
          listOf(
            CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR),
            CandidateContributor("Alan Lee", ContributorRole.AUTHOR),
            CandidateContributor("Terceiro Autor Oculto", ContributorRole.AUTHOR),
          ),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "ed_1",
              workTitle = "O Senhor dos Anéis",
              contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
              publisher = "HarperCollins Brasil",
              publicationYear = 2022,
              totalPages = 1200,
              isbn13 = "9786555113631",
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "ed_2",
              workTitle = "O Senhor dos Anéis",
              contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
              publisher = "Martins Fontes",
              publicationYear = 2001,
              totalPages = 1212,
              isbn13 = "9788533613409",
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            ),
          ),
        suggestedGenreSlug = "fantasy",
        sources = listOf(MetadataSource.GOOGLE_BOOKS, MetadataSource.OPEN_LIBRARY),
      )
    val fantasyGenre =
      Genre(
        id = 10L,
        slug = "fantasy",
        nameEn = "Fantasy, Adventure",
        namePt = "Fantasia, Aventura",
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Senhor dos Aneis",
                hasSearched = true,
                genresBySlug = mapOf("fantasy" to fantasyGenre),
                response =
                  HybridSearchResponse(
                    query = "Senhor dos Aneis",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 2)),
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
    val cardTag = hasTestTag("search_work_card_0")
    val notInCover = !hasAnyAncestor(hasTestTag("search_work_cover_0"))
    val titleNode =
      composeTestRule.onNode(
        hasText("O Senhor dos Anéis") and hasAnyAncestor(cardTag) and notInCover,
        useUnmergedTree = true,
      )
    val yearNode =
      composeTestRule.onNode(hasText("2001") and hasAnyAncestor(cardTag), useUnmergedTree = true)
    val genreNode =
      composeTestRule.onNode(hasText("Fantasia") and hasAnyAncestor(cardTag), useUnmergedTree = true)
    val coverNode = composeTestRule.onNodeWithTag("search_work_cover_0", useUnmergedTree = true)

    titleNode.assertIsDisplayed()
    composeTestRule.onNode(hasText("J. R. R. Tolkien, Alan Lee") and hasAnyAncestor(cardTag), useUnmergedTree = true).assertIsDisplayed()
    yearNode.assertIsDisplayed()
    genreNode.assertIsDisplayed()
    coverNode.assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).assertIsDisplayed()

    // Protagonist cover bounds are substantial (>= 100dp wide, >= 140dp tall) and positioned left of text
    val coverBounds = coverNode.getBoundsInRoot()
    val titleBounds = titleNode.getBoundsInRoot()
    val yearBounds = yearNode.getBoundsInRoot()
    val genreBounds = genreNode.getBoundsInRoot()
    assertTrue((coverBounds.right - coverBounds.left) >= 100.dp)
    assertTrue((coverBounds.bottom - coverBounds.top) >= 140.dp)
    assertTrue(titleBounds.left > coverBounds.right)

    // Year and single primary genre occupy clearly separated vertical rows (never squashed on one line)
    assertTrue(genreBounds.top > yearBounds.bottom)
    composeTestRule.onNode(hasText("Fantasia, Aventura", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()

    // Excluded noisy items on closed card
    composeTestRule.onNode(hasText("Terceiro Autor Oculto", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Volume Único", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("HarperCollins", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("9786555113631") and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("edições", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Na biblioteca", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun expandedWorkCard_rendersFixed4ColumnGalleryHandlesMissingFieldsSelectionAndCollapse() {
    val editions =
      listOf(
        SearchEditionCandidate(
          candidateId = "ed_1",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = "Aleph",
          publicationYear = 2017,
          totalPages = 680,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "ed_2",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = "Editora Nova Fronteira Tradicional",
          publicationYear = 1984,
          totalPages = 620,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "ed_3",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = null,
          publicationYear = null,
          totalPages = null,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "ed_4",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = "Ace Books",
          publicationYear = 2010,
          totalPages = 896,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "ed_5",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = "Gollancz",
          publicationYear = 2015,
          totalPages = 592,
          sources = listOf(MetadataSource.APPLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "ed_6",
          workTitle = "Duna",
          contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
          publisher = "Chilton",
          publicationYear = 1965,
          totalPages = 412,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
      )
    val group =
      SearchWorkGroup(
        groupId = "g_dune",
        canonicalTitle = "Duna",
        primaryAuthors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
        editions = editions,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    var expandedGroupIds by mutableStateOf(emptySet<String>())
    var selectedEditionId: String? = null

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Duna",
                hasSearched = true,
                expandedGroupIds = expandedGroupIds,
                response =
                  HybridSearchResponse(
                    query = "Duna",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 6)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = { id ->
              expandedGroupIds = if (id in expandedGroupIds) expandedGroupIds - id else expandedGroupIds + id
            },
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

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()

    // Expand via terracotta circular button
    composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()

    // Work title remains in header, NEVER repeated inside edition tiles
    composeTestRule
      .onNode(
        hasText("Duna") and
          hasAnyAncestor(hasTestTag("search_work_card_0")) and
          !hasAnyAncestor(hasTestTag("search_work_cover_0")),
        useUnmergedTree = true,
      )
      .assertIsDisplayed()

    // Row 0 (tiles 0..3): scroll into view and assert fixed 4-column horizontal alignment
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    for (idx in 0..3) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText("Duna") and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    val row0Tops = (0..3).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Lefts = (0..3).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    // Missing metadata on edition 3 ("ed_3") renders graceful "—" fallback without empty collapse
    composeTestRule
      .onNode(hasText("Aleph") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("680") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2017") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    val missingNodes =
      composeTestRule.onAllNodes(
        hasText("—") and hasAnyAncestor(hasTestTag("search_edition_select_0_2")),
        useUnmergedTree = true,
      )
    assertEquals(3, missingNodes.fetchSemanticsNodes().size)

    // Tapping an edition tile triggers onSelectEdition
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performClick()
    assertEquals("ed_3", selectedEditionId)

    // Row 1 (tiles 4..5): scroll into view, verify alignment and incomplete trailing slots have no fake tiles
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_4"))
    for (idx in 4..5) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText("Duna") and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    composeTestRule.onNodeWithTag("search_edition_select_0_6").assertDoesNotExist()
    val row1Top0 = composeTestRule.onNodeWithTag("search_edition_select_0_4").getBoundsInRoot().top
    val row1Top1 = composeTestRule.onNodeWithTag("search_edition_select_0_5").getBoundsInRoot().top
    val row1Left0 = composeTestRule.onNodeWithTag("search_edition_select_0_4").getBoundsInRoot().left
    val row1Left1 = composeTestRule.onNodeWithTag("search_edition_select_0_5").getBoundsInRoot().left
    assertTrue(kotlin.math.abs((row1Top0 - row1Top1).value) <= 2f)
    assertTrue(row1Left1 > row1Left0)
    // Verify column alignment matches Row 0 columns 0 and 1
    assertTrue(kotlin.math.abs((row1Left0 - row0Lefts[0]).value) <= 2f)
    assertTrue(kotlin.math.abs((row1Left1 - row0Lefts[1]).value) <= 2f)

    // Bottom discreet collapse button collapses group (> 4 editions)
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_collapse_0"))
    composeTestRule.onNodeWithTag("search_work_collapse_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
  }

  @Test
  fun expandedGallery_renders8EditionsInTwoAligned4ColumnRowsWithLongTitleAndPublishers() {
    val longTitle = "As Crônicas Extraordinárias de um Viajante Solitário Pelos Mares do Sul"
    val editions =
      (1..8).map { idx ->
        SearchEditionCandidate(
          candidateId = "long_ed_$idx",
          workTitle = longTitle,
          contributors = listOf(CandidateContributor("Autor de Nome Extenso Literário", ContributorRole.AUTHOR)),
          publisher = "Companhia Editora Nacional de Publicações Clássicas Vol. $idx",
          publicationYear = 2000 + idx,
          totalPages = 300 + idx * 10,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        )
      }
    val group =
      SearchWorkGroup(
        groupId = "g_eight",
        canonicalTitle = longTitle,
        primaryAuthors = listOf(CandidateContributor("Autor de Nome Extenso Literário", ContributorRole.AUTHOR)),
        editions = editions,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Viajante",
                hasSearched = true,
                expandedGroupIds = setOf("g_eight"),
                editionWindowLimits = mapOf("g_eight" to 8),
                response =
                  HybridSearchResponse(
                    query = "Viajante",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 8)),
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
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    for (idx in 0..3) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    val row0Tops = (0..3).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Lefts = (0..3).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_4"))
    for (idx in 4..7) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    composeTestRule.onNodeWithTag("search_edition_select_0_8").assertDoesNotExist()

    val row1Tops = (4..7).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row1Lefts = (4..7).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row1Tops.all { kotlin.math.abs((it - row1Tops.first()).value) <= 2f })
    assertTrue(row1Lefts.zipWithNext().all { (a, b) -> b > a })
    row0Lefts.zip(row1Lefts).forEach { (left0, left1) ->
      assertTrue(kotlin.math.abs((left0 - left1).value) <= 2f)
    }
  }

  @Test
  fun expandedGallery_editorialActionsSecondaryLanguagesAndRemoteLoadMoreWorkCleanly() {
    val primaryEditions =
      listOf(
        SearchEditionCandidate(
          candidateId = "pt_1",
          workTitle = "Vou te Receitar um Gato",
          contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
          publisher = "Intrínseca",
          publicationYear = 2024,
          totalPages = 224,
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "pt_2",
          workTitle = "Vou te Receitar um Gato",
          contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
          publisher = "Companhia",
          publicationYear = 2025,
          totalPages = 240,
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.APPLE_BOOKS),
        ),
      )
    val secondaryEditions =
      listOf(
        SearchEditionCandidate(
          candidateId = "fr_1",
          workTitle = "On vous prescrira un chat",
          contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
          publisher = "Gallimard",
          publicationYear = 2024,
          totalPages = 256,
          canonicalLanguageTag = "fr",
          languageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "es_1",
          workTitle = "Te Receto un Gato",
          contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
          publisher = "Plaza",
          publicationYear = 2024,
          totalPages = 230,
          canonicalLanguageTag = "es",
          languageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
      )
    val group =
      SearchWorkGroup(
        groupId = "g_gato",
        canonicalTitle = "Vou te Receitar um Gato",
        primaryAuthors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
        editions = primaryEditions + secondaryEditions,
        externalWorkKey = "OL123W",
        sources = listOf(MetadataSource.GOOGLE_BOOKS, MetadataSource.OPEN_LIBRARY),
      )

    var expandedSecondaryIds by mutableStateOf(emptySet<String>())
    var loadMoreCalled = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Vou te receitar um gato",
                hasSearched = true,
                expandedGroupIds = setOf("g_gato"),
                expandedSecondaryEditionGroupIds = expandedSecondaryIds,
                response =
                  HybridSearchResponse(
                    query = "Vou te receitar um gato",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(group),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 4)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = {},
            onToggleGroupSecondaryEditions = { id ->
              expandedSecondaryIds =
                if (id in expandedSecondaryIds) expandedSecondaryIds - id else expandedSecondaryIds + id
            },
            onLoadMoreEditions = { loadMoreCalled = true },
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
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_load_more_0"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Editions in other languages · 2").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Load more editions").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Hide other languages").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_2"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_3").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_load_more_0"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_load_more_0").performClick()
    assertTrue(loadMoreCalled)
  }

  @Test
  fun expandedGallery_repeatedExpandCollapseAndReduceMotionRenderCleanlyAcrossMultipleWorks() {
    val work1 = sampleWorkGroup(groupId = "w1", title = "Dom Casmurro", author = "Machado de Assis")
    val work2 = sampleWorkGroup(groupId = "w2", title = "Vidas Secas", author = "Graciliano Ramos")
    var expandedGroupIds by mutableStateOf(emptySet<String>())

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = true) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Clássicos",
                hasSearched = true,
                expandedGroupIds = expandedGroupIds,
                response =
                  HybridSearchResponse(
                    query = "Clássicos",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work1, work2),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 2)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = { id ->
              expandedGroupIds = if (id in expandedGroupIds) expandedGroupIds - id else expandedGroupIds + id
            },
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
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_card_1").assertIsDisplayed()

    // Expand -> collapse -> expand repeatedly under reduceMotion = true
    repeat(2) {
      composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()
      composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
    }

    composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_1"))
    composeTestRule.onNodeWithTag("search_work_card_1").assertIsDisplayed()
  }
}
