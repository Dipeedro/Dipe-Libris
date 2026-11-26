package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.DuplicateDetector
import com.example.core.bibliographic.domain.GenreSuggester
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.MetadataDiffAnalyzer
import com.example.core.bibliographic.domain.SynopsisSelector
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.GoogleBooksAdapter
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.db.MetadataSuggestionEntity
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.DefaultMetadataUpdateRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.di.BibliographicDraftStore
import com.example.core.di.BookReviewMode
import com.example.core.di.ReviewDraftPayload
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocaleManager
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.BibliographicMath
import com.example.core.model.BookCopy
import com.example.core.model.BookReviewSubmission
import com.example.core.model.CandidateContributor
import com.example.core.model.CatalogBook
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.DuplicateAssessment
import com.example.core.model.Edition
import com.example.core.model.Genre
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LibraryViewMode
import com.example.core.model.MetadataSource
import com.example.core.model.MetadataSuggestion
import com.example.core.model.MetadataSuggestionScope
import com.example.core.model.MetadataSuggestionStatus
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.ReadingStatus
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import com.example.core.model.TypographyPreset
import com.example.core.model.Work
import com.example.feature.library.BookReviewScreen
import com.example.feature.library.BookReviewViewModel
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import com.example.feature.library.BookSearchViewModel
import com.example.feature.library.IsbnScannerScreen
import com.example.feature.library.MetadataSuggestionsSheet
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeBibPrefsRepository : PreferencesRepository {
  private val state = MutableStateFlow(UserPreferences(language = AppLanguage.ENGLISH))
  override val preferences: Flow<UserPreferences> = state

  override suspend fun setLanguage(language: AppLanguage) {
    state.update { it.copy(language = language) }
  }

  override suspend fun setThemeMode(themeMode: AppThemeMode) {}

  override suspend fun setTypographyPreset(preset: TypographyPreset) {}

  override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {}

  override suspend fun setReduceMotion(reduceMotion: Boolean) {}

  override suspend fun setReaderIdentity(name: String, bio: String) {}
}

private class NoOpCoverStorageManager : CoverStorageManager {
  override suspend fun importCoverFromUri(sourceUri: Uri): String? = null

  override suspend fun downloadAndImportCover(imageUrl: String): String? = null

  override suspend fun deleteCoverIfManaged(coverUri: String?) {}
}

private class FakeSourceAdapter(
  override val source: MetadataSource,
  private val resultCandidates: List<SearchEditionCandidate> = emptyList(),
) : BibliographicSourceAdapter {
  override suspend fun searchByIsbn(
    isbn13: String?,
    isbn10: String?,
    rawIsbn: String,
  ): SourceAdapterResult =
    SourceAdapterResult(
      status = SourceQueryStatus(source, ProviderOutcomeState.SUCCESS, resultCandidates.size),
      candidates = resultCandidates,
    )

  override suspend fun searchByQuery(query: String): SourceAdapterResult =
    SourceAdapterResult(
      status = SourceQueryStatus(source, ProviderOutcomeState.SUCCESS, resultCandidates.size),
      candidates = resultCandidates,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BibliographicDomainAndUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .addCallback(DipeLibrisDatabase.SEED_CALLBACK)
        .allowMainThreadQueries()
        .build()
    // Ensure genres seeded immediately for in-memory database
    DipeLibrisDatabase.seedDefaultGenres(db.openHelper.writableDatabase)
    libraryRepo =
      RoomLibraryRepository(
        bookDao = db.bookDao(),
        shelfDao = db.shelfDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        readingJourneyDao = db.readingJourneyDao(),
        database = db,
        coverStorageManager = NoOpCoverStorageManager(),
        genreDao = db.genreDao(),
        contributorDao = db.contributorDao(),
        metadataSuggestionDao = db.metadataSuggestionDao(),
      )
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun bibliographicMathValidatesConvertsAndIdentifiesBrazilianIsbn() {
    val parsed13 = BibliographicMath.parseIsbn("978-85-359-1484-9")
    assertNotNull(parsed13)
    assertEquals("9788535914849", parsed13!!.isbn13)
    assertEquals("8535914846", parsed13.isbn10)
    assertTrue(BibliographicMath.isBrazilianIsbn("978-85-359-1484-9"))

    val parsed10 = BibliographicMath.parseIsbn("0140449132")
    assertNotNull(parsed10)
    assertEquals("0140449132", parsed10!!.isbn10)
    assertEquals("9780140449136", parsed10.isbn13)
    assertFalse(BibliographicMath.isBrazilianIsbn("0140449132"))

    // Invalid check digit rejected
    assertNull(BibliographicMath.parseIsbn("9788535914840"))
    assertNull(BibliographicMath.parseIsbn("123456"))

    assertEquals("memorias postumas", BibliographicMath.normalizeText("Memórias Póstumas!"))
    assertEquals("cortico", BibliographicMath.normalizeTitleCore("O Cortiço (Edição Crítica)"))
  }

  @Test
  fun workGroupingMergesSameEditionAndGroupsEditionsUnderSingleWork() {
    val openLibraryEd1 =
      SearchEditionCandidate(
        candidateId = "ol_1",
        workTitle = "Dom Casmurro",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        publisher = "Editora 34",
        publicationYear = 2016,
        totalPages = 280,
        isbn13 = "9788535914849",
        language = "pt",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
        externalWorkKey = "OL1234W",
        categories = listOf("Literary Fiction", "Classics"),
      )
    val googleDuplicateOfEd1 =
      SearchEditionCandidate(
        candidateId = "gb_1",
        workTitle = "Dom Casmurro",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        publisher = "Editora 34",
        publicationYear = 2016,
        isbn13 = "9788535914849",
        language = "pt",
        coverCandidates =
          listOf(
            CoverCandidate(
              imageUrl = "http://books.google.com/cover.jpg",
              source = MetadataSource.GOOGLE_BOOKS,
              matchesIsbn = true,
            )
          ),
        synopsisCandidates =
          listOf(
            SynopsisCandidate(
              text = "Bento Santiago narra a história de seu ciúme por Capitu ao longo da juventude no Rio de Janeiro imperial.",
              language = "pt",
              source = MetadataSource.GOOGLE_BOOKS,
            )
          ),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val englishEdition =
      SearchEditionCandidate(
        candidateId = "ol_en",
        workTitle = "Dom Casmurro",
        contributors =
          listOf(
            CandidateContributor("Machado de Assis", ContributorRole.AUTHOR),
            CandidateContributor("Helen Caldwell", ContributorRole.TRANSLATOR),
          ),
        publisher = "Oxford University Press",
        publicationYear = 1998,
        isbn13 = "9780140449136",
        language = "en",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
        externalWorkKey = "OL1234W",
      )

    val groups =
      WorkGroupingEngine.groupCandidates(
        rawCandidates = listOf(openLibraryEd1, googleDuplicateOfEd1, englishEdition),
        preferredLanguage = "pt",
      )

    assertEquals(1, groups.size)
    val workGroup = groups.first()
    assertEquals(2, workGroup.editions.size)
    val ptEdition = workGroup.editions.first { it.isbn13 == "9788535914849" }
    assertTrue(MetadataSource.OPEN_LIBRARY in ptEdition.sources)
    assertTrue(MetadataSource.GOOGLE_BOOKS in ptEdition.sources)
    assertEquals("https://books.google.com/cover.jpg", ptEdition.bestCover?.imageUrl)
  }

  @Test
  fun coverRankerSynopsisSelectorAndGenreSuggesterPrioritizeHighestQualitySignals() {
    val covers =
      CoverRanker.rankAndDeduplicate(
        listOf(
          CoverCandidate("https://example.com/no_cover.jpg", MetadataSource.OPEN_LIBRARY, matchesIsbn = true),
          CoverCandidate("https://example.com/thumb-S.jpg", MetadataSource.OPEN_LIBRARY, matchesIsbn = false),
          CoverCandidate(
            "http://example.com/isbn-L.jpg",
            MetadataSource.GOOGLE_BOOKS,
            matchesIsbn = true,
            widthHint = 600,
            heightHint = 900,
          ),
        )
      )
    assertEquals(2, covers.size)
    assertEquals("https://example.com/isbn-L.jpg", covers.first().imageUrl)

    val synopses =
      SynopsisSelector.rankAndDeduplicate(
        candidates =
          listOf(
            SynopsisCandidate(
              text = "An incomplete description in English that cuts off...",
              language = "en",
              source = MetadataSource.OPEN_LIBRARY,
            ),
            SynopsisCandidate(
              text = "<p>Uma narrativa clássica e envolvente sobre memória, amizade e conflitos humanos no Brasil do século XIX.</p>",
              language = "pt",
              source = MetadataSource.GOOGLE_BOOKS,
            ),
          ),
        preferredLanguage = "pt",
        editionLanguage = "pt",
      )
    assertEquals(2, synopses.size)
    assertTrue(synopses.first().text.startsWith("Uma narrativa clássica"))
    assertFalse(synopses.first().text.contains("<p>"))

    runBlocking {
      val genres = db.genreDao().getAllGenres().map { Genre(it.id, it.slug, it.namePt, it.nameEn, it.isCustom) }
      val sciFi = GenreSuggester.matchGenre(listOf("Science Fiction & Fantasy"), genres)
      assertEquals("fantasy-scifi", sciFi?.slug)
      val poetry = GenreSuggester.matchGenre(listOf("Poesia Brasileira"), genres)
      assertEquals("poetry", poetry?.slug)
    }
  }

  @Test
  fun duplicateDetectorDistinguishesExactSameWorkUncertainAndNoMatch() {
    val existingBook =
      CatalogBook(
        work = Work(id = 1L, title = "Dom Casmurro", author = "Machado de Assis", externalWorkKey = "OL1W"),
        primaryEdition =
          Edition(
            id = 10L,
            workId = 1L,
            isbn13 = "9788535914849",
            isbn10 = "8535914846",
            isPrimary = true,
          ),
        primaryCopy = BookCopy(id = 20L, editionId = 10L),
        copyCount = 2,
      )
    val matchedEntity =
      EditionEntity(
        id = 10L,
        workId = 1L,
        isbn13 = "9788535914849",
        isbn10 = "8535914846",
      )

    val exact =
      DuplicateDetector.assessRaw(
        isbn13 = "9788535914849",
        isbn10 = null,
        externalWorkKey = null,
        title = "Dom Casmurro",
        author = "Machado de Assis",
        localCatalog = listOf(existingBook),
        matchedIsbnEditions = listOf(matchedEntity),
        copyCountsByEditionId = mapOf(10L to 2),
      )
    assertTrue(exact is DuplicateAssessment.ExactEditionMatch)
    assertEquals(2, (exact as DuplicateAssessment.ExactEditionMatch).existingCopiesCount)

    val sameWorkDiffEd =
      DuplicateDetector.assessRaw(
        isbn13 = "9780140449136",
        isbn10 = null,
        externalWorkKey = "OL1W",
        title = "Dom Casmurro (Penguin)",
        author = "Machado de Assis",
        localCatalog = listOf(existingBook),
        matchedIsbnEditions = emptyList(),
        copyCountsByEditionId = emptyMap(),
      )
    assertTrue(sameWorkDiffEd is DuplicateAssessment.SameWorkDifferentEditionMatch)

    val uncertain =
      DuplicateDetector.assessRaw(
        isbn13 = null,
        isbn10 = null,
        externalWorkKey = null,
        title = "Dom Casmurro",
        author = "Autor Desconhecido",
        localCatalog = listOf(existingBook),
        matchedIsbnEditions = emptyList(),
        copyCountsByEditionId = emptyMap(),
      )
    assertTrue(uncertain is DuplicateAssessment.UncertainWorkMatch)

    val noMatch =
      DuplicateDetector.assessRaw(
        isbn13 = null,
        isbn10 = null,
        externalWorkKey = null,
        title = "The Pragmatic Programmer",
        author = "David Thomas",
        localCatalog = listOf(existingBook),
        matchedIsbnEditions = emptyList(),
        copyCountsByEditionId = emptyMap(),
      )
    assertEquals(DuplicateAssessment.NoMatch, noMatch)
  }

  @Test
  fun metadataDiffProtectsUserEditsAndTracksAcceptedAndRejectedSuggestions() = runBlocking {
    val genres = db.genreDao().getAllGenres()
    val workId =
      libraryRepo.saveBookFromReview(
        BookReviewSubmission(
          title = "Vidas Secas",
          contributors = listOf(CandidateContributor("Graciliano Ramos", ContributorRole.AUTHOR)),
          primaryGenreId = genres.first().id,
          activeSynopsis = "Sinopse escrita manualmente pelo leitor.",
          isSynopsisCustomized = true,
          publisherOrNote = "Record",
          isbn13 = "9788535914849",
          coverSource = "MANUAL",
          selectedLocalCoverUri = "/local/custom_cover.jpg",
          isCoverCustomized = true,
        )
      )

    val foundCandidate =
      SearchEditionCandidate(
        candidateId = "cand_update",
        workTitle = "Vidas Secas",
        subtitle = "Edição Comemorativa",
        contributors =
          listOf(
            CandidateContributor("Graciliano Ramos", ContributorRole.AUTHOR),
            CandidateContributor("Paulo Honório", ContributorRole.TRANSLATOR),
          ),
        publisher = "Record",
        publicationYear = 2019,
        totalPages = 176,
        isbn13 = "9788535914849",
        coverCandidates =
          listOf(CoverCandidate("https://example.com/new.jpg", MetadataSource.OPEN_LIBRARY, matchesIsbn = true)),
        synopsisCandidates =
          listOf(
            SynopsisCandidate(
              text = "Sinopse externa muito mais longa que não deve sobrescrever a sinopse customizada do usuário em hipótese alguma.",
              language = "pt",
              source = MetadataSource.OPEN_LIBRARY,
            )
          ),
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = FakeSourceAdapter(MetadataSource.OPEN_LIBRARY, listOf(foundCandidate)),
        googleBooksAdapter = FakeSourceAdapter(MetadataSource.GOOGLE_BOOKS),
        crossrefAdapter = FakeSourceAdapter(MetadataSource.CROSSREF),
        wikidataAdapter = FakeSourceAdapter(MetadataSource.WIKIDATA),
        brasilApiAdapter = FakeSourceAdapter(MetadataSource.BRASIL_API),
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )

    val updateRepo =
      DefaultMetadataUpdateRepository(
        libraryRepository = libraryRepo,
        orchestrator = orchestrator,
        suggestionDao = db.metadataSuggestionDao(),
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        coverStorageManager = NoOpCoverStorageManager(),
        database = db,
      )

    val createdCount = updateRepo.checkUpdatesForWork(workId, "pt")
    assertTrue(createdCount >= 3) // subtitle, publicationYear, totalPages, translator

    val pending = libraryRepo.observeBookDetail(workId).first()!!.pendingSuggestions
    // Custom synopsis and custom cover MUST NOT produce overwrite suggestions
    assertFalse(pending.any { it.fieldName == MetadataDiffAnalyzer.FIELD_SYNOPSIS })
    assertFalse(pending.any { it.fieldName == MetadataDiffAnalyzer.FIELD_COVER_URL })

    val pagesSuggestion = pending.first { it.fieldName == MetadataDiffAnalyzer.FIELD_TOTAL_PAGES }
    updateRepo.acceptSuggestion(pagesSuggestion.id)

    val yearSuggestion = pending.first { it.fieldName == MetadataDiffAnalyzer.FIELD_PUBLICATION_YEAR }
    updateRepo.rejectSuggestion(yearSuggestion.id)

    val updatedDetail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(176, updatedDetail.editions.first().edition.totalPages)
    assertNull(updatedDetail.editions.first().edition.publicationYear)

    // Re-running check must NOT re-suggest the rejected year value
    updateRepo.checkUpdatesForWork(workId, "pt")
    val afterRecheck = libraryRepo.observeBookDetail(workId).first()!!.pendingSuggestions
    assertFalse(afterRecheck.any { it.fieldName == MetadataDiffAnalyzer.FIELD_PUBLICATION_YEAR })
  }

  @Test
  fun bookReviewScreenSectionsToggleIndependentlyAndCustomGenrePersists() {
    val draftStore = BibliographicDraftStore()
    draftStore.setReviewDraft(ReviewDraftPayload(mode = BookReviewMode.MANUAL))
    val vm =
      BookReviewViewModel(
        modeArg = "manual",
        targetWorkIdArg = null,
        prefillArg = null,
        libraryRepository = libraryRepo,
        coverStorageManager = NoOpCoverStorageManager(),
        preferencesRepository = FakeBibPrefsRepository(),
        draftStore = draftStore,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          val state by vm.uiState.collectAsState()
          BookReviewScreen(
            uiState = state,
            widthClass = WindowWidthClass.COMPACT,
            viewModel = vm,
            onNavigateBack = {},
            onSavedWork = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    // Work section starts expanded
    composeTestRule.onNodeWithTag("review_title_input").assertIsDisplayed()

    // Collapse Section 1 (Work & Contributors) -> Title hides, but Section 3 (Edition) & Section 6 (Copy) remain open
    composeTestRule.onNodeWithTag("review_section_header_work").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("review_title_input").assertDoesNotExist()
    composeTestRule
      .onNodeWithTag("book_review_lazy_column")
      .performScrollToNode(hasTestTag("review_publisher_input"))
    composeTestRule.onNodeWithTag("review_publisher_input").assertIsDisplayed()

    // Create a custom genre inside Section 4
    composeTestRule
      .onNodeWithTag("book_review_lazy_column")
      .performScrollToNode(hasTestTag("review_create_genre_button"))
    composeTestRule.onNodeWithTag("review_create_genre_button").performClick()
    composeTestRule.onNodeWithTag("review_custom_genre_input").performTextInput("Crônicas Brasileiras")
    composeTestRule.onNodeWithTag("review_custom_genre_confirm").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("review_custom_genre_input")).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithText("Crônicas Brasileiras").assertIsDisplayed()

    // Expand Collector details block inside Section 6
    composeTestRule
      .onNodeWithTag("book_review_lazy_column")
      .performScrollToNode(hasTestTag("review_collector_details_toggle"))
    composeTestRule
      .onNodeWithTag("review_collector_details_toggle")
      .performScrollTo()
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("review_copy_code_input")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule
      .onNodeWithTag("book_review_lazy_column")
      .performScrollToNode(hasTestTag("review_copy_code_input"))
    composeTestRule
      .onNodeWithTag("review_copy_code_input")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun metadataSuggestionsSheetDisplaysCurrentVsFoundAndTriggersCallbacks() {
    var acceptedId: Long? = null
    var rejectedId: Long? = null
    val suggestion =
      MetadataSuggestion(
        id = 7L,
        workId = 1L,
        editionId = 10L,
        scope = MetadataSuggestionScope.EDITION,
        fieldName = MetadataDiffAnalyzer.FIELD_TOTAL_PAGES,
        currentValue = null,
        suggestedValue = "320",
        sourceName = "Open Library",
        reason = MetadataDiffAnalyzer.REASON_MISSING_FIELD,
        confidenceScore = 80,
        status = MetadataSuggestionStatus.PENDING,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          MetadataSuggestionsSheet(
            suggestions = listOf(suggestion),
            onAccept = { acceptedId = it },
            onReject = { rejectedId = it },
            onDismiss = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("metadata_suggestions_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("suggestion_card_7").assertIsDisplayed()
    composeTestRule.onNodeWithTag("suggestion_accept_7").performClick()
    assertEquals(7L, acceptedId)
    composeTestRule.onNodeWithTag("suggestion_reject_7").performClick()
    assertEquals(7L, rejectedId)
  }

  @Test
  fun searchScreenShowsOfflineBannerExpandsWorksAndScannerValidatesManualIsbn() {
    var selectedCandidate: SearchEditionCandidate? = null
    val candidate =
      SearchEditionCandidate(
        candidateId = "cand_1",
        workTitle = "Grande Sertão: Veredas",
        contributors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
        publisher = "Companhia das Letras",
        publicationYear = 2019,
        isbn13 = "9788535914849",
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val group =
      SearchWorkGroup(
        groupId = "work_1",
        canonicalTitle = "Grande Sertão: Veredas",
        primaryAuthors = listOf(CandidateContributor("João Guimarães Rosa", ContributorRole.AUTHOR)),
        editions = listOf(candidate),
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    var expandedGroups = emptySet<String>()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Grande Sertão",
                hasSearched = true,
                expandedGroupIds = expandedGroups,
                response =
                  HybridSearchResponse(
                    query = "Grande Sertão",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = true,
                    workGroups = listOf(group),
                  ),
              ),
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = {},
            onSubmitSearch = {},
            onExpandSecondarySources = {},
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = { expandedGroups = expandedGroups + it },
            onLoadMoreEditions = {},
            onSelectEdition = { selectedCandidate = it },
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

    composeTestRule.onNodeWithTag("search_offline_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_expand_0").performClick()
    assertTrue("work_1" in expandedGroups)
  }

  @Test
  fun isbnScannerManualFallbackValidatesInvalidAndDeliversValidIsbn() {
    var detected: String? = null
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          IsbnScannerScreen(
            widthClass = WindowWidthClass.COMPACT,
            onIsbnDetected = { detected = it },
            onNavigateBack = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("scanner_manual_isbn_input").performScrollTo().performTextInput("12345")
    composeTestRule.onNodeWithTag("scanner_manual_isbn_submit").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("scanner_manual_isbn_error").assertIsDisplayed()
    assertNull(detected)

    composeTestRule.onNodeWithTag("scanner_manual_isbn_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("scanner_manual_isbn_input").performTextInput("978-85-359-1484-9")
    composeTestRule.onNodeWithTag("scanner_manual_isbn_submit").performScrollTo().performClick()
    assertEquals("9788535914849", detected)
  }

  @Test
  fun googleBooksAdapterParsesVolumesDistinguishesErrorsAndSkipsOnlyWithoutKey() = runBlocking {
    val sampleJson =
      """
      {
        "kind": "books#volumes",
        "totalItems": 1,
        "items": [
          {
            "id": "wQIKEQAAQBAJ",
            "volumeInfo": {
              "title": "Vou te receitar um gato",
              "authors": ["Syou Ishida"],
              "publisher": "Editora Intrinseca",
              "publishedDate": "2024-07-03",
              "description": "<p>Best-seller japonês na Clínica Kokoro.</p>",
              "industryIdentifiers": [
                {"type": "ISBN_13", "identifier": "9788551009796"},
                {"type": "ISBN_10", "identifier": "8551009796"}
              ],
              "pageCount": 209,
              "categories": ["Fiction"],
              "imageLinks": {
                "thumbnail": "http://books.google.com/books/content?id=wQIKEQAAQBAJ&zoom=1&edge=curl"
              },
              "language": "pt-BR"
            }
          }
        ]
      }
      """
        .trimIndent()

    var capturedUrl: String? = null
    var nextCode = 200
    var nextBody = sampleJson
    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          capturedUrl = chain.request().url.toString()
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(nextCode)
            .message(if (nextCode == 200) "OK" else "Error")
            .body(nextBody.toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    // 1. Placeholder or blank key falls back to keyless search successfully
    val skippedAdapter =
      GoogleBooksAdapter(client = fakeClient, apiKeyProvider = { "MY_GOOGLE_BOOKS_API_KEY" })
    assertEquals(
      ProviderOutcomeState.SUCCESS,
      skippedAdapter.searchByQuery("Vou te receitar um gato").status.state,
    )

    // 2. Valid key executes GET with &key=... and parses full volume metadata
    val activeAdapter = GoogleBooksAdapter(client = fakeClient, apiKeyProvider = { "test-key-123" })
    val okResult = activeAdapter.searchByQuery("Vou te receitar um gato")
    assertEquals(ProviderOutcomeState.SUCCESS, okResult.status.state)
    assertEquals(1, okResult.candidates.size)
    assertTrue(capturedUrl.orEmpty().contains("key=test-key-123"))
    val candidate = okResult.candidates.first()
    assertEquals("Vou te receitar um gato", candidate.workTitle)
    assertEquals("Syou Ishida", candidate.contributors.first().name)
    assertEquals("Editora Intrinseca", candidate.publisher)
    assertEquals(2024, candidate.publicationYear)
    assertEquals(209, candidate.totalPages)
    assertEquals("9788551009796", candidate.isbn13)
    assertEquals("8551009796", candidate.isbn10)
    assertTrue(candidate.coverCandidates.first().imageUrl.startsWith("https://"))
    assertFalse(candidate.coverCandidates.first().imageUrl.contains("edge=curl"))
    assertEquals("Best-seller japonês na Clínica Kokoro.", candidate.synopsisCandidates.first().text)

    // 3. HTTP 403 quota -> RATE_LIMITED, HTTP 403 invalid key -> ERROR
    nextCode = 403
    nextBody = """{"error":{"code":403,"message":"Quota exceeded"}}"""
    assertEquals(
      ProviderOutcomeState.RATE_LIMITED,
      activeAdapter.searchByQuery("gato").status.state,
    )

    nextBody = """{"error":{"code":403,"message":"Books API has not been used in project"}}"""
    val errResult = activeAdapter.searchByQuery("gato")
    assertEquals(ProviderOutcomeState.ERROR, errResult.status.state)
    assertEquals("HTTP 403", errResult.status.message)
  }

  @Test
  fun bookSearchScreenWithGoogleBooksAdapterDisplaysResultsAndStatusChip() {
    val sampleJson =
      """
      {
        "kind": "books#volumes",
        "totalItems": 1,
        "items": [
          {
            "id": "wQIKEQAAQBAJ",
            "volumeInfo": {
              "title": "Vou te receitar um gato",
              "authors": ["Syou Ishida"],
              "publisher": "Editora Intrinseca",
              "publishedDate": "2024-07-03",
              "industryIdentifiers": [
                {"type": "ISBN_13", "identifier": "9788551009796"}
              ],
              "pageCount": 209,
              "language": "pt-BR"
            }
          }
        ]
      }
      """
        .trimIndent()
    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(sampleJson.toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    val draftStore = BibliographicDraftStore()
    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = FakeSourceAdapter(MetadataSource.OPEN_LIBRARY),
        googleBooksAdapter = GoogleBooksAdapter(client = fakeClient, apiKeyProvider = { "test-key" }),
        crossrefAdapter = FakeSourceAdapter(MetadataSource.CROSSREF),
        wikidataAdapter = FakeSourceAdapter(MetadataSource.WIKIDATA),
        brasilApiAdapter = FakeSourceAdapter(MetadataSource.BRASIL_API),
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )
    val vm =
      BookSearchViewModel(
        initialQuery = null,
        orchestrator = orchestrator,
        libraryRepository = libraryRepo,
        preferencesRepository = FakeBibPrefsRepository(),
        draftStore = draftStore,
      )

    var navigatedToReview = false
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          val state by vm.uiState.collectAsState()
          BookSearchScreen(
            uiState = state,
            widthClass = WindowWidthClass.COMPACT,
            onQueryChange = vm::setQuery,
            onSubmitSearch = { vm.submitSearch(false) },
            onExpandSecondarySources = { vm.submitSearch(true) },
            onOpenScanner = {},
            onManualCatalog = {},
            onToggleGroupExpanded = vm::toggleGroupExpanded,
            onLoadMoreEditions = vm::loadMoreEditionsForWork,
            onSelectEdition = { cand ->
              vm.selectEditionCandidate(cand) { navigatedToReview = true }
            },
            onOpenExistingBook = {},
            onDismissDuplicate = vm::dismissDuplicatePrompt,
            onDuplicateAddCopy = { _, _, _ -> },
            onDuplicateAddEdition = { _, _ -> },
            onDuplicateSaveSeparately = {},
            onNavigateBack = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("search_query_input").performTextInput("Vou te receitar um gato")
    composeTestRule.onNodeWithTag("search_query_input").performImeAction()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("search_work_card_0")).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Google Books (1)").assertDoesNotExist()
    composeTestRule
      .onNode(hasText("Vou te receitar um gato") and hasAnyAncestor(hasTestTag("search_work_card_0")))
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Syou Ishida").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_sources_status_toggle"))
    composeTestRule.onNodeWithTag("search_sources_status_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_bottom_source_google_books", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_sources_status_toggle").performClick()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_sources_status_details"))
    composeTestRule.onNodeWithTag("search_sources_status_details").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_0"))
    composeTestRule.onNodeWithTag("search_edition_select_0_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_confirm_edition_button").assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToReview }
    assertEquals("Vou te receitar um gato", draftStore.pendingReviewDraft.value?.candidate?.workTitle)
  }
}
