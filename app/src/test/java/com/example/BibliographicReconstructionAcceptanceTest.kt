package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.QueryIntentParser
import com.example.core.bibliographic.domain.QueryPlanner
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.bibliographic.network.AdapterSearchRequest
import com.example.core.bibliographic.network.AppleBooksAdapter
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.CrossrefAdapter
import com.example.core.bibliographic.network.GoogleBooksAdapter
import com.example.core.bibliographic.network.OpenLibraryAdapter
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.bibliographic.network.WikidataAdapter
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LibraryViewMode
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.core.model.SynopsisCandidate
import com.example.core.model.TitleBundleKind
import com.example.core.model.TypographyPreset
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
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

private class AcceptancePrefsRepo : PreferencesRepository {
  private val state = MutableStateFlow(UserPreferences(language = AppLanguage.PORTUGUESE_BR))
  override val preferences: Flow<UserPreferences> = state

  override suspend fun setLanguage(language: AppLanguage) {}
  override suspend fun setThemeMode(themeMode: AppThemeMode) {}
  override suspend fun setTypographyPreset(preset: TypographyPreset) {}
  override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {}
  override suspend fun setReduceMotion(reduceMotion: Boolean) {}
  override suspend fun setReaderIdentity(name: String, bio: String) {}
}

private class AcceptanceNoOpCoverStorage : CoverStorageManager {
  override suspend fun importCoverFromUri(sourceUri: Uri): String? = null
  override suspend fun deleteCoverIfManaged(coverUri: String?) {}
}

private class FakeAdapter(
  override val source: MetadataSource,
  private val handler: suspend (AdapterSearchRequest) -> SourceAdapterResult,
) : BibliographicSourceAdapter {
  val calls = AtomicInteger(0)

  override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String): SourceAdapterResult {
    calls.incrementAndGet()
    return SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
  }

  override suspend fun searchByQuery(query: String): SourceAdapterResult {
    calls.incrementAndGet()
    return handler(AdapterSearchRequest(query, QueryIntentParser.parse(query)))
  }

  override suspend fun searchPaginated(request: AdapterSearchRequest): SourceAdapterResult {
    calls.incrementAndGet()
    return handler(request)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BibliographicReconstructionAcceptanceTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var context: Context
  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .addCallback(DipeLibrisDatabase.SEED_CALLBACK)
        .allowMainThreadQueries()
        .build()
    DipeLibrisDatabase.seedDefaultGenres(db.openHelper.writableDatabase)
    libraryRepo =
      RoomLibraryRepository(
        bookDao = db.bookDao(),
        shelfDao = db.shelfDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        readingJourneyDao = db.readingJourneyDao(),
        database = db,
        coverStorageManager = AcceptanceNoOpCoverStorage(),
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

  private fun jsonClient(handler: (url: String) -> Pair<Int, String>): OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor { chain ->
        val req = chain.request()
        val (code, body) = handler(req.url.toString())
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(code)
          .message(if (code in 200..299) "OK" else "Error")
          .body(body.toResponseBody("application/json".toMediaType()))
          .build()
      }
      .build()

  @Test
  fun caseAC_jogosVorazesRanksOriginalFirstMovesAcademicReflectionToDerivativeAndBridgesEnglishEdition() {
    val intent = QueryIntentParser.parse("Jogos Vorazes")
    val academicReflection =
      SearchEditionCandidate(
        candidateId = "gb_reflection",
        workTitle = "Jogos Vorazes: uma reflexão sobre o mundo contemporâneo",
        subtitle = "uma reflexão sobre o mundo contemporâneo",
        contributors = listOf(CandidateContributor("Pesquisador Acadêmico", ContributorRole.AUTHOR)),
        language = "pt-BR",
        publisher = "Editora Universitária",
        categories = listOf("Social Science", "Education"),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val ptOriginalRocco =
      SearchEditionCandidate(
        candidateId = "gb_jv_rocco",
        workTitle = "Jogos Vorazes",
        contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788579800245",
        publisher = "Rocco",
        publicationYear = 2010,
        externalWorkKey = "OL5735363W",
        workEditionCount = 120,
        synopsisCandidates = listOf(SynopsisCandidate(text = "Num futuro sombrio...", language = "pt-BR", source = MetadataSource.GOOGLE_BOOKS)),
        coverCandidates = listOf(CoverCandidate("https://books.google.com/jv.jpg", MetadataSource.GOOGLE_BOOKS)),
        sources = listOf(MetadataSource.GOOGLE_BOOKS, MetadataSource.OPEN_LIBRARY),
      )
    val enWorkHungerGames =
      SearchEditionCandidate(
        candidateId = "ol_hg_en",
        workTitle = "The Hunger Games",
        originalTitle = "The Hunger Games",
        contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
        language = "en",
        isbn13 = "9780439023481",
        externalWorkKey = "OL5735363W",
        workEditionCount = 180,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val bridgeEnCandidate =
      SearchEditionCandidate(
        candidateId = "wd_bridge_jv",
        workTitle = "Jogos Vorazes",
        originalTitle = "The Hunger Games",
        contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
        language = "pt-BR",
        externalWorkKey = "OL5735363W",
        externalIds = mapOf("wikidata_work" to "Q11679"),
        sources = listOf(MetadataSource.WIKIDATA),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(academicReflection, enWorkHungerGames, bridgeEnCandidate, ptOriginalRocco),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )

    // Original literary work is #1 and absorbs EN translation via OL5735363W bridge
    assertEquals(1, outcome.primaryGroups.size)
    assertEquals("Jogos Vorazes", outcome.primaryGroups.first().canonicalTitle)
    assertEquals("Suzanne Collins", outcome.primaryGroups.first().displayAuthors)
    assertTrue(outcome.primaryGroups.first().editions.any { it.isbn13 == "9788579800245" })
    assertTrue(outcome.primaryGroups.first().editions.any { it.isbn13 == "9780439023481" })

    // Academic reflection is demoted to derivativeGroups (T7), NOT mixed in primaryGroups
    assertEquals(1, outcome.derivativeGroups.size)
    assertEquals(MatchTier.DERIVATIVE_OR_ACADEMIC, outcome.derivativeGroups.first().effectiveMatchTier)
    assertTrue(outcome.derivativeGroups.first().canonicalTitle.contains("reflexão"))
  }

  @Test
  fun caseBDL_senhorDosAneisBlocksHomographAneisAnaisAndSeparatesVolumesOmnibusAndBox() {
    val intent = QueryIntentParser.parse("O Senhor dos Anéis")

    val homographAnais =
      SearchEditionCandidate(
        candidateId = "bad_anais",
        workTitle = "O Senhor dos Anais",
        contributors = listOf(CandidateContributor("Outro Autor", ContributorRole.AUTHOR)),
        language = "pt-BR",
        publisher = "Editora Aleatória",
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val vol1 =
      SearchEditionCandidate(
        candidateId = "lotr_v1",
        workTitle = "O Senhor dos Anéis: A Sociedade do Anel",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788595084759",
        workEditionCount = 90,
        sources = listOf(MetadataSource.OPEN_LIBRARY, MetadataSource.GOOGLE_BOOKS),
      )
    val vol2 =
      SearchEditionCandidate(
        candidateId = "lotr_v2",
        workTitle = "O Senhor dos Anéis: As Duas Torres",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788595084766",
        workEditionCount = 85,
        sources = listOf(MetadataSource.OPEN_LIBRARY, MetadataSource.GOOGLE_BOOKS),
      )
    val omnibus =
      SearchEditionCandidate(
        candidateId = "lotr_omnibus",
        workTitle = "O Senhor dos Anéis - Volume Único",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788595086357",
        workEditionCount = 60,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val boxTrilogia =
      SearchEditionCandidate(
        candidateId = "lotr_box",
        workTitle = "Box Trilogia O Senhor dos Anéis",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788595086333",
        workEditionCount = 50,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val unrelatedHobbit =
      SearchEditionCandidate(
        candidateId = "hobbit",
        workTitle = "O Hobbit",
        seriesName = "Terra Média",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788595084742",
        workEditionCount = 200,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(homographAnais, boxTrilogia, unrelatedHobbit, vol2, omnibus, vol1),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )

    val allTitles = (outcome.primaryGroups + outcome.secondaryGroups + outcome.derivativeGroups).map { it.canonicalTitle }
    assertFalse("Must reject homograph 'O Senhor dos Anais'", allTitles.any { it.contains("Anais") })
    assertFalse("'O Hobbit' must not pass unrequested title filter", allTitles.any { it == "O Hobbit" })

    // Volumes, Omnibus, and Box remain distinct groups
    assertEquals(4, outcome.primaryGroups.size)
    // Omnibus (EXACT_WORK) comes first, Volumes 1 & 2 follow in order, Box (COLLECTION_OR_BOX) separated
    val first = outcome.primaryGroups[0]
    assertEquals(TitleBundleKind.OMNIBUS_SINGLE_VOLUME, first.effectiveBundleKind)
    assertEquals(TitleBundleKind.COLLECTION_OR_BOX, outcome.primaryGroups.last().effectiveBundleKind)
  }

  @Test
  fun caseE_authorSearchMachadoDeAssisPrioritizesAuthoredWorksOverThirdPartyBiographies() {
    val intent = QueryIntentParser.parse("Machado de Assis")
    val domCasmurro =
      SearchEditionCandidate(
        candidateId = "ma_dom",
        workTitle = "Dom Casmurro",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788535920001",
        workEditionCount = 90,
        sources = listOf(MetadataSource.OPEN_LIBRARY, MetadataSource.GOOGLE_BOOKS),
      )
    val memorias =
      SearchEditionCandidate(
        candidateId = "ma_mem",
        workTitle = "Memórias Póstumas de Brás Cubas",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        language = "pt-BR",
        isbn13 = "9788535920002",
        workEditionCount = 80,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val biographyByOther =
      SearchEditionCandidate(
        candidateId = "bio_machado",
        workTitle = "Machado de Assis: Historiografia e Crítica",
        subtitle = "Estudo Crítico sobre o Bruxo do Cosme Velho",
        contributors = listOf(CandidateContributor("Roberto Schwarz", ContributorRole.AUTHOR)),
        language = "pt-BR",
        categories = listOf("Literary Criticism"),
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )

    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates = listOf(biographyByOther, memorias, domCasmurro),
        preferredLanguage = "pt-BR",
        parsedIntent = intent,
      )

    assertEquals(2, outcome.primaryGroups.size)
    assertTrue(outcome.primaryGroups.all { it.displayAuthors.contains("Machado de Assis") })
    assertEquals(1, outcome.derivativeGroups.size)
    assertEquals("Roberto Schwarz", outcome.derivativeGroups.first().displayAuthors)
  }

  @Test
  fun caseFGP_googleBooksAdapterUsesIntitleOrderByRelevanceCorrectPaginationAndNoKeyQuotaState() = runBlocking {
    val capturedUrls = java.util.concurrent.CopyOnWriteArrayList<String>()
    val client =
      jsonClient { url ->
        capturedUrls += url
        val decoded = URLDecoder.decode(url, "UTF-8")
        if (decoded.contains("startIndex=30")) {
          200 to """{"totalItems":60,"items":[{"id":"gb2","volumeInfo":{"title":"Jogos Vorazes: Em Chamas","authors":["Suzanne Collins"],"language":"pt-BR"}}]}"""
        } else {
          200 to """{"totalItems":60,"items":[{"id":"gb1","volumeInfo":{"title":"Jogos Vorazes","authors":["Suzanne Collins"],"language":"pt-BR"}}]}"""
        }
      }

    // With blank key: 200 OK still returns data (no fake offline block)
    val adapter = GoogleBooksAdapter(client = client, apiKeyProvider = { "" }, baseUrl = "https://mock.gb")
    val intent = QueryIntentParser.parse("Jogos Vorazes")
    val p1 = adapter.searchPaginated(AdapterSearchRequest("Jogos Vorazes", intent, page = 1, pageSize = 30))
    assertEquals(ProviderOutcomeState.SUCCESS, p1.status.state)
    assertTrue(capturedUrls.any { URLDecoder.decode(it, "UTF-8").contains("intitle:\"Jogos Vorazes\"") })
    assertTrue(capturedUrls.all { it.contains("orderBy=relevance") })
    assertFalse(capturedUrls.any { URLDecoder.decode(it, "UTF-8").contains("OR inauthor:") })

    // Page 2 must pass startIndex=30 (= (2-1)*30), never page=2 bug
    val p2 = adapter.searchPaginated(AdapterSearchRequest("Jogos Vorazes", intent, page = 2, pageSize = 30))
    assertEquals(ProviderOutcomeState.SUCCESS, p2.status.state)
    assertTrue(capturedUrls.any { it.contains("startIndex=30") })

    // Verify planner only runs fallback on page 1 when primary hits 0
    val emptyUrls = java.util.concurrent.CopyOnWriteArrayList<String>()
    val emptyClient =
      jsonClient { url ->
        emptyUrls += url
        200 to """{"totalItems":0,"items":[]}"""
      }
    val emptyAdapter = GoogleBooksAdapter(client = emptyClient, apiKeyProvider = { "" }, baseUrl = "https://mock.gb")
    val zeroIntent = QueryIntentParser.parse("Livro Raro Inexistente")
    emptyAdapter.searchPaginated(AdapterSearchRequest("Livro Raro Inexistente", zeroIntent, page = 1, pageSize = 30))
    assertTrue(emptyUrls.size >= 3) // structured + fallback

    // Page 2 with zero primary candidates must NOT invoke fallbackQuery
    val planP2 = QueryPlanner.planGoogleBooks(zeroIntent, page = 2, pageSize = 30)
    assertEquals(null, planP2.fallbackQuery)

    // Without key + HTTP 429 => SKIPPED_NO_API_KEY
    val quotaClient = jsonClient { 429 to """{"error":{"message":"Quota exceeded"}}""" }
    val quotaAdapter = GoogleBooksAdapter(client = quotaClient, apiKeyProvider = { "" }, baseUrl = "https://mock.gb")
    val quotaRes = quotaAdapter.searchPaginated(AdapterSearchRequest("Jogos Vorazes", intent, page = 1, pageSize = 30))
    assertEquals(ProviderOutcomeState.SKIPPED_NO_API_KEY, quotaRes.status.state)
  }

  @Test
  fun caseH_openLibraryEnrichesPopularWorkMissingPtEmbeddedEditions() = runBlocking {
    val calledPaths = mutableListOf<String>()
    val client =
      jsonClient { url ->
        calledPaths += url
        when {
          url.contains("/works/OL5735363W/editions.json") ->
            200 to
              """
              {
                "entries": [
                  {
                    "key": "/books/OL_PT_1M",
                    "title": "Jogos Vorazes",
                    "publishers": ["Rocco"],
                    "isbn_13": ["9788579800245"],
                    "languages": [{"key": "/languages/por"}],
                    "covers": [991122],
                    "number_of_pages": 400,
                    "publish_date": "2010"
                  }
                ]
              }
              """.trimIndent()
          url.contains("search.json") ->
            200 to
              """
              {
                "numFound": 1,
                "docs": [
                  {
                    "key": "/works/OL5735363W",
                    "title": "The Hunger Games",
                    "author_name": ["Suzanne Collins"],
                    "first_publish_year": 2008,
                    "edition_count": 120,
                    "ratings_count": 5000,
                    "language": ["eng", "por"],
                    "cover_i": 880011,
                    "editions": {
                      "numFound": 1,
                      "docs": [
                        {
                          "key": "/books/OL_EN_1M",
                          "title": "The Hunger Games",
                          "language": ["eng"],
                          "isbn": ["9780439023481"],
                          "publish_year": [2008]
                        }
                      ]
                    }
                  }
                ]
              }
              """.trimIndent()
          else -> 404 to "{}"
        }
      }

    val olAdapter = OpenLibraryAdapter(client = client, baseUrl = "https://mock.ol")
    val req = AdapterSearchRequest("Jogos Vorazes", QueryIntentParser.parse("Jogos Vorazes"), page = 1, pageSize = 24)
    val res = olAdapter.searchPaginated(req)

    assertEquals(ProviderOutcomeState.SUCCESS, res.status.state)
    assertTrue("Expected /works/OL5735363W/editions.json enrichment call", calledPaths.any { it.contains("/works/OL5735363W/editions.json") })
    val ptEdition = res.candidates.firstOrNull { it.isbn13 == "9788579800245" }
    assertTrue(ptEdition != null)
    assertEquals("Jogos Vorazes", ptEdition!!.editionTitle)
    assertEquals("The Hunger Games", ptEdition.originalTitle)
    assertEquals("/works/OL5735363W", ptEdition.externalWorkKey)
    val grouped = WorkGroupingEngine.groupCandidates(res.candidates, "pt-BR", req.parsedIntent)
    assertEquals("Jogos Vorazes", grouped.first().canonicalTitle)
  }

  @Test
  fun caseIJ_crossrefDisabledOutsideIsbnDoiAndWikidataMediaWikiBridgeFiltersNonBooks() = runBlocking {
    val crossrefHttpCalls = AtomicInteger(0)
    val crossrefClient =
      jsonClient { _ ->
        crossrefHttpCalls.incrementAndGet()
        200 to
          """
          {"message":{"items":[{"DOI":"10.1234/book.1","title":["Dom Casmurro Critica"],"author":[{"given":"João","family":"Silva"}],"ISBN":["9788500000001"],"type":"book"}]}}
          """.trimIndent()
      }
    val crossref = CrossrefAdapter(client = crossrefClient, baseUrl = "https://mock.cr")

    // General query even with expandSecondary=true MUST make 0 Crossref HTTP calls
    val textRes =
      crossref.searchPaginated(
        AdapterSearchRequest("Jogos Vorazes", QueryIntentParser.parse("Jogos Vorazes"), expandSecondary = true)
      )
    assertEquals(ProviderOutcomeState.EMPTY, textRes.status.state)
    assertEquals(0, crossrefHttpCalls.get())

    // Explicit DOI IS allowed
    val doiRes = crossref.searchByQuery("10.1234/book.1")
    assertEquals(ProviderOutcomeState.SUCCESS, doiRes.status.state)
    assertEquals(1, crossrefHttpCalls.get())

    // Wikidata MediaWiki bridge (wbsearchentities + wbgetentities) resolves book Q11679 and skips film Q212965
    val wikidataClient =
      jsonClient { url ->
        when {
          url.contains("action=wbsearchentities") ->
            200 to
              """
              {
                "search": [
                  {"id": "Q11679", "label": "Jogos Vorazes", "description": "romance de Suzanne Collins"},
                  {"id": "Q212965", "label": "Jogos Vorazes (filme)", "description": "filme de 2012"}
                ]
              }
              """.trimIndent()
          url.contains("action=wbgetentities") ->
            200 to
              """
              {
                "entities": {
                  "Q11679": {
                    "id": "Q11679",
                    "labels": {
                      "pt-br": {"value": "Jogos Vorazes"},
                      "en": {"value": "The Hunger Games"}
                    },
                    "claims": {
                      "P31": [{"mainsnak": {"datavalue": {"value": {"id": "Q7725634"}}}}],
                      "P648": [{"mainsnak": {"datavalue": {"value": "OL5735363W"}}}]
                    }
                  },
                  "Q212965": {
                    "id": "Q212965",
                    "labels": {"pt": {"value": "Jogos Vorazes"}},
                    "claims": {
                      "P31": [{"mainsnak": {"datavalue": {"value": {"id": "Q11424"}}}}]
                    }
                  }
                }
              }
              """.trimIndent()
          else -> 404 to "{}"
        }
      }

    val wikidata = WikidataAdapter(client = wikidataClient, mediaWikiApiUrl = "https://mock.wd/api.php")
    val bridge = wikidata.fetchBilingualBridge("Jogos Vorazes")
    assertEquals(ProviderOutcomeState.SUCCESS, bridge.status.state)
    assertEquals(1, bridge.candidates.size)
    assertEquals("/works/OL5735363W", bridge.candidates.first().externalWorkKey)
    assertEquals("The Hunger Games", bridge.candidates.first().originalTitle)
    assertEquals("Q11679", bridge.candidates.first().externalIds["wikidata_work"])
  }

  @Test
  fun caseK_appleBooksAdapterParsesCountryBrUpgradesArtworkAppliesCooldownAndDefersCoversToOpenSources() = runBlocking {
    val fakeNow = AtomicLong(100_000L)
    val calls = AtomicInteger(0)
    val client =
      jsonClient { _ ->
        val n = calls.incrementAndGet()
        if (n == 1) {
          200 to
            """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackId": 456789,
                  "trackName": "Jogos Vorazes",
                  "artistName": "Suzanne Collins",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Publication/100x100bb.jpg",
                  "description": "<p>Na na&ccedil;&atilde;o de Panem, <b>Katniss</b> entra na arena.</p>",
                  "releaseDate": "2010-09-01T07:00:00Z",
                  "genres": ["Ficção", "Distopia"],
                  "trackViewUrl": "https://books.apple.com/br/book/jogos-vorazes/id456789"
                }
              ]
            }
            """.trimIndent()
        } else {
          429 to "{}"
        }
      }

    val apple = AppleBooksAdapter(client = client, baseUrl = "https://mock.apple", clockMs = { fakeNow.get() })
    val req = AdapterSearchRequest("Jogos Vorazes", QueryIntentParser.parse("Jogos Vorazes"), page = 1)
    val ok = apple.searchPaginated(req)

    assertEquals(ProviderOutcomeState.SUCCESS, ok.status.state)
    val cand = ok.candidates.first()
    assertEquals("Jogos Vorazes", cand.workTitle)
    assertTrue(cand.bestCover!!.imageUrl.contains("600x900bb"))
    assertFalse(cand.synopsisCandidates.first().text.contains("<p>"))
    assertEquals("https://books.apple.com/br/book/jogos-vorazes/id456789", cand.externalIds["apple_books_url"])

    // Open sources must outrank Apple Books covers in CoverRanker
    val openOlCover = CoverCandidate("https://covers.openlibrary.org/b/id/991122-L.jpg", MetadataSource.OPEN_LIBRARY, matchesIsbn = true, matchesLanguage = true)
    val ranked = CoverRanker.rankAndDeduplicate(listOf(cand.bestCover!!, openOlCover))
    assertEquals(MetadataSource.OPEN_LIBRARY, ranked.first().source)

    // Page > 1 does not repeat Apple Books calls
    val p2 = apple.searchPaginated(req.copy(page = 2))
    assertEquals(ProviderOutcomeState.EMPTY, p2.status.state)
    assertEquals(1, calls.get())

    // HTTP 429 activates cooldown circuit breaker so subsequent call short-circuits without network hit
    val rl1 = apple.searchPaginated(req)
    assertEquals(ProviderOutcomeState.RATE_LIMITED, rl1.status.state)
    assertEquals(2, calls.get())
    fakeNow.addAndGet(10_000L)
    val rl2 = apple.searchPaginated(req)
    assertEquals(ProviderOutcomeState.RATE_LIMITED, rl2.status.state)
    assertEquals(2, calls.get()) // still 2: blocked by cooldown
  }

  @Test
  fun caseMN_orchestratorEnforcesBatchDeadlineCacheReGroupingAndPagination() = runBlocking {
    val fastOl =
      FakeAdapter(MetadataSource.OPEN_LIBRARY) { req ->
        SourceAdapterResult(
          SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1, totalAvailable = 5, hasMore = true),
          listOf(
            SearchEditionCandidate(
              candidateId = "ol_${req.page}",
              workTitle = if (req.page == 1) "Jogos Vorazes" else "Em Chamas",
              contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
              language = "pt-BR",
              isbn13 = if (req.page == 1) "9788579800245" else "9788579800726",
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            )
          ),
        )
      }
    val ultraSlowGb =
      FakeAdapter(MetadataSource.GOOGLE_BOOKS) {
        delay(5_000L) // Exceeds PRIMARY_BATCH_DEADLINE_MS (3_200L)
        SourceAdapterResult(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.EMPTY))
      }
    val emptyCr = FakeAdapter(MetadataSource.CROSSREF) { SourceAdapterResult(SourceQueryStatus(MetadataSource.CROSSREF, ProviderOutcomeState.EMPTY)) }
    val emptyWd = FakeAdapter(MetadataSource.WIKIDATA) { SourceAdapterResult(SourceQueryStatus(MetadataSource.WIKIDATA, ProviderOutcomeState.EMPTY)) }
    val emptyBr = FakeAdapter(MetadataSource.BRASIL_API) { SourceAdapterResult(SourceQueryStatus(MetadataSource.BRASIL_API, ProviderOutcomeState.EMPTY)) }

    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = fastOl,
        googleBooksAdapter = ultraSlowGb,
        crossrefAdapter = emptyCr,
        wikidataAdapter = emptyWd,
        brasilApiAdapter = emptyBr,
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )

    // Verify Room cache roundtrip + Crossref bypassed
    val start = System.currentTimeMillis()
    val emissions = orchestrator.searchProgressive("Jogos Vorazes", "pt-BR").toList()
    val elapsed = System.currentTimeMillis() - start

    assertTrue("Batch must complete within bounded deadline + margin, took ${elapsed}ms", elapsed < 4_600L)
    assertEquals(2, emissions.size)
    assertEquals("Jogos Vorazes", emissions.last().workGroups.first().canonicalTitle)
    assertEquals(0, emptyCr.calls.get())

    // Cached second hit uses v3: cache without re-invoking OL/GB
    val olCountAfterFirst = fastOl.calls.get()
    val cachedEmissions = orchestrator.searchProgressive("Jogos Vorazes", "pt-BR").toList()
    assertTrue(cachedEmissions.last().isFromCache)
    assertEquals(olCountAfterFirst, fastOl.calls.get())

    // Negative-cache guard: an all-failed search must NOT overwrite valid cache or persist negative entry
    val brokenOl = FakeAdapter(MetadataSource.OPEN_LIBRARY) { SourceAdapterResult(SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.ERROR)) }
    val brokenGb = FakeAdapter(MetadataSource.GOOGLE_BOOKS) { SourceAdapterResult(SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.ERROR)) }
    val failOrch =
      HybridSearchOrchestrator(
        openLibraryAdapter = brokenOl,
        googleBooksAdapter = brokenGb,
        crossrefAdapter = emptyCr,
        wikidataAdapter = emptyWd,
        brasilApiAdapter = emptyBr,
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )
    val failRes = failOrch.searchProgressive("Consulta Falha Rede", "pt-BR").toList().last()
    assertTrue(failRes.workGroups.isEmpty())
    val secondFailCall = failOrch.searchProgressive("Consulta Falha Rede", "pt-BR").toList().last()
    assertFalse("All-error query must NOT be served from cache!", secondFailCall.isFromCache)
  }

  @Test
  fun caseO_searchScreenDisplaysBadgesAppleAttributionAndCollapsedDerivativeDrawer() {
    val boxGroup =
      SearchWorkGroup(
        groupId = "g_box",
        canonicalTitle = "Box Trilogia O Senhor dos Anéis",
        primaryAuthors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "c_box",
              workTitle = "Box Trilogia O Senhor dos Anéis",
              contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
              language = "pt-BR",
              coverCandidates = listOf(CoverCandidate("https://is1-ssl.mzstatic.com/100x100bb.jpg", MetadataSource.APPLE_BOOKS)),
              externalIds = mapOf("apple_books_url" to "https://books.apple.com/br/book/id123"),
              sources = listOf(MetadataSource.APPLE_BOOKS),
            )
          ),
        bundleKindName = TitleBundleKind.COLLECTION_OR_BOX.name,
      )

    val derivativeGroup =
      SearchWorkGroup(
        groupId = "g_deriv",
        canonicalTitle = "O Senhor dos Anéis: Análise Filosófica",
        primaryAuthors = listOf(CandidateContributor("Autor Acadêmico", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "c_deriv",
              workTitle = "O Senhor dos Anéis: Análise Filosófica",
              contributors = listOf(CandidateContributor("Autor Acadêmico", ContributorRole.AUTHOR)),
              language = "pt-BR",
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            )
          ),
        matchTierName = MatchTier.DERIVATIVE_OR_ACADEMIC.name,
      )

    val response =
      HybridSearchResponse(
        query = "O Senhor dos Anéis",
        isIsbnQuery = false,
        isFromCache = false,
        isOffline = false,
        workGroups = listOf(boxGroup),
        derivativeWorkGroups = listOf(derivativeGroup),
        totalFoundHint = 152,
        sourceStatuses = listOf(SourceQueryStatus(MetadataSource.APPLE_BOOKS, ProviderOutcomeState.SUCCESS, 1)),
      )

    var expandedDerivatives = false
    val uiState =
      BookSearchUiState(
        query = "O Senhor dos Anéis",
        hasSearched = true,
        isSearching = false,
        response = response,
        isDerivativeWorksExpanded = false,
      )

    composeTestRule.setContent {
      DipeLibrisTheme {
        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.PORTUGUESE_BR) {
          BookSearchScreen(
            uiState = uiState,
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
            onToggleDerivativeWorks = { expandedDerivatives = true },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    // Clean summary without noisy ~152 raw hint
    val expectedSummary = context.getString(R.string.search_results_count_summary, 1)
    composeTestRule.onNodeWithText(expectedSummary).assertIsDisplayed()
    composeTestRule.onNodeWithText("152", substring = true).assertDoesNotExist()

    // Box badge + Apple Books attribution visible on primary card
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_0"))
    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.search_bundle_box_badge), substring = true).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.search_source_attribution_apple), substring = true).performScrollTo().assertIsDisplayed()

    // Derivative drawer starts collapsed; academic item hidden until toggled
    composeTestRule.onNodeWithText("O Senhor dos Anéis: Análise Filosófica").assertDoesNotExist()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_derivative_works_toggle"))
    composeTestRule.onNodeWithTag("search_derivative_works_toggle").performClick()
    assertTrue(expandedDerivatives)
  }
}
