package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
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
import com.example.feature.library.calculateBook3dProjection
import com.example.feature.library.calculateBookThicknessSpec
import com.example.feature.library.formatEditorialDisplayTitle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
  @Config(qualifiers = "w411dp-h891dp")
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
        slug = "fantasy-scifi",
        nameEn = "Fantasy & Sci-Fi",
        namePt = "Fantasia e ficção científica",
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
    val expandNode = composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true)

    titleNode.assertIsDisplayed()
    composeTestRule.onNode(hasText("J. R. R. Tolkien, Alan Lee") and hasAnyAncestor(cardTag), useUnmergedTree = true).assertIsDisplayed()
    yearNode.assertIsDisplayed()
    genreNode.assertIsDisplayed()
    coverNode.assertIsDisplayed()
    expandNode.assertIsDisplayed()

    // Protagonist cover bounds are substantial (>= 100dp wide, >= 140dp tall) and positioned left of text
    val cardBounds = composeTestRule.onNodeWithTag("search_work_card_0").getBoundsInRoot()
    val coverBounds = coverNode.getBoundsInRoot()
    val titleBounds = titleNode.getBoundsInRoot()
    val yearBounds = yearNode.getBoundsInRoot()
    val genreBounds = genreNode.getBoundsInRoot()
    val expandBounds = expandNode.getBoundsInRoot()
    assertTrue((coverBounds.right - coverBounds.left) >= 100.dp)
    assertTrue((coverBounds.bottom - coverBounds.top) >= 140.dp)
    assertTrue(titleBounds.left > coverBounds.right)

    // Bottom-end expand control keeps a full >= 48dp accessible touch target inside the card
    assertTrue((expandBounds.right - expandBounds.left) >= 47.dp)
    assertTrue((expandBounds.bottom - expandBounds.top) >= 47.dp)
    assertTrue(expandBounds.left >= titleBounds.left)
    assertTrue(expandBounds.bottom <= cardBounds.bottom)
    assertTrue(expandBounds.right <= cardBounds.right)

    // Year and single primary genre share a clean inline baseline row
    assertTrue(kotlin.math.abs((yearBounds.top - genreBounds.top).value) <= 6f)
    assertTrue(genreBounds.left > yearBounds.right)
    composeTestRule.onNode(hasText("Fantasia e ficção científica", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("ficção", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()

    // Excluded noisy items on closed card
    composeTestRule.onNode(hasText("Terceiro Autor Oculto", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Volume Único", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("HarperCollins", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("9786555113631") and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("edições", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Na biblioteca", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun expandedWorkCard_renders3ColumnCompactGalleryHandlesMissingFieldsSelectionAndCollapse() {
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
    var highlightedEditionId by mutableStateOf<String?>(null)
    var confirmedEditionId by mutableStateOf<String?>(null)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Duna",
                hasSearched = true,
                expandedGroupIds = expandedGroupIds,
                selectedEditionId = highlightedEditionId,
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
            onToggleEditionSelection = { candidate ->
              highlightedEditionId =
                if (highlightedEditionId == candidate.candidateId) null else candidate.candidateId
            },
            onSelectEdition = { confirmedEditionId = it.candidateId },
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

    // Expand via card control
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

    // Row 0 (tiles 0..2): scroll into view and assert 3-column Compact horizontal alignment
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    for (idx in 0..2) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText("Duna") and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    val row0Tops = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Lefts = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    // Resting edition tiles show strictly covers without visible metadata or placeholder dashes
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_2_back_face").assertDoesNotExist()
    composeTestRule
      .onNode(hasText("Aleph") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule
      .onNode(hasText("680 p.") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule
      .onNode(hasText("—") and hasAnyAncestor(hasTestTag("search_edition_select_0_2")), useUnmergedTree = true)
      .assertDoesNotExist()

    // Holding edition 0_0 reveals verso with publisher, year, and pages without selecting; releasing restores cover
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Aleph") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2017") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("680 p.") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    assertEquals(null, highlightedEditionId)

    // Holding edition 0_2 (all fields null) omits missing fields gracefully with ZERO "—" placeholders
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_2_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("—") and hasAnyAncestor(hasTestTag("search_edition_select_0_2")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_2_back_face").assertDoesNotExist()

    // Tapping an edition tile surfaces refined selection indicator & confirm CTA before committing
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals("ed_3", highlightedEditionId)
    assertEquals(null, confirmedEditionId)
    composeTestRule.onNodeWithTag("search_edition_select_0_2_selected_badge", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_confirm_edition_button"))
    composeTestRule.onNodeWithTag("search_confirm_edition_button").assertIsDisplayed()

    // Selecting another edition transfers the selection indicator cleanly
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performClick()
    composeTestRule.waitForIdle()
    assertEquals("ed_1", highlightedEditionId)
    assertEquals(null, confirmedEditionId)
    composeTestRule.onNodeWithTag("search_edition_select_0_0_selected_badge", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_2_selected_badge", useUnmergedTree = true).assertDoesNotExist()

    // Confirm CTA commits onSelectEdition with the highlighted edition
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_confirm_edition_button"))
    composeTestRule.onNodeWithTag("search_confirm_edition_button").performClick()
    assertEquals("ed_1", confirmedEditionId)

    // Row 1 (tiles 3..5): scroll into view, verify 3-column alignment
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_3"))
    for (idx in 3..5) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText("Duna") and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    composeTestRule.onNodeWithTag("search_edition_select_0_6").assertDoesNotExist()
    val row1Tops = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row1Lefts = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row1Tops.all { kotlin.math.abs((it - row1Tops.first()).value) <= 2f })
    assertTrue(row1Lefts.zipWithNext().all { (a, b) -> b > a })
    row0Lefts.zip(row1Lefts).forEach { (left0, left1) ->
      assertTrue(kotlin.math.abs((left0 - left1).value) <= 2f)
    }

    // Bottom discreet collapse button collapses group (> 3 editions)
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_collapse_0"))
    composeTestRule.onNodeWithTag("search_work_collapse_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
  }

  @Test
  fun expandedGallery_renders8EditionsInAligned3ColumnRowsWithLongTitleAndPublishers() {
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
    for (idx in 0..2) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    val row0Tops = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Lefts = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_3"))
    for (idx in 3..5) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
    }
    val row1Tops = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row1Lefts = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row1Tops.all { kotlin.math.abs((it - row1Tops.first()).value) <= 2f })
    row0Lefts.zip(row1Lefts).forEach { (left0, left1) ->
      assertTrue(kotlin.math.abs((left0 - left1).value) <= 2f)
    }

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_6"))
    for (idx in 6..7) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("search_edition_select_0_8").assertDoesNotExist()

    val row2Top0 = composeTestRule.onNodeWithTag("search_edition_select_0_6").getBoundsInRoot().top
    val row2Top1 = composeTestRule.onNodeWithTag("search_edition_select_0_7").getBoundsInRoot().top
    val row2Left0 = composeTestRule.onNodeWithTag("search_edition_select_0_6").getBoundsInRoot().left
    val row2Left1 = composeTestRule.onNodeWithTag("search_edition_select_0_7").getBoundsInRoot().left
    assertTrue(kotlin.math.abs((row2Top0 - row2Top1).value) <= 2f)
    assertTrue(kotlin.math.abs((row2Left0 - row0Lefts[0]).value) <= 2f)
    assertTrue(kotlin.math.abs((row2Left1 - row0Lefts[1]).value) <= 2f)
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

    // Non-redundant footer: single search/load-more pill + language toggle pill; duplicate "+" action removed
    composeTestRule.onNodeWithTag("search_edition_add_more_0").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_load_more_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Editions in other languages · 2").assertIsDisplayed()

    val loadMoreBounds = composeTestRule.onNodeWithTag("search_edition_load_more_0").getBoundsInRoot()
    val langPillBounds = composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").getBoundsInRoot()
    assertTrue(langPillBounds.left > loadMoreBounds.right)

    composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_secondary_editions_toggle_0"))
    composeTestRule.onNodeWithContentDescription("Hide other languages").assertIsDisplayed()
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

  @Test
  @Config(qualifiers = "w320dp-h640dp")
  fun narrowScreen320dp_rendersSearchBarClosedCardAnd3ColumnEditionsWithoutOverflow() {
    val editions =
      (1..4).map { idx ->
        SearchEditionCandidate(
          candidateId = "narrow_ed_$idx",
          workTitle = "Grande Sertão: Veredas",
          contributors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
          publisher = "Companhia das Letras Edição Especial",
          publicationYear = 2019 + idx,
          totalPages = 550 + idx,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        )
      }
    val group =
      SearchWorkGroup(
        groupId = "g_narrow",
        canonicalTitle = "Grande Sertão: Veredas — Edição Anotada e Revisada",
        primaryAuthors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
        editions = editions,
        suggestedGenreSlug = "fiction",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Grande Sertão",
                hasSearched = true,
                expandedGroupIds = setOf("g_narrow"),
                genresBySlug = mapOf("fiction" to Genre(1L, "fiction", "Fiction", "Ficção")),
                response =
                  HybridSearchResponse(
                    query = "Grande Sertão",
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
    val scanBounds = composeTestRule.onNodeWithTag("search_scan_isbn_button").getBoundsInRoot()
    assertTrue((scanBounds.right - scanBounds.left) >= 47.dp)
    assertTrue((scanBounds.bottom - scanBounds.top) >= 47.dp)
    composeTestRule.onNodeWithTag("search_query_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_3"))
    composeTestRule.onNodeWithTag("search_edition_select_0_3").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w840dp-h900dp")
  fun expandedWidth_uses5EditionColumnsAndContextualDrawerHeaders() {
    val editions =
      (1..5).map { idx ->
        SearchEditionCandidate(
          candidateId = "exp_ed_$idx",
          workTitle = "Memórias Póstumas de Brás Cubas",
          contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
          publisher = "Penguin-Companhia $idx",
          publicationYear = 2010 + idx,
          totalPages = 320 + idx,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        )
      }
    val primary =
      SearchWorkGroup(
        groupId = "g_exp",
        canonicalTitle = "Memórias Póstumas de Brás Cubas",
        primaryAuthors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        editions = editions,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val secondary = sampleWorkGroup(groupId = "sec_exp", title = "Posthumous Memoirs of Brás Cubas")
    val derivative = sampleWorkGroup(groupId = "der_exp", title = "Guia de Leitura: Brás Cubas")

    var secExpanded by mutableStateOf(false)
    var derivExpanded by mutableStateOf(false)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Brás Cubas",
                hasSearched = true,
                expandedGroupIds = setOf("g_exp"),
                isSecondaryLanguagesExpanded = secExpanded,
                isDerivativeWorksExpanded = derivExpanded,
                response =
                  HybridSearchResponse(
                    query = "Brás Cubas",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(primary),
                    secondaryWorkGroups = listOf(secondary),
                    derivativeWorkGroups = listOf(derivative),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 5)),
                  ),
              ),
            widthClass = WindowWidthClass.EXPANDED,
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
            onToggleSecondaryLanguages = { secExpanded = !secExpanded },
            onToggleDerivativeWorks = { derivExpanded = !derivExpanded },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    val row0Tops = (0..4).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Lefts = (0..4).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_secondary_languages_toggle"))
    composeTestRule.onNodeWithTag("search_secondary_languages_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_1"))
    composeTestRule.onNodeWithTag("search_work_card_1").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_derivative_works_toggle"))
    composeTestRule.onNodeWithTag("search_derivative_works_toggle").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_2"))
    composeTestRule.onNodeWithTag("search_work_card_2").assertIsDisplayed()
  }

  @Test
  fun edition3dHold_handlesPartialMetadataEarlyReleaseScrollCancellationAndAccessibility() {
    val editions =
      listOf(
        SearchEditionCandidate(
          candidateId = "ed_full",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = "Record",
          publicationYear = 2019,
          totalPages = 592,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "ed_no_pages",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = "Folha de S.Paulo",
          publicationYear = 2003,
          totalPages = null,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "ed_year_only",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = null,
          publicationYear = 1986,
          totalPages = null,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "ed_extra_1",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = "Bompiani",
          publicationYear = 1980,
          totalPages = 512,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "ed_extra_2",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = "Mariner",
          publicationYear = 2014,
          totalPages = 536,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "ed_extra_3",
          workTitle = "O Nome da Rosa",
          contributors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
          publisher = "Nova Fronteira",
          publicationYear = 1983,
          totalPages = 560,
          sources = listOf(MetadataSource.BRASIL_API),
        ),
      )
    val work =
      SearchWorkGroup(
        groupId = "g_rosa",
        canonicalTitle = "O Nome da Rosa",
        primaryAuthors = listOf(CandidateContributor("Umberto Eco", ContributorRole.AUTHOR)),
        editions = editions,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    var selectedId by mutableStateOf<String?>(null)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "O Nome da Rosa",
                hasSearched = true,
                expandedGroupIds = setOf("g_rosa"),
                selectedEditionId = selectedId,
                response =
                  HybridSearchResponse(
                    query = "O Nome da Rosa",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 6)),
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
            onToggleEditionSelection = { candidate ->
              selectedId = if (selectedId == candidate.candidateId) null else candidate.candidateId
            },
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
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_1"))

    // 1. Partial metadata (publisher + year, no pages): omits pages without placeholder dash
    composeTestRule.onNodeWithTag("search_edition_select_0_1").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_1_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Folha de S.Paulo") and hasAnyAncestor(hasTestTag("search_edition_select_0_1")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2003") and hasAnyAncestor(hasTestTag("search_edition_select_0_1")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("p.", substring = true) and hasAnyAncestor(hasTestTag("search_edition_select_0_1")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule
      .onNode(hasText("—") and hasAnyAncestor(hasTestTag("search_edition_select_0_1")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_1").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()
    assertEquals(null, selectedId)

    // 2. Year-only edition omits missing publisher and pages cleanly
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()
    composeTestRule
      .onNode(hasText("1986") and hasAnyAncestor(hasTestTag("search_edition_select_0_2")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("—") and hasAnyAncestor(hasTestTag("search_edition_select_0_2")), useUnmergedTree = true)
      .assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()

    // 3. Early release mid-flip cancels rotation cleanly back to 0° without triggering selection
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(280) // Past hold start, mid-rotation (~20-35 deg)
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    assertEquals(null, selectedId)

    // 4. Vertical scroll drag across an edition tile does NOT flip or select
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput {
      down(center)
      moveBy(Offset(0f, -140f))
      up()
    }
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    assertEquals(null, selectedId)

    // 5. Accessibility CustomAccessibilityAction toggles verso inspection and dismisses cleanly
    val nodeSemantics = composeTestRule.onNodeWithTag("search_edition_select_0_0").fetchSemanticsNode()
    val customActions = nodeSemantics.config[SemanticsActions.CustomActions]
    assertTrue(customActions.isNotEmpty())
    val inspectAction = customActions.first()
    assertTrue(composeTestRule.runOnIdle { inspectAction.action.invoke() })
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Record") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    val backDesc = composeTestRule.onNodeWithTag("search_edition_select_0_0").fetchSemanticsNode()
      .config[SemanticsProperties.ContentDescription]
      .joinToString()
    assertTrue(backDesc.contains("Record"))
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(400)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    assertTrue(inspectAction.label.isNotBlank())
  }

  @Test
  fun edition3dHold_underReduceMotionUsesCrossfadeCleanlyWithoutSelectionLeak() {
    val work = sampleWorkGroup(groupId = "w_rm", title = "Dom Casmurro", author = "Machado de Assis")
    var selectedId by mutableStateOf<String?>(null)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = true) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Dom Casmurro",
                hasSearched = true,
                expandedGroupIds = setOf("w_rm"),
                selectedEditionId = selectedId,
                response =
                  HybridSearchResponse(
                    query = "Dom Casmurro",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1)),
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
            onToggleEditionSelection = { candidate ->
              selectedId = if (selectedId == candidate.candidateId) null else candidate.candidateId
            },
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
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(450)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
      .onNode(hasText("Rocco") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(250)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0_back_face").assertDoesNotExist()
    assertEquals(null, selectedId)
  }

  @Test
  fun bookThicknessSpec_progressiveBoundedAndFallbackBehavior() {
    val missingSpec = calculateBookThicknessSpec(null)
    val zeroSpec = calculateBookThicknessSpec(0)
    val negativeSpec = calculateBookThicknessSpec(-25)
    assertEquals(6.5f, missingSpec.thicknessDp.value, 0.01f)
    assertEquals(0.5f, missingSpec.normalizedThickness, 0.01f)
    assertEquals(missingSpec.thicknessDp.value, zeroSpec.thicknessDp.value, 0.001f)
    assertEquals(missingSpec.thicknessDp.value, negativeSpec.thicknessDp.value, 0.001f)

    val minFloor = calculateBookThicknessSpec(20)
    val floorBoundary = calculateBookThicknessSpec(60)
    assertEquals(3.5f, minFloor.thicknessDp.value, 0.01f)
    assertEquals(minFloor.thicknessDp.value, floorBoundary.thicknessDp.value, 0.001f)

    val maxCeiling = calculateBookThicknessSpec(1800)
    val ceilingBoundary = calculateBookThicknessSpec(950)
    assertEquals(9.5f, maxCeiling.thicknessDp.value, 0.01f)
    assertEquals(maxCeiling.thicknessDp.value, ceilingBoundary.thicknessDp.value, 0.001f)

    val p80 = calculateBookThicknessSpec(80).thicknessDp.value
    val p220 = calculateBookThicknessSpec(220).thicknessDp.value
    val p480 = calculateBookThicknessSpec(480).thicknessDp.value
    val p900 = calculateBookThicknessSpec(900).thicknessDp.value
    assertTrue(p80 < p220 && p220 < p480 && p480 < p900)

    // Progressive logarithmic curve gives stronger differentiation in common 80..280p range than 700..900p
    val lowDelta = calculateBookThicknessSpec(280).thicknessDp.value - p80
    val highDelta = p900 - calculateBookThicknessSpec(700).thicknessDp.value
    assertTrue(lowDelta > highDelta)
  }

  @Test
  @Config(qualifiers = "w411dp-h915dp-420dpi")
  fun edition3dHold_versoMetadataRendersInThumbSafeUpperBand() {
    val work = sampleWorkGroup(groupId = "w_thumb", title = "A Hora da Estrela", author = "Clarice Lispector")
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = false) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Clarice",
                hasSearched = true,
                expandedGroupIds = setOf("w_thumb"),
                response =
                  HybridSearchResponse(
                    query = "Clarice",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1)),
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
            onToggleEditionSelection = {},
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
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(700)
    composeTestRule.waitForIdle()

    val tileBounds = composeTestRule.onNodeWithTag("search_edition_select_0_0").getBoundsInRoot()
    val tileHeight = tileBounds.bottom - tileBounds.top
    val upperSafeLimit = tileBounds.top + tileHeight * 0.58f

    val publisherBounds =
      composeTestRule
        .onNode(hasText("Rocco") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
        .getBoundsInRoot()
    val yearBounds =
      composeTestRule
        .onNode(hasText("2020") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
        .getBoundsInRoot()
    val pagesBounds =
      composeTestRule
        .onNode(hasText("88", substring = true) and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
        .getBoundsInRoot()

    val diag = "tile=$tileBounds limit=$upperSafeLimit pub=$publisherBounds year=$yearBounds pages=$pagesBounds"
    assertTrue("publisher bottom out of upper band: $diag", publisherBounds.bottom <= upperSafeLimit)
    assertTrue("year bottom out of upper band: $diag", yearBounds.bottom <= upperSafeLimit)
    assertTrue("pages bottom out of upper band: $diag", pagesBounds.bottom <= upperSafeLimit)
    assertTrue("vertical order invalid: $diag", publisherBounds.top < yearBounds.top && yearBounds.top < pagesBounds.top)

    composeTestRule.onNodeWithTag("search_edition_select_0_0").performTouchInput { up() }
    composeTestRule.mainClock.advanceTimeBy(350)
    composeTestRule.waitForIdle()
  }

  @Test
  fun workDrawer_expandsCollapsesSmoothlyAndRecoversFromMidCloseInterruption() {
    val work =
      SearchWorkGroup(
        groupId = "w_drawer",
        canonicalTitle = "Grande Sertão: Veredas",
        primaryAuthors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "w_drawer_ed_1",
              workTitle = "Grande Sertão: Veredas",
              contributors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
              publisher = "Companhia das Letras",
              publicationYear = 2019,
              totalPages = 560,
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "w_drawer_ed_2",
              workTitle = "Grande Sertão: Veredas",
              contributors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
              publisher = "Nova Fronteira",
              publicationYear = 2006,
              totalPages = 624,
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            ),
          ),
        sources = listOf(MetadataSource.GOOGLE_BOOKS, MetadataSource.OPEN_LIBRARY),
        matchTierName = MatchTier.EXACT_WORK.name,
      )
    var expandedIds by mutableStateOf(emptySet<String>())
    var selectedId by mutableStateOf<String?>(null)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = false) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Grande Sertão",
                hasSearched = true,
                expandedGroupIds = expandedIds,
                selectedEditionId = selectedId,
                response =
                  HybridSearchResponse(
                    query = "Grande Sertão",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work),
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
              expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
            },
            onLoadMoreEditions = {},
            onToggleEditionSelection = { candidate ->
              selectedId = if (selectedId == candidate.candidateId) null else candidate.candidateId
            },
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
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()

    // Expand drawer smoothly
    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_1").assertIsDisplayed()

    // Start collapse and interrupt mid-flight (~90ms in) by re-expanding
    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(90)
    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(300)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performClick()
    composeTestRule.waitForIdle()
    assertEquals("w_drawer_ed_1", selectedId)

    // Complete full collapse and verify clean unmount
    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(260)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
  }

  @Test
  fun workDrawer_reduceMotionCollapsesImmediatelyWithoutDanglingRows() {
    val work = sampleWorkGroup(groupId = "w_rm_drawer", title = "Vidas Secas", author = "Graciliano Ramos")
    var expandedIds by mutableStateOf(setOf("w_rm_drawer"))

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
        DipeLibrisTheme(userReduceMotion = true) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Vidas Secas",
                hasSearched = true,
                expandedGroupIds = expandedIds,
                response =
                  HybridSearchResponse(
                    query = "Vidas Secas",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(work),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1)),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = { id ->
              expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
            },
            onLoadMoreEditions = {},
            onToggleEditionSelection = {},
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
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
  }

  @Test
  fun book3dProjection_coherentRigidBodyAcrossFullFlipArc() {
    val widthPx = 280f
    val heightPx = 420f
    val thicknessPx = 24f // ~8.5% of width, realistic hardcover thickness
    val cameraDistPx = 14f * 3f * 72f
    val liftMaxPx = 7.5f
    val cornerRadiusPx = 18f
    val boardOverhangPx = 6.6f

    val angles = listOf(0f, 30f, 60f, 80f, 90f, 100f, 120f, 150f, 180f)
    val projections =
      angles.map { deg ->
        calculateBook3dProjection(
          widthPx = widthPx,
          heightPx = heightPx,
          thicknessPx = thicknessPx,
          flipProgress = deg / 180f,
          pressProgress = 0f,
          cameraDistPx = cameraDistPx,
          liftOffsetMaxPx = liftMaxPx,
          foreCornerRadiusPx = cornerRadiusPx,
          boardOverhangPx = boardOverhangPx,
          reduceMotion = false,
        )
      }

    // Validate finite coordinates and continuous non-negative slab thickness at every angle
    projections.forEachIndexed { idx, p ->
      val deg = angles[idx]
      assertFalse("NaN at $deg", p.slabFrontX.isNaN() || p.slabBackX.isNaN() || p.slabTopY.isNaN())
      assertTrue("Negative slabWidth at $deg", p.slabWidthPx >= 0f)
      assertTrue("Slab inverted vertically at $deg", p.slabBottomY > p.slabTopY)
      assertTrue(
        "Slab protrudes above cover fore-edge at $deg (${p.slabTopY} vs ${p.projectedEdgeTopY})",
        p.slabTopY >= p.projectedEdgeTopY - 0.01f,
      )
      assertTrue(
        "Slab protrudes below cover fore-edge at $deg (${p.slabBottomY} vs ${p.projectedEdgeBottomY})",
        p.slabBottomY <= p.projectedEdgeBottomY + 0.01f,
      )
      assertTrue(
        "Board rim protrudes above cover height at $deg",
        p.boardOuterTopY >= p.projectedEdgeTopY - 0.01f && p.boardOuterBotY <= p.projectedEdgeBottomY + 0.01f,
      )
    }

    // 0° & 180° rest poles: zero slab, zero horizontal offset, flat facing cover
    val at0 = projections[0]
    val at180 = projections.last()
    assertFalse(at0.isSlabVisible)
    assertEquals(0f, at0.frontRotationY, 0.001f)
    assertEquals(0f, at0.frontTranslationX, 0.001f)
    assertEquals(1f, at0.frontAlpha, 0.001f)
    assertEquals(0f, at0.backAlpha, 0.001f)

    assertFalse(at180.isSlabVisible)
    assertEquals(0f, at180.backRotationY, 0.001f)
    assertEquals(0f, at180.backTranslationX, 0.001f)
    assertEquals(0f, at180.frontAlpha, 0.001f)
    assertEquals(1f, at180.backAlpha, 0.001f)

    // 0° < θ < 90° (30°, 60°, 80°): Front rotates negatively so fore-edge is closer (> spine),
    // and slabFrontX aligns exactly with Front's projected right fore-edge
    for (idx in 1..3) {
      val deg = angles[idx]
      val p = projections[idx]
      val rad = deg * PI / 180.0
      assertFalse(p.isShowingBack)
      assertTrue(p.isSlabVisible)
      assertEquals(-deg, p.frontRotationY, 0.001f)
      assertTrue("Fore-edge must be closer than spine at $deg", p.foreEdgeScale > 1f && p.spineEdgeScale < 1f)
      val expectedFrontRightEdge =
        (widthPx * 0.5f) + (widthPx * 0.5f * p.combinedScale * cos(rad).toFloat()) * p.foreEdgeScale + p.frontTranslationX
      assertEquals("Front/Slab seam mismatch at $deg", expectedFrontRightEdge, p.slabFrontX, 0.01f)
      // At oblique angles, slabTopY respects 6dp corner curve clearance inside cover height
      assertTrue("Insufficient corner clearance at $deg", p.slabTopY - p.projectedEdgeTopY >= boardOverhangPx)
    }

    // 90° crossover: centered slab == thickness * combinedScale, zero shadow jump, full-height board rims with overhanging boards
    val at90 = projections[4]
    assertEquals(0f, at90.absCos, 0.001f)
    assertTrue(at90.isSlabVisible)
    assertEquals(widthPx * 0.5f, (at90.slabFrontX + at90.slabBackX) * 0.5f, 0.01f)
    assertEquals(thicknessPx * at90.combinedScale, at90.slabWidthPx, 0.05f)
    assertEquals(0f, at90.frontShadowFactor, 0.001f)
    assertEquals(0f, at90.backShadowFactor, 0.001f)
    assertEquals(at90.projectedEdgeTopY, at90.boardOuterTopY, 0.01f)
    assertEquals(at90.projectedEdgeBottomY, at90.boardOuterBotY, 0.01f)
    assertTrue(at90.slabTopY - at90.projectedEdgeTopY >= boardOverhangPx * 0.9f)

    // 90° < θ < 180° (100°, 120°, 150°): Verso rotates +180-deg so its left fore-edge is closer (> spine),
    // and slabBackX aligns exactly with Verso's projected left fore-edge
    for (idx in 5..7) {
      val deg = angles[idx]
      val p = projections[idx]
      val backAngleRad = (180f - deg) * PI / 180.0
      assertTrue(p.isShowingBack)
      assertTrue(p.isSlabVisible)
      assertEquals(180f - deg, p.backRotationY, 0.001f)
      assertTrue("Fore-edge must remain closer than spine at $deg", p.foreEdgeScale > 1f && p.spineEdgeScale < 1f)
      val expectedBackLeftEdge =
        (widthPx * 0.5f) - (widthPx * 0.5f * p.combinedScale * cos(backAngleRad).toFloat()) * p.foreEdgeScale + p.backTranslationX
      assertEquals("Verso/Slab seam mismatch at $deg", expectedBackLeftEdge, p.slabBackX, 0.01f)
    }

    // Symmetry check: slab width at 30° == slab width at 150°, 60° == 120°, 80° == 100° (modulo small lift ramp)
    assertTrue(abs(projections[1].slabWidthPx - thicknessPx * sin(30.0 * PI / 180.0).toFloat() * projections[1].combinedScale) < 0.05f)
    assertTrue(abs(projections[6].slabWidthPx - thicknessPx * sin(120.0 * PI / 180.0).toFloat() * projections[6].combinedScale) < 0.05f)
  }

  @Test
  fun book3dProjection_handlesSquareAspectAndReduceMotionCleanly() {
    val squareMid =
      calculateBook3dProjection(
        widthPx = 300f,
        heightPx = 300f,
        thicknessPx = 19.5f,
        flipProgress = 0.5f,
        pressProgress = 0f,
        cameraDistPx = 14f * 3f * 72f,
        liftOffsetMaxPx = 7.5f,
        foreCornerRadiusPx = 18f,
        boardOverhangPx = 6.6f,
        reduceMotion = false,
      )
    assertTrue(squareMid.isSlabVisible)
    assertEquals(150f, (squareMid.slabFrontX + squareMid.slabBackX) * 0.5f, 0.01f)
    assertTrue(squareMid.slabTopY > squareMid.projectedEdgeTopY)
    assertTrue(squareMid.slabBottomY < squareMid.projectedEdgeBottomY)

    val reduced =
      calculateBook3dProjection(
        widthPx = 280f,
        heightPx = 420f,
        thicknessPx = 24f,
        flipProgress = 0.5f,
        pressProgress = 0f,
        cameraDistPx = 14f * 3f * 72f,
        liftOffsetMaxPx = 7.5f,
        foreCornerRadiusPx = 18f,
        boardOverhangPx = 6.6f,
        reduceMotion = true,
      )
    assertFalse(reduced.isSlabVisible)
    assertEquals(0f, reduced.frontRotationY, 0.001f)
    assertEquals(0f, reduced.backRotationY, 0.001f)
    assertEquals(0.5f, reduced.frontAlpha, 0.001f)
    assertEquals(0.5f, reduced.backAlpha, 0.001f)
  }
}
