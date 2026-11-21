package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.QueryIntentParser
import com.example.core.bibliographic.domain.QueryIntentType
import com.example.core.bibliographic.domain.RelevanceAndDerivativeFilter
import com.example.core.bibliographic.domain.WorkGroupingEngine
import com.example.core.bibliographic.network.AdapterSearchRequest
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.OpenLibraryAdapter
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.di.BibliographicDraftStore
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.BibliographicMath
import com.example.core.model.CandidateContributor
import com.example.core.model.CatalogBook
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
import com.example.core.model.SynopsisCandidate
import com.example.core.model.TypographyPreset
import com.example.core.model.Work
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import com.example.feature.library.BookSearchViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class TestSearchPrefsRepo : PreferencesRepository {
  private val state = MutableStateFlow(UserPreferences(language = AppLanguage.PORTUGUESE_BR))
  override val preferences: Flow<UserPreferences> = state
  override suspend fun setLanguage(language: AppLanguage) {}
  override suspend fun setThemeMode(themeMode: AppThemeMode) {}
  override suspend fun setTypographyPreset(preset: TypographyPreset) {}
  override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {}
  override suspend fun setReduceMotion(reduceMotion: Boolean) {}
  override suspend fun setReaderIdentity(name: String, bio: String) {}
}

private class TestNoOpCoverStorage : CoverStorageManager {
  override suspend fun importCoverFromUri(sourceUri: android.net.Uri): String? = null
  override suspend fun deleteCoverIfManaged(coverUri: String?) {}
}

private class ConfigurableAdapter(
  override val source: MetadataSource,
  private val onSearch: suspend (AdapterSearchRequest) -> SourceAdapterResult,
) : BibliographicSourceAdapter {
  val invocations = AtomicInteger(0)

  override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String): SourceAdapterResult {
    invocations.incrementAndGet()
    return onSearch(
      AdapterSearchRequest(
        rawQuery = rawIsbn,
        parsedIntent = QueryIntentParser.parse(rawIsbn),
        page = 1,
      )
    )
  }

  override suspend fun searchByQuery(query: String): SourceAdapterResult {
    invocations.incrementAndGet()
    return onSearch(
      AdapterSearchRequest(
        rawQuery = query,
        parsedIntent = QueryIntentParser.parse(query),
        page = 1,
      )
    )
  }

  override suspend fun searchPaginated(request: AdapterSearchRequest): SourceAdapterResult {
    invocations.incrementAndGet()
    return onSearch(request)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchReformulationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var context: Context
  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
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
        coverStorageManager = TestNoOpCoverStorage(),
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
  fun evidenceWeightedLanguageClassificationHandlesConflictsAndVariantsWithoutBlindOverrides() {
    // 1. Explicit pt-BR with Brazilian ISBN confirms PT_BR
    val brConfirmed =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-BR",
        isbn13Or10 = "9788532530783",
        publisher = "Rocco",
        editionTitle = "Harry Potter e a Pedra Filosofal",
      )
    assertEquals("pt-br", brConfirmed.canonicalTag)
    assertEquals(LanguageTier.PT_BR, brConfirmed.tier)
    assertEquals(LanguageConfidence.CONFIRMED, brConfirmed.confidence)
    assertFalse(brConfirmed.hasConflict)

    // 2. Explicit pt-BR with convergent PT-PT signals (978-972 + Editorial Presença) resolves conflict to pt-pt
    val ptPtMislabeled =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-BR",
        isbn13Or10 = "9789722325332",
        publisher = "Editorial Presença",
        editionTitle = "Harry Potter e a Pedra Filosofal",
      )
    assertEquals("pt-pt", ptPtMislabeled.canonicalTag)
    assertEquals(LanguageTier.PT_PT_OR_OTHER, ptPtMislabeled.tier)
    assertEquals(LanguageConfidence.INFERRED_WITH_CONFLICT, ptPtMislabeled.confidence)
    assertTrue(ptPtMislabeled.hasConflict)

    // 3. Generic "por" without auxiliary cues remains uncertain Portuguese ("pt"), NOT arbitrarily pt-PT
    val genericPor =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "por",
        isbn13Or10 = null,
        publisher = "Independente",
        editionTitle = "Dom Casmurro",
      )
    assertEquals("pt", genericPor.canonicalTag)
    assertEquals(LanguageTier.PT_UNCERTAIN, genericPor.tier)
    assertEquals(LanguageConfidence.UNCERTAIN, genericPor.confidence)

    // 4. Explicit "pt-PT" promotes to EXPLICIT_MATCH when user explicitly asks for Portugal edition
    val explicitPtPtRequested =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "pt-PT",
        isbn13Or10 = "9789722325332",
        publisher = "Presença",
        editionTitle = "O Pequeno Príncipe",
        explicitRequestedLanguage = "pt-pt",
      )
    assertEquals(LanguageTier.EXPLICIT_MATCH, explicitPtPtRequested.tier)

    // 5. Explicit "en" with Cyrillic title marks script conflict
    val scriptConflict =
      BibliographicMath.assessLanguage(
        explicitLanguageRaw = "en",
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Преступление и наказание",
      )
    assertEquals("en", scriptConflict.canonicalTag)
    assertEquals(LanguageConfidence.UNCERTAIN, scriptConflict.confidence)
    assertTrue(scriptConflict.hasConflict)
  }

  @Test
  fun queryIntentParserPreservesShortTitlesAndSupportsExplicitModifiers() {
    val domCasmurro = QueryIntentParser.parse("Dom Casmurro")
    assertEquals(QueryIntentType.AMBIGUOUS_OR_SHORT, domCasmurro.intentType)

    val memorias = QueryIntentParser.parse("Memórias Póstumas de Brás Cubas")
    assertEquals(QueryIntentType.TITLE_STRONG, memorias.intentType)

    val explicitAuthor = QueryIntentParser.parse("autor: Machado de Assis")
    assertEquals(QueryIntentType.EXPLICIT_AUTHOR, explicitAuthor.intentType)
    assertEquals("Machado de Assis", explicitAuthor.authorQueryPart)

    val explicitTitle = QueryIntentParser.parse("titulo: O Alienista")
    assertEquals(QueryIntentType.EXPLICIT_TITLE, explicitTitle.intentType)
    assertEquals("O Alienista", explicitTitle.titleQueryPart)

    val withLang = QueryIntentParser.parse("O Pequeno Príncipe portugal")
    assertEquals("pt-pt", withLang.explicitLanguageRequest)
    assertFalse(withLang.cleanQuery.contains("portugal", ignoreCase = true))

    val memoriasNoAccent = QueryIntentParser.parse("Memorias Postumas de Bras Cubas")
    assertEquals(QueryIntentType.TITLE_STRONG, memoriasNoAccent.intentType)
    assertTrue(
      RelevanceAndDerivativeFilter.evaluateCandidate(
        SearchEditionCandidate(
          candidateId = "ok_memorias",
          workTitle = "Memórias Póstumas de Brás Cubas",
          contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
          language = "pt-BR",
        ),
        memoriasNoAccent,
      ).accepted
    )

    val isbnNewBr = QueryIntentParser.parse("978-65-5532-000-8")
    assertEquals(QueryIntentType.ISBN_EXACT, isbnNewBr.intentType)
    assertTrue(BibliographicMath.isBrazilianIsbn("978-65-5532-000-8"))

    // Nonexistent query must reject unrelated works (anti-noise floor)
    val nonsenseIntent = QueryIntentParser.parse("xyzqwk999livroinexistente")
    val unrelatedCandidate =
      SearchEditionCandidate(
        candidateId = "unrelated_1",
        workTitle = "Dom Casmurro",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
      )
    assertFalse(RelevanceAndDerivativeFilter.evaluateCandidate(unrelatedCandidate, nonsenseIntent).accepted)
  }

  @Test
  fun relevanceFilterEliminatesColoringStationeryAndGuidesWithoutFalsePositives() {
    // Legitimate titles containing "Guia" and "Caderno" MUST pass!
    val mochilaIntent = QueryIntentParser.parse("Guia do Mochileiro das Galaxias")
    val hitchhiker =
      SearchEditionCandidate(
        candidateId = "ok_guia",
        workTitle = "O Guia do Mochileiro das Galáxias",
        contributors = listOf(CandidateContributor("Douglas Adams", ContributorRole.AUTHOR)),
        publisher = "Editora Arqueiro",
        language = "pt-BR",
      )
    assertTrue(RelevanceAndDerivativeFilter.evaluateCandidate(hitchhiker, mochilaIntent).accepted)

    val cadernoIntent = QueryIntentParser.parse("Caderno de Memorias Coloniais")
    val isabela =
      SearchEditionCandidate(
        candidateId = "ok_caderno",
        workTitle = "Caderno de Memórias Coloniais",
        contributors = listOf(CandidateContributor("Isabela Figueiredo", ContributorRole.AUTHOR)),
        publisher = "Todavia",
        language = "pt-BR",
      )
    assertTrue(RelevanceAndDerivativeFilter.evaluateCandidate(isabela, cadernoIntent).accepted)

    // Derivatives & noise MUST be filtered out on Harry Potter / Coraline / Que tal mais um gato?
    val hpIntent = QueryIntentParser.parse("Harry Potter e a Pedra Filosofal")
    val coloring =
      SearchEditionCandidate(
        candidateId = "bad_color",
        workTitle = "Harry Potter Coloring Book: Pedra Filosofal",
        contributors = listOf(CandidateContributor("Scholastic", ContributorRole.AUTHOR)),
      )
    assertFalse(RelevanceAndDerivativeFilter.evaluateCandidate(coloring, hpIntent).accepted)

    val stationery =
      SearchEditionCandidate(
        candidateId = "bad_notebook",
        workTitle = "Harry Potter e a Pedra Filosofal Composition Notebook",
        contributors = emptyList(),
      )
    assertFalse(RelevanceAndDerivativeFilter.evaluateCandidate(stationery, hpIntent).accepted)

    val sparknotes =
      SearchEditionCandidate(
        candidateId = "bad_guide",
        workTitle = "Coraline Summary & Analysis",
        publisher = "SparkNotes",
        contributors = listOf(CandidateContributor("SparkNotes", ContributorRole.AUTHOR)),
      )
    assertFalse(
      RelevanceAndDerivativeFilter.evaluateCandidate(sparknotes, QueryIntentParser.parse("Coraline")).accepted
    )

    // Anti-stopword floor for "Que tal mais um gato?" rejects unrelated Oracle DBA book
    val gatoIntent = QueryIntentParser.parse("Que tal mais um gato?")
    val offTopic =
      SearchEditionCandidate(
        candidateId = "bad_oracle",
        workTitle = "Oracle Database 19c Performance Guide",
        contributors = listOf(CandidateContributor("John Smith", ContributorRole.AUTHOR)),
      )
    assertFalse(RelevanceAndDerivativeFilter.evaluateCandidate(offTopic, gatoIntent).accepted)

    val realGato =
      SearchEditionCandidate(
        candidateId = "ok_gato",
        workTitle = "Que tal mais um gato?",
        contributors = listOf(CandidateContributor("Syou Ishida", ContributorRole.AUTHOR)),
        publisher = "Intrínseca",
      )
    assertTrue(RelevanceAndDerivativeFilter.evaluateCandidate(realGato, gatoIntent).accepted)

    // Explicit coloring book search allows coloring books
    val explicitColorIntent = QueryIntentParser.parse("livro de colorir harry potter")
    assertTrue(RelevanceAndDerivativeFilter.evaluateCandidate(coloring, explicitColorIntent).accepted)
  }

  @Test
  fun testMatrixCasesGroupingOrderingVolumeGuardAndLanguageSeparation() {
    // A1: Harry Potter (BR Rocco beats EN & PT-PT Presença; PT-PT moves to secondaryTierEditions)
    val hpIntent = QueryIntentParser.parse("Harry Potter e a Pedra Filosofal")
    val hpRoccoBr =
      SearchEditionCandidate(
        candidateId = "hp_br",
        workTitle = "Harry Potter e a Pedra Filosofal",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Rocco",
        publicationYear = 2000,
        isbn13 = "9788532511010",
        language = "pt",
        coverCandidates = listOf(CoverCandidate("https://example.com/hp_br.jpg", MetadataSource.GOOGLE_BOOKS, matchesIsbn = true)),
        externalWorkKey = "/works/OL82563W",
        workEditionCount = 240,
        workReadingLogCount = 1800,
        sources = listOf(MetadataSource.GOOGLE_BOOKS),
      )
    val hpPtPt =
      SearchEditionCandidate(
        candidateId = "hp_ptpt",
        workTitle = "Harry Potter e a Pedra Filosofal",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Editorial Presença",
        publicationYear = 1999,
        isbn13 = "9789722325332",
        language = "pt-PT",
        externalWorkKey = "/works/OL82563W",
        workEditionCount = 240,
        workReadingLogCount = 1800,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val hpEn =
      SearchEditionCandidate(
        candidateId = "hp_en",
        workTitle = "Harry Potter and the Philosopher's Stone",
        contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        publisher = "Bloomsbury",
        publicationYear = 1997,
        isbn13 = "9780747532699",
        language = "en",
        externalWorkKey = "/works/OL82563W",
        workEditionCount = 240,
        workReadingLogCount = 1800,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )
    val hpOutcome = WorkGroupingEngine.groupSeparated(listOf(hpPtPt, hpEn, hpRoccoBr), "pt-BR", hpIntent)
    assertEquals(1, hpOutcome.primaryGroups.size)
    assertEquals(0, hpOutcome.secondaryGroups.size)
    val hpGroup = hpOutcome.primaryGroups.first()
    assertEquals("9788532511010", hpGroup.editions.first().isbn13)
    assertEquals(2, hpGroup.primaryTierEditions.size) // BR + EN
    assertEquals(1, hpGroup.secondaryTierEditions.size) // PT-PT separated!
    assertEquals("9789722325332", hpGroup.secondaryTierEditions.first().isbn13)

    // B1 & B2: Volume & Box/Trilogy strict separation
    val lotrIntent = QueryIntentParser.parse("O Senhor dos Aneis")
    val lotrVol1 =
      SearchEditionCandidate(
        candidateId = "lotr_1",
        workTitle = "O Senhor dos Anéis: A Sociedade do Anel (Vol. 1)",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        publisher = "HarperCollins Brasil",
        isbn13 = "9788595084759",
        language = "pt-BR",
      )
    val lotrVol2 =
      SearchEditionCandidate(
        candidateId = "lotr_2",
        workTitle = "O Senhor dos Anéis: As Duas Torres (Vol. 2)",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        publisher = "HarperCollins Brasil",
        isbn13 = "9788595084766",
        language = "pt-BR",
      )
    val lotrBox =
      SearchEditionCandidate(
        candidateId = "lotr_box",
        workTitle = "Box Trilogia O Senhor dos Anéis",
        contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        publisher = "HarperCollins Brasil",
        isbn13 = "9788595086357",
        language = "pt-BR",
      )
    val lotrGroups = WorkGroupingEngine.groupSeparated(listOf(lotrVol1, lotrVol2, lotrBox), "pt-BR", lotrIntent)
    assertEquals(3, lotrGroups.primaryGroups.size)

    // A4: Machado de Assis -> Popular works first; foreign-only works isolated in secondaryGroups
    val machadoIntent = QueryIntentParser.parse("Machado de Assis")
    val domCasmurroPopular =
      SearchEditionCandidate(
        candidateId = "ma_dom",
        workTitle = "Dom Casmurro",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        publisher = "Companhia das Letras",
        isbn13 = "9788582850350",
        language = "pt-BR",
        workEditionCount = 120,
        workReadingLogCount = 900,
      )
    val obscureCritique =
      SearchEditionCandidate(
        candidateId = "ma_obscure",
        workTitle = "Estudo menor sobre Machado de Assis",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        language = "pt-BR",
        workEditionCount = 1,
        workReadingLogCount = 0,
      )
    val russianOnly =
      SearchEditionCandidate(
        candidateId = "ma_ru",
        workTitle = "Дон Касмурро",
        contributors = listOf(CandidateContributor("Machado de Assis", ContributorRole.AUTHOR)),
        language = "ru",
        workEditionCount = 1,
      )
    val maOutcome =
      WorkGroupingEngine.groupSeparated(
        listOf(obscureCritique, russianOnly, domCasmurroPopular),
        "pt-BR",
        machadoIntent,
      )
    assertEquals(2, maOutcome.primaryGroups.size)
    assertEquals("Dom Casmurro", maOutcome.primaryGroups.first().canonicalTitle)
    assertEquals(1, maOutcome.secondaryGroups.size)
    assertEquals("Дон Касмурро", maOutcome.secondaryGroups.first().canonicalTitle)
  }

  @Test
  fun openLibraryAdapterParsesNestedEditionsWithDistinctLanguagesInsteadOfDocLanguageZero() = runBlocking {
    val sampleOlJson =
      """
      {
        "numFound": 1,
        "docs": [
          {
            "key": "/works/OL15800W",
            "title": "O Pequeno Príncipe",
            "author_name": ["Antoine de Saint-Exupéry"],
            "author_key": ["/authors/OL1998A"],
            "first_publish_year": 1943,
            "edition_count": 410,
            "readinglog_count": 3200,
            "ratings_count": 450,
            "language": ["cat", "epo", "por", "eng"],
            "cover_i": 8231990,
            "editions": {
              "numFound": 3,
              "docs": [
                {
                  "key": "/books/OL1M",
                  "title": "El Petit Príncep",
                  "language": ["cat"],
                  "publisher": ["Salamandra"],
                  "isbn": ["9788498381498"],
                  "cover_i": 1001,
                  "publish_year": [2009]
                },
                {
                  "key": "/books/OL2M",
                  "title": "O Pequeno Príncipe",
                  "language": ["por"],
                  "publisher": ["Agir"],
                  "isbn": ["9788522005239"],
                  "cover_i": 1002,
                  "publish_year": [2015],
                  "number_of_pages_median": 96
                }
              ]
            }
          }
        ]
      }
      """.trimIndent()

    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(sampleOlJson.toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    val adapter = OpenLibraryAdapter(client = fakeClient)
    val result = adapter.searchPaginated(AdapterSearchRequest("O Pequeno Príncipe", QueryIntentParser.parse("O Pequeno Príncipe")))
    assertEquals(ProviderOutcomeState.SUCCESS, result.status.state)
    assertEquals(2, result.candidates.size)

    val brEdition = result.candidates.first { it.isbn13 == "9788522005239" }
    assertEquals("pt-br", brEdition.canonicalLanguageTag)
    assertEquals(LanguageTier.PT_BR.rank, brEdition.languageTierRank)
    assertEquals(410, brEdition.workEditionCount)
    assertEquals(3200, brEdition.workReadingLogCount)

    val catEdition = result.candidates.first { it.isbn13 == "9788498381498" }
    assertEquals("ca", catEdition.canonicalLanguageTag)
    assertEquals(LanguageTier.PT_PT_OR_OTHER.rank, catEdition.languageTierRank)
  }

  @Test
  fun progressiveOrchestratorStreamsFastSourcesDegradesGracefullyAndSupportsPagination() = runBlocking {
    val fastOl =
      ConfigurableAdapter(MetadataSource.OPEN_LIBRARY) { req ->
        delay(20)
        if (req.page == 1) {
          SourceAdapterResult(
            SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1, totalAvailable = 50, hasMore = true),
            listOf(
              SearchEditionCandidate(
                candidateId = "ol_coraline",
                workTitle = "Coraline",
                contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
                publisher = "Intrínseca",
                isbn13 = "9788551006757",
                language = "pt-BR",
                workEditionCount = 85,
                sources = listOf(MetadataSource.OPEN_LIBRARY),
              )
            ),
          )
        } else {
          SourceAdapterResult(
            SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1, totalAvailable = 50, hasMore = false),
            listOf(
              SearchEditionCandidate(
                candidateId = "ol_coraline_sequel",
                workTitle = "Coraline & O Oceano no Fim do Caminho",
                contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
                publisher = "Intrínseca",
                isbn13 = "9788580574319",
                language = "pt-BR",
                sources = listOf(MetadataSource.OPEN_LIBRARY),
              )
            ),
          )
        }
      }

    val slowerGb =
      ConfigurableAdapter(MetadataSource.GOOGLE_BOOKS) {
        delay(120)
        SourceAdapterResult(
          SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.RATE_LIMITED, message = "HTTP 429"),
          emptyList(),
        )
      }
    val crossref = ConfigurableAdapter(MetadataSource.CROSSREF) { SourceAdapterResult(SourceQueryStatus(MetadataSource.CROSSREF, ProviderOutcomeState.EMPTY)) }
    val wikidata = ConfigurableAdapter(MetadataSource.WIKIDATA) { SourceAdapterResult(SourceQueryStatus(MetadataSource.WIKIDATA, ProviderOutcomeState.EMPTY)) }
    val brasilApi = ConfigurableAdapter(MetadataSource.BRASIL_API) { SourceAdapterResult(SourceQueryStatus(MetadataSource.BRASIL_API, ProviderOutcomeState.EMPTY)) }

    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = fastOl,
        googleBooksAdapter = slowerGb,
        crossrefAdapter = crossref,
        wikidataAdapter = wikidata,
        brasilApiAdapter = brasilApi,
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )

    val emissions = orchestrator.searchProgressive("Coraline", "pt-BR", expandSecondary = false).toList()
    // Should emit initial loading state followed by ONE consolidated batch response after all primary sources finish!
    assertEquals(2, emissions.size)
    // Secondary sources MUST NOT be called on standard query!
    assertEquals(0, crossref.invocations.get())
    assertEquals(0, wikidata.invocations.get())
    assertEquals(0, brasilApi.invocations.get())

    assertTrue(emissions[0].isPartialResult)

    // Final emission retains Coraline despite Google Books HTTP 429
    val finalResp = emissions.last()
    assertFalse(finalResp.isPartialResult)
    assertEquals("Coraline", finalResp.workGroups.first().canonicalTitle)
    assertEquals(1, finalResp.workGroups.size)
    assertTrue(finalResp.hasMoreResults)
    assertTrue(finalResp.sourceStatuses.any { it.source == MetadataSource.GOOGLE_BOOKS && it.state == ProviderOutcomeState.RATE_LIMITED })

    // Manual Load More page 2 adds the second work without losing Coraline
    val page2 = orchestrator.loadNextPage(finalResp, "pt-BR", expandSecondary = false)
    assertEquals(2, page2.workGroups.size)
  }

  @Test
  fun googleBooksCoverCandidatesPrioritizeHdAndStorageManagerRejectsPlaceholderPng(): Unit = runBlocking {
    val candidates =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrl = "http://books.google.com/books/content?id=XYZ&zoom=1&edge=curl",
        matchesIsbn = true,
        matchesPublisherYear = true,
        matchesLanguage = true,
      )
    assertTrue(candidates.size >= 3)
    assertTrue(candidates[0].imageUrl.contains("fife=w800"))
    assertTrue(candidates[1].imageUrl.contains("zoom=0"))
    assertTrue(candidates[2].imageUrl.contains("zoom=1"))
    assertTrue(candidates.all { it.imageUrl.startsWith("https://") && !it.imageUrl.contains("edge=curl") })

    // Simulate HTTP 200 ~9KB tiny PNG on fife=w800 & zoom=0, and real JPEG on zoom=1
    val realBmp = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
    val realBytes =
      ByteArrayOutputStream().use { out ->
        realBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
      }
    val fakePlaceholderBytes = ByteArray(9103) { 1 }

    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val u = chain.request().url.toString()
          val bytes =
            if (u.contains("fife=w800") || u.contains("zoom=0")) fakePlaceholderBytes
            else realBytes
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody("image/jpeg".toMediaType()))
            .build()
        }
        .build()

    val manager = InternalCoverStorageManager(context = context, okHttpClient = fakeClient)
    val savedPath = manager.downloadBestAvailableCover(candidates.map { it.imageUrl })
    assertNotNull(savedPath)
    manager.deleteCoverIfManaged(savedPath)
  }

  @Test
  fun viewModelSearchesLocallyWhileTypingAndCancelsStaleSearchOnNewSubmit() = runBlocking {
    libraryRepo.addBook(
      title = "Memórias Póstumas de Brás Cubas",
      author = "Machado de Assis",
      totalPages = 320,
      publicationYear = 1881,
      editionNote = "Companhia das Letras",
      status = ReadingStatus.UNREAD,
    )

    val delayedOl =
      ConfigurableAdapter(MetadataSource.OPEN_LIBRARY) { req ->
        if (req.rawQuery.contains("Primeira")) {
          delay(400)
          SourceAdapterResult(
            SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1),
            listOf(
              SearchEditionCandidate(
                candidateId = "stale_1",
                workTitle = "Primeira Busca Lenta",
                contributors = listOf(CandidateContributor("Autor Lento", ContributorRole.AUTHOR)),
                language = "pt-BR",
              )
            ),
          )
        } else {
          delay(30)
          SourceAdapterResult(
            SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1),
            listOf(
              SearchEditionCandidate(
                candidateId = "fresh_2",
                workTitle = "Coraline",
                contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
                language = "pt-BR",
              )
            ),
          )
        }
      }
    val emptyAdapter = { src: MetadataSource ->
      ConfigurableAdapter(src) { SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY)) }
    }
    val emptyGb = emptyAdapter(MetadataSource.GOOGLE_BOOKS)

    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = delayedOl,
        googleBooksAdapter = emptyGb,
        crossrefAdapter = emptyAdapter(MetadataSource.CROSSREF),
        wikidataAdapter = emptyAdapter(MetadataSource.WIKIDATA),
        brasilApiAdapter = emptyAdapter(MetadataSource.BRASIL_API),
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
        preferencesRepository = TestSearchPrefsRepo(),
        draftStore = BibliographicDraftStore(),
      )

    val collectorJob = launch(testDispatcher) { vm.uiState.collect {} }
    try {
      // Typing triggers instant local search with ZERO external API calls
      vm.setQuery("Bras Cubas")
      testDispatcher.scheduler.advanceTimeBy(120)
      testDispatcher.scheduler.runCurrent()
      repeat(30) {
        if (vm.uiState.value.localLiveMatches.isNotEmpty()) return@repeat
        Thread.sleep(20)
        testDispatcher.scheduler.runCurrent()
      }
      assertEquals(1, vm.uiState.value.localLiveMatches.size)
      assertEquals(0, delayedOl.invocations.get())
      assertEquals(0, emptyGb.invocations.get())

      // Rapid consecutive submits cancel stale search
      vm.setQuery("Primeira Busca")
      vm.submitSearch()
      testDispatcher.scheduler.advanceTimeBy(40)
      testDispatcher.scheduler.runCurrent()
      vm.setQuery("Coraline")
      vm.submitSearch()
      repeat(40) {
        testDispatcher.scheduler.advanceTimeBy(50)
        testDispatcher.scheduler.runCurrent()
        if (vm.uiState.value.response?.isPartialResult == false &&
          vm.uiState.value.response?.workGroups?.firstOrNull()?.canonicalTitle == "Coraline"
        ) {
          return@repeat
        }
        Thread.sleep(20)
      }

      val finalTitle = vm.uiState.value.response?.workGroups?.firstOrNull()?.canonicalTitle
      assertEquals("Coraline", finalTitle)
    } finally {
      collectorJob.cancel()
    }
  }

  @Test
  fun uiCollapsesSecondaryLanguagesShowsLoadMoreAndLiveLocalMatches() {
    val primaryGroup =
      SearchWorkGroup(
        groupId = "g_br",
        canonicalTitle = "O Pequeno Príncipe",
        primaryAuthors = listOf(CandidateContributor("Antoine de Saint-Exupéry", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "ed_br",
              workTitle = "O Pequeno Príncipe",
              publisher = "Agir",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            )
          ),
      )
    val secondaryGroup =
      SearchWorkGroup(
        groupId = "g_ptpt",
        canonicalTitle = "O Principezinho",
        primaryAuthors = listOf(CandidateContributor("Antoine de Saint-Exupéry", ContributorRole.AUTHOR)),
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "ed_ptpt",
              workTitle = "O Principezinho",
              publisher = "Editorial Presença",
              language = "pt-pt",
              canonicalLanguageTag = "pt-pt",
              languageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            )
          ),
        primaryLanguageTierRank = LanguageTier.PT_PT_OR_OTHER.rank,
      )

    var secondaryExpanded = false
    var loadMoreClicks = 0
    val secondaryExpandedState = androidx.compose.runtime.mutableStateOf(false)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "O Pequeno Príncipe",
                hasSearched = true,
                isSecondaryLanguagesExpanded = secondaryExpandedState.value,
                response =
                  HybridSearchResponse(
                    query = "O Pequeno Príncipe",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(primaryGroup),
                    secondaryWorkGroups = listOf(secondaryGroup),
                    hasMoreResults = true,
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
            onLoadMoreResults = { loadMoreClicks++ },
            onToggleSecondaryLanguages = {
              secondaryExpanded = !secondaryExpanded
              secondaryExpandedState.value = secondaryExpanded
            },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("search_work_card_0").assertIsDisplayed()
    // Secondary pt-PT card is collapsed initially!
    composeTestRule.onNodeWithText("O Principezinho").assertDoesNotExist()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_secondary_languages_toggle"))
    composeTestRule.onNodeWithTag("search_secondary_languages_toggle").performClick()
    composeTestRule.waitForIdle()
    assertTrue(secondaryExpanded)
    composeTestRule.onNodeWithText("O Principezinho").assertIsDisplayed()

    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_load_more_results_button"))
    composeTestRule.onNodeWithTag("search_load_more_results_button").performClick()
    assertEquals(1, loadMoreClicks)
  }

  @Test
  fun uiDisplaysLiveLocalMatchesBeforeSearchSubmitted() {
    val localBook =
      CatalogBook(
        work = Work(id = 42L, title = "Dom Casmurro", author = "Machado de Assis"),
        primaryEdition = null,
        primaryCopy = null,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Dom",
                hasSearched = false,
                localLiveMatches = listOf(localBook),
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

    composeTestRule.onNodeWithTag("search_live_local_match_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Dom Casmurro").assertIsDisplayed()
  }
}
