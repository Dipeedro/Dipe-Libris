package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
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
@Config(sdk = [36], qualifiers = "w360dp-h780dp")
class SearchVisualPolishTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @androidx.compose.runtime.Composable
  private fun ProvidePtBrLocale(content: @androidx.compose.runtime.Composable () -> Unit) {
    val baseContext = LocalContext.current
    val localizedContext =
      androidx.compose.runtime.remember(baseContext) {
        LocaleManager.createLocalizedContext(baseContext, AppLanguage.PORTUGUESE_BR)
      }
    val localizedConfig = androidx.compose.runtime.remember(localizedContext) { localizedContext.resources.configuration }
    CompositionLocalProvider(
      LocalContext provides localizedContext,
      LocalConfiguration provides localizedConfig,
      LocalAppLanguage provides AppLanguage.PORTUGUESE_BR,
      content = content,
    )
  }

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
        slug = "fantasy-scifi",
        nameEn = "Fantasy & Sci-Fi",
        namePt = "Fantasia e ficção científica",
      )

    composeTestRule.setContent {
      ProvidePtBrLocale {
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
    val metaLineNode =
      composeTestRule.onNode(
        hasText("2001 · 2 edições") and hasAnyAncestor(cardTag),
        useUnmergedTree = true,
      )
    val coverNode = composeTestRule.onNodeWithTag("search_work_cover_0", useUnmergedTree = true)
    val expandNode = composeTestRule.onNodeWithTag("search_work_expand_0", useUnmergedTree = true)

    titleNode.assertIsDisplayed()
    composeTestRule
      .onNode(hasText("J. R. R. Tolkien, Alan Lee") and hasAnyAncestor(cardTag), useUnmergedTree = true)
      .assertIsDisplayed()
    metaLineNode.assertIsDisplayed()
    coverNode.assertIsDisplayed()
    expandNode.assertIsDisplayed()

    // Compact cover (~64dp x 96dp), text right of cover, vertically centered chevron on right, card height ~120dp
    val cardBounds = composeTestRule.onNodeWithTag("search_work_card_0").getBoundsInRoot()
    val coverBounds = coverNode.getBoundsInRoot()
    val titleBounds = titleNode.getBoundsInRoot()
    val metaBounds = metaLineNode.getBoundsInRoot()
    val expandBounds = expandNode.getBoundsInRoot()
    val coverWidth = (coverBounds.right - coverBounds.left).value
    val coverHeight = (coverBounds.bottom - coverBounds.top).value
    assertTrue("Expected compact width ~64dp, was $coverWidth", coverWidth in 60f..68f)
    assertTrue("Expected compact height ~96dp, was $coverHeight", coverHeight in 92f..100f)
    assertTrue(titleBounds.left > coverBounds.right)
    assertTrue(metaBounds.top > titleBounds.bottom)
    assertTrue((cardBounds.bottom - cardBounds.top) <= 145.dp)

    // Chevron sits vertically centered on the right edge (not in footer)
    val cardCenterY = (cardBounds.top + cardBounds.bottom) / 2f
    val expandCenterY = (expandBounds.top + expandBounds.bottom) / 2f
    assertTrue(kotlin.math.abs((cardCenterY - expandCenterY).value) <= 6f)
    assertTrue(expandBounds.left > titleBounds.right)

    // Excluded noisy items and genre on search card
    composeTestRule.onNode(hasText("Fantasia", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Terceiro Autor Oculto", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Volume Único", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("HarperCollins", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("9786555113631") and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNode(hasText("Na biblioteca", substring = true) and hasAnyAncestor(cardTag), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun closedEditorialCard_displaysLanguageCodeOnlyWhenDifferentFromAppDefault() {
    val englishEdition =
      SearchEditionCandidate(
        candidateId = "ed_en",
        workTitle = "Dune",
        contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
        publisher = "Ace",
        publicationYear = 1998,
        totalPages = 680,
        language = "en",
        canonicalLanguageTag = "en",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val ptEdition =
      SearchEditionCandidate(
        candidateId = "ed_pt",
        workTitle = "Duna",
        contributors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
        publisher = "Aleph",
        publicationYear = 2017,
        totalPages = 680,
        language = "pt-br",
        canonicalLanguageTag = "pt-br",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val groupEn =
      SearchWorkGroup(
        groupId = "g_en",
        canonicalTitle = "Dune",
        primaryAuthors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
        editions = List(6) { englishEdition.copy(candidateId = "ed_en_$it") },
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val groupPt =
      SearchWorkGroup(
        groupId = "g_pt",
        canonicalTitle = "Duna",
        primaryAuthors = listOf(CandidateContributor("Frank Herbert", ContributorRole.AUTHOR)),
        editions = listOf(ptEdition),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    composeTestRule.setContent {
      ProvidePtBrLocale {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Dune",
                hasSearched = true,
                response =
                  HybridSearchResponse(
                    query = "Dune",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(groupEn, groupPt),
                    sourceStatuses = listOf(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 7)),
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
    composeTestRule.onNodeWithText("2 obras").assertIsDisplayed()
    composeTestRule
      .onNode(hasText("1998 · 6 edições · EN") and hasAnyAncestor(hasTestTag("search_work_card_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2017 · 1 edição") and hasAnyAncestor(hasTestTag("search_work_card_1")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("BR", substring = true) and hasAnyAncestor(hasTestTag("search_work_card_1")), useUnmergedTree = true)
      .assertDoesNotExist()
  }

  @Test
  fun expandedWorkCard_rendersFixed3ColumnGalleryHidesHeaderCoverHandlesMissingFields2StepSelectionAndCollapse() {
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
          publicationYear = 2004,
          totalPages = null,
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
    var selectedEdition: SearchEditionCandidate? by mutableStateOf(null)
    var confirmedEditionId: String? = null

    composeTestRule.setContent {
      ProvidePtBrLocale {
        DipeLibrisTheme(userReduceMotion = true) {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Duna",
                hasSearched = true,
                expandedGroupIds = expandedGroupIds,
                selectedEdition = selectedEdition,
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
            onSelectEdition = { confirmedEditionId = it.candidateId },
            onToggleEditionSelection = { cand ->
              selectedEdition = if (selectedEdition?.candidateId == cand.candidateId) null else cand
            },
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
    composeTestRule.onNodeWithTag("search_work_cover_0", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_selection_bottom_bar").assertDoesNotExist()

    // Clicking anywhere on the card expands it and removes the duplicate header cover
    composeTestRule.onNodeWithTag("search_work_card_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_work_cover_0", useUnmergedTree = true).assertDoesNotExist()

    // Work title remains in header, NEVER repeated inside edition tiles
    composeTestRule
      .onNode(
        hasText("Duna") and
          hasAnyAncestor(hasTestTag("search_work_card_0")) and
          !hasAnyAncestor(hasTestTag("search_work_cover_0")),
        useUnmergedTree = true,
      )
      .assertIsDisplayed()

    // Row 0 (tiles 0..2): 3-column horizontal alignment and uniform row height
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    for (idx in 0..2) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
      composeTestRule
        .onNode(hasText("Duna") and hasAnyAncestor(hasTestTag("search_edition_select_0_$idx")), useUnmergedTree = true)
        .assertDoesNotExist()
    }
    val row0Tops = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row0Bottoms = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().bottom }
    val row0Lefts = (0..2).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row0Tops.all { kotlin.math.abs((it - row0Tops.first()).value) <= 2f })
    assertTrue(row0Bottoms.all { kotlin.math.abs((it - row0Bottoms.first()).value) <= 2f })
    assertTrue(row0Lefts.zipWithNext().all { (a, b) -> b > a })

    // Formatting: "680 p. · 2017", year-only "2004" when pages missing, NEVER "—"
    composeTestRule
      .onNode(hasText("Aleph") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("680 p. · 2017") and hasAnyAncestor(hasTestTag("search_edition_select_0_0")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onNode(hasText("2004") and hasAnyAncestor(hasTestTag("search_edition_select_0_1")), useUnmergedTree = true)
      .assertIsDisplayed()
    composeTestRule
      .onAllNodes(hasText("—", substring = true) and hasAnyAncestor(hasTestTag("search_work_card_0")), useUnmergedTree = true)
      .fetchSemanticsNodes()
      .let { assertEquals(0, it.size) }

    // 2-step selection: tap selects tile & shows fixed bottom bar; tap again deselects; tap + CTA confirms
    composeTestRule.onNodeWithTag("search_edition_select_0_2").performClick()
    composeTestRule.waitForIdle()
    assertEquals(null, confirmedEditionId)
    composeTestRule.onNodeWithTag("search_edition_select_0_2_selected_badge", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_selection_bottom_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_confirm_edition_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("search_edition_select_0_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_selection_bottom_bar").assertDoesNotExist()

    composeTestRule.onNodeWithTag("search_edition_select_0_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_confirm_edition_button").performClick()
    assertEquals("ed_3", confirmedEditionId)

    // Row 1 (tiles 3..5): aligned under columns 0, 1, 2
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_3"))
    for (idx in 3..5) {
      composeTestRule.onNodeWithTag("search_edition_select_0_$idx").assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("search_edition_select_0_6").assertDoesNotExist()
    val row1Tops = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().top }
    val row1Lefts = (3..5).map { composeTestRule.onNodeWithTag("search_edition_select_0_$it").getBoundsInRoot().left }
    assertTrue(row1Tops.all { kotlin.math.abs((it - row1Tops.first()).value) <= 2f })
    row0Lefts.zip(row1Lefts).forEach { (left0, left1) ->
      assertTrue(kotlin.math.abs((left0 - left1).value) <= 2f)
    }

    // Footer text button "Recolher" collapses group and restores header cover
    composeTestRule.onNodeWithTag("search_work_collapse_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Recolher").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_collapse_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_edition_select_0_0").assertDoesNotExist()
    composeTestRule.onNodeWithTag("search_work_cover_0", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun expandedGallery_renders8EditionsInAligned3ColumnRowsWithLongTitleAndUniformPublishers() {
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
      ProvidePtBrLocale {
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
  }

  @Test
  fun expandedGallery_textFooterActionsSecondaryLanguagesAndRemoteLoadMoreWorkCleanly() {
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
      ProvidePtBrLocale {
        DipeLibrisTheme(userReduceMotion = true) {
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
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_collapse_0"))
    composeTestRule.waitForIdle()

    // Footer has two text actions ("Buscar mais edições" on left, "Recolher" on right), min 48dp touch height, no old icon bar
    composeTestRule.onNodeWithTag("search_edition_add_more_0").assertDoesNotExist()
    composeTestRule.onNodeWithText("Buscar mais edições").assertIsDisplayed()
    composeTestRule.onNodeWithText("Recolher").assertIsDisplayed()
    val moreBounds = composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").getBoundsInRoot()
    val collapseBounds = composeTestRule.onNodeWithTag("search_work_collapse_0").getBoundsInRoot()
    assertTrue((moreBounds.bottom - moreBounds.top) >= 48.dp)
    assertTrue((collapseBounds.bottom - collapseBounds.top) >= 48.dp)
    assertTrue(collapseBounds.left > moreBounds.right)

    // First click reveals secondary-language editions; second click triggers remote load more
    composeTestRule.onNodeWithTag("search_work_secondary_editions_toggle_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_2"))
    composeTestRule.onNodeWithTag("search_edition_select_0_2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_3").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_load_more_0"))
    composeTestRule.onNodeWithTag("search_edition_load_more_0").performClick()
    assertTrue(loadMoreCalled)
  }

  @Test
  fun unifiedSecondaryActionButtons_shareSameHeightAndFormat() {
    val primary = sampleWorkGroup(groupId = "p1", title = "Harry Potter e a Pedra Filosofal")
    val derivative = sampleWorkGroup(groupId = "d1", title = "Guia Não Oficial de Harry Potter")

    composeTestRule.setContent {
      ProvidePtBrLocale {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Harry Potter",
                hasSearched = true,
                response =
                  HybridSearchResponse(
                    query = "Harry Potter",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(primary),
                    derivativeWorkGroups = listOf(derivative),
                    expandedSecondarySources = false,
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
    composeTestRule.onNodeWithText("1 obra").assertIsDisplayed()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_derivative_works_toggle"))
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_expand_sources_button"))
    val derivBounds = composeTestRule.onNodeWithTag("search_derivative_works_toggle").getBoundsInRoot()
    val expandSourcesBounds = composeTestRule.onNodeWithTag("search_expand_sources_button").getBoundsInRoot()
    val derivHeight = (derivBounds.bottom - derivBounds.top).value
    val expandHeight = (expandSourcesBounds.bottom - expandSourcesBounds.top).value
    assertTrue(derivHeight >= 48f)
    assertTrue(expandHeight >= 48f)
    assertTrue(kotlin.math.abs(derivHeight - expandHeight) <= 2f)
  }

  @Test
  fun expandedGallery_repeatedExpandCollapseAndReduceMotionRenderCleanlyAcrossMultipleWorks() {
    val work1 = sampleWorkGroup(groupId = "w1", title = "Dom Casmurro", author = "Machado de Assis")
    val work2 = sampleWorkGroup(groupId = "w2", title = "Vidas Secas", author = "Graciliano Ramos")
    var expandedGroupIds by mutableStateOf(emptySet<String>())

    composeTestRule.setContent {
      ProvidePtBrLocale {
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
  fun searchQueryInput_longTextResetsToStartCleanlyOnSubmit() {
    var query by mutableStateOf("")
    var submitted = false

    composeTestRule.setContent {
      ProvidePtBrLocale {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState = BookSearchUiState(query = query),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = { query = it },
            onSubmitSearch = { submitted = true },
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

    val longQuery = "Harry Potter e a pedra filosofal"
    composeTestRule.onNodeWithTag("search_query_input").performClick()
    composeTestRule.onNodeWithTag("search_query_input").performTextInput(longQuery)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_query_input").performImeAction()
    composeTestRule.waitForIdle()

    assertTrue(submitted)
    assertEquals(longQuery, query)
    composeTestRule.onNodeWithTag("search_query_input").assertIsDisplayed()
  }
}
