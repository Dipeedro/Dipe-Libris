package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.QueryIntentParser
import com.example.core.bibliographic.domain.SearchActivityCoordinator
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverPlaceholderDetector
import com.example.core.data.local.media.CoverUpgradeCoordinator
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.di.BibliographicDraftStore
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LanguageConfidence
import com.example.core.model.LanguageTier
import com.example.core.model.LibraryViewMode
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.ReadingStatus
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.core.model.TypographyPreset
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import com.example.feature.library.BookSearchViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchStabilityAndCoverFixTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: DipeLibrisDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("dipe_cover_upgrade_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun workGroupingEngine_neverProducesDuplicateGroupIdsAcrossBarrier3bAndInlineSubtitles() {
    val intent = QueryIntentParser.parse("Vou te receitar um gato")

    // Case 1: Same externalWorkKey split by Barreira #3b because inline subtitle differs & subtitle field is null
    val sharedWorkKey = "/works/OL99999W"
    val mainEdition =
      SearchEditionCandidate(
        candidateId = "ol_cat_main",
        workTitle = "Vou te receitar um gato",
        contributors = emptyList(),
        isbn13 = "9786555601111",
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = sharedWorkKey,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val guideWithInlineSubtitle =
      SearchEditionCandidate(
        candidateId = "ol_cat_guide",
        workTitle = "Vou te receitar um gato: Guia ilustrado de cuidados clinicos e bem-estar felino",
        contributors = emptyList(),
        isbn13 = "9786555602222",
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        externalWorkKey = sharedWorkKey,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    // Case 2: Different source without externalWorkKey sharing same base title + inline subtitle (>40 chars)
    val googleVariant =
      SearchEditionCandidate(
        candidateId = "gb_cat_companion",
        workTitle = "Vou te receitar um gato: Caderno complementar de atividades terapeuticas e leitura",
        contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
        isbn13 = "9786555603333",
        language = "pt",
        canonicalLanguageTag = "pt-br",
        languageTierRank = LanguageTier.PT_BR.rank,
        languageConfidenceName = LanguageConfidence.CONFIRMED.name,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(mainEdition, guideWithInlineSubtitle, googleVariant),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )

    val allGroups = outcome.primaryGroups + outcome.secondaryGroups + outcome.derivativeGroups
    assertTrue("Expected at least 2 separate work groups", allGroups.size >= 2)
    val uniqueIds = allGroups.map { it.groupId }.toSet()
    assertEquals(
      "All SearchWorkGroup instances must have strictly unique groupIds",
      allGroups.size,
      uniqueIds.size,
    )
  }

  @Test
  fun coverPlaceholderDetector_safelyHandlesRecycledAndDrawableInputsWithoutThrowing() {
    val recycledBmp = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
    recycledBmp.recycle()

    // Must return false cleanly instead of throwing IllegalStateException on recycled bitmap
    assertFalse(CoverPlaceholderDetector.isKnownGooglePlaceholderBitmap(recycledBmp))
    assertFalse(
      CoverPlaceholderDetector.isKnownGooglePlaceholderDrawable(
        BitmapDrawable(context.resources, recycledBmp)
      )
    )

    val tinyBmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    assertTrue(CoverPlaceholderDetector.isKnownGooglePlaceholderBitmap(tinyBmp))
    tinyBmp.recycle()

    val validBmp = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply {
      Canvas(this).drawColor(Color.rgb(42, 95, 160))
    }
    assertFalse(
      CoverPlaceholderDetector.isKnownGooglePlaceholderDrawable(
        BitmapDrawable(context.resources, validBmp)
      )
    )
    validBmp.recycle()
  }

  @Test
  fun bookSearchScreen_survivesPartialToFinalWithCollidingGroupIdsAndShowsDiscreteWarningBanner() {
    val collidingId = "wg_vou_te_receitar_samehash"
    val work1 =
      SearchWorkGroup(
        groupId = collidingId,
        canonicalTitle = "Vou te receitar um gato",
        primaryAuthors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
        editions = listOf(SearchEditionCandidate(candidateId = "ed1", workTitle = "Vou te receitar um gato")),
      )
    val work2 =
      SearchWorkGroup(
        groupId = collidingId,
        canonicalTitle = "Vou te receitar um gato: Guia complementar",
        primaryAuthors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "ed2",
              workTitle = "Vou te receitar um gato: Guia complementar",
            )
          ),
      )

    var uiState by mutableStateOf(
      BookSearchUiState(
        query = "Vou te receitar um gato",
        isSearching = true,
        hasSearched = true,
        response =
          HybridSearchResponse(
            query = "Vou te receitar um gato",
            isIsbnQuery = false,
            isFromCache = false,
            isOffline = false,
            workGroups = listOf(work1),
            isPartialResult = true,
          ),
      )
    )
    var retryCount = 0

    composeTestRule.setContent {
      DipeLibrisTheme {
        BookSearchScreen(
          uiState = uiState,
          widthClass = WindowWidthClass.COMPACT,
          onQueryChange = {},
          onSubmitSearch = { retryCount++ },
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
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()

    // Simulate Emission 3 introducing a colliding groupId + discrete partial failure warning
    uiState =
      uiState.copy(
        isSearching = false,
        hasPartialFailureWarning = true,
        response =
          HybridSearchResponse(
            query = "Vou te receitar um gato",
            isIsbnQuery = false,
            isFromCache = false,
            isOffline = false,
            workGroups = listOf(work1, work2),
            isPartialResult = false,
          ),
      )
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_work_card_1").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Vou te receitar um gato").assertCountEquals(2)
    composeTestRule.onNodeWithText("Vou te receitar um gato: Guia complementar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_partial_warning_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_partial_warning_retry").performClick()
    assertEquals(1, retryCount)

    // Verify full recoverable error state when zero works are present
    uiState =
      BookSearchUiState(
        query = "Vou te receitar um gato",
        isSearching = false,
        hasSearched = true,
        response = null,
        hasSearchError = true,
      )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_error_retry_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_error_retry_button").performClick()
    assertEquals(2, retryCount)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun bookSearchViewModel_preservesPartialResultsOnFinalErrorAndReleasesSearchCoordinator(): Unit = runBlocking {
    val testDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    try {
      val libraryRepo =
        RoomLibraryRepository(
          bookDao = db.bookDao(),
          shelfDao = db.shelfDao(),
          editionDao = db.editionDao(),
          copyDao = db.copyDao(),
          readingJourneyDao = db.readingJourneyDao(),
          database = db,
          genreDao = db.genreDao(),
          contributorDao = db.contributorDao(),
          metadataSuggestionDao = db.metadataSuggestionDao(),
        )
      val emptyAdapter = { src: MetadataSource ->
        object : BibliographicSourceAdapter {
          override val source: MetadataSource = src
          override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String) =
            SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
          override suspend fun searchByQuery(query: String) =
            SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
        }
      }

      val partialWork =
        SearchWorkGroup(
          groupId = "wg_partial_cat",
          canonicalTitle = "Vou te receitar um gato",
          primaryAuthors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
          editions = listOf(SearchEditionCandidate(candidateId = "ed_p", workTitle = "Vou te receitar um gato")),
        )

      val coordinator = SearchActivityCoordinator()

      val failingFinalOrchestrator =
        object : HybridSearchOrchestrator(
          openLibraryAdapter = emptyAdapter(MetadataSource.OPEN_LIBRARY),
          googleBooksAdapter = emptyAdapter(MetadataSource.GOOGLE_BOOKS),
          crossrefAdapter = emptyAdapter(MetadataSource.CROSSREF),
          wikidataAdapter = emptyAdapter(MetadataSource.WIKIDATA),
          brasilApiAdapter = emptyAdapter(MetadataSource.BRASIL_API),
          connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
          searchCacheDao = db.searchCacheDao(),
          editionDao = db.editionDao(),
          bookDao = db.bookDao(),
          libraryRepository = libraryRepo,
        ) {
          override fun searchProgressive(
            rawQuery: String,
            preferredLanguage: String?,
            expandSecondary: Boolean,
            forceRefresh: Boolean,
          ): Flow<HybridSearchResponse> = flow {
            assertTrue("Coordinator must report search active during search flow", coordinator.isSearchActive.value)
            emit(
              HybridSearchResponse(
                query = rawQuery,
                isIsbnQuery = false,
                isFromCache = false,
                isOffline = false,
                workGroups = listOf(partialWork),
                isPartialResult = true,
              )
            )
            delay(50)
            throw IllegalStateException("Simulated final grouping failure")
          }
        }

      val prefsRepo =
        object : PreferencesRepository {
          private val flowState = MutableStateFlow(UserPreferences(language = AppLanguage.PORTUGUESE_BR))
          override val preferences: Flow<UserPreferences> = flowState
          override suspend fun setLanguage(language: AppLanguage) {}
          override suspend fun setThemeMode(themeMode: AppThemeMode) {}
          override suspend fun setTypographyPreset(preset: TypographyPreset) {}
          override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {}
          override suspend fun setReduceMotion(reduceMotion: Boolean) {}
          override suspend fun setReaderIdentity(name: String, bio: String) {}
        }

      val vm =
        BookSearchViewModel(
          initialQuery = null,
          orchestrator = failingFinalOrchestrator,
          libraryRepository = libraryRepo,
          preferencesRepository = prefsRepo,
          draftStore = BibliographicDraftStore(),
          searchActivityCoordinator = coordinator,
        )

      val collectorJob = launch(testDispatcher) { vm.uiState.collect {} }
      try {
        vm.setQuery("Vou te receitar um gato")
        vm.submitSearch()
        repeat(40) {
          testDispatcher.scheduler.advanceTimeBy(25)
          testDispatcher.scheduler.runCurrent()
          if (!vm.uiState.value.isSearching && vm.uiState.value.hasPartialFailureWarning) return@repeat
          delay(10)
        }

        val state = vm.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.hasPartialFailureWarning)
        assertFalse(state.hasSearchError)
        assertEquals(1, state.response?.workGroups?.size)
        assertEquals("Vou te receitar um gato", state.response?.workGroups?.first()?.canonicalTitle)
        assertFalse("Coordinator must return to idle after search completes or fails", coordinator.isSearchActive.value)
      } finally {
        collectorJob.cancel()
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun coverUpgradeCoordinator_pausesWhileSearchActivityCoordinatorIsActive(): Unit = runBlocking {
    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    val lowResFile = File(coversDir, "cover_pause_test.jpg")
    val bmp = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888).apply {
      Canvas(this).drawColor(Color.rgb(20, 80, 120))
    }
    val rawBytes =
      ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        out.toByteArray()
      }
    FileOutputStream(lowResFile).use { it.write(rawBytes) }

    val libraryRepo =
      RoomLibraryRepository(
        bookDao = db.bookDao(),
        shelfDao = db.shelfDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        readingJourneyDao = db.readingJourneyDao(),
        database = db,
        genreDao = db.genreDao(),
        contributorDao = db.contributorDao(),
        metadataSuggestionDao = db.metadataSuggestionDao(),
      )
    libraryRepo.addBook(
      title = "O Cortiço",
      author = "Aluísio Azevedo",
      totalPages = 220,
      publicationYear = 2020,
      editionNote = "Ática",
      isbn = "9788508172221",
      coverUri = lowResFile.absolutePath,
      status = ReadingStatus.UNREAD,
    )

    var editionDaoReads = 0
    val emptyAdapter =
      object : BibliographicSourceAdapter {
        override val source: MetadataSource = MetadataSource.OPEN_LIBRARY
        override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String): SourceAdapterResult {
          editionDaoReads++
          return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
        }
        override suspend fun searchByQuery(query: String) =
          SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
    val quietAdapter = { src: MetadataSource ->
      object : BibliographicSourceAdapter {
        override val source: MetadataSource = src
        override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String) =
          SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
        override suspend fun searchByQuery(query: String) =
          SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
      }
    }

    val connectivity = object : ConnectivityChecker { override fun isOnline(): Boolean = true }
    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = emptyAdapter,
        googleBooksAdapter = quietAdapter(MetadataSource.GOOGLE_BOOKS),
        crossrefAdapter = quietAdapter(MetadataSource.CROSSREF),
        wikidataAdapter = quietAdapter(MetadataSource.WIKIDATA),
        brasilApiAdapter = quietAdapter(MetadataSource.BRASIL_API),
        connectivityChecker = connectivity,
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )
    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()
        }
        .build()
    val searchCoordinator = SearchActivityCoordinator()
    searchCoordinator.onSearchStarted()

    val fakePrefsRepo =
      object : PreferencesRepository {
        private val flowState = MutableStateFlow(UserPreferences(language = AppLanguage.PORTUGUESE_BR))
        override val preferences: Flow<UserPreferences> = flowState
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setThemeMode(themeMode: AppThemeMode) {}
        override suspend fun setTypographyPreset(preset: TypographyPreset) {}
        override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {}
        override suspend fun setReduceMotion(reduceMotion: Boolean) {}
        override suspend fun setReaderIdentity(name: String, bio: String) {}
      }

    val upgradeCoordinator =
      CoverUpgradeCoordinator(
        context = context,
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        coverStorageManager = InternalCoverStorageManager(context, fakeClient),
        orchestrator = orchestrator,
        preferencesRepository = fakePrefsRepo,
        connectivityChecker = connectivity,
        searchActivityCoordinator = searchCoordinator,
      )

    val upgradeJob = async(Dispatchers.Default) { upgradeCoordinator.upgradeLowResCoversIfNeeded() }
    delay(120)
    assertEquals("Upgrade must pause while search is active", 0, editionDaoReads)

    searchCoordinator.onSearchFinished()
    upgradeJob.await()
    assertTrue("Upgrade should resume once active search finishes", editionDaoReads > 0)
    lowResFile.delete()
  }
}
