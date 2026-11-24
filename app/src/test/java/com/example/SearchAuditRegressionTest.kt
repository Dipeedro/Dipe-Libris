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
import com.example.core.bibliographic.network.BibliographicHttpClients
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.OpenLibraryAdapter
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.WindowWidthClass
import com.example.core.localization.LocalAppLanguage
import com.example.core.model.AppLanguage
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.HybridSearchResponse
import com.example.core.model.LanguageTier
import com.example.core.model.MatchTier
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import com.example.core.model.SourceQueryStatus
import com.example.core.model.TitleBundleKind
import com.example.feature.library.BookSearchScreen
import com.example.feature.library.BookSearchUiState
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class AuditNoOpCoverStorage : CoverStorageManager {
  override suspend fun importCoverFromUri(sourceUri: Uri): String? = null
  override suspend fun deleteCoverIfManaged(coverUri: String?) {}
}

private class AuditFakeAdapter(
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchAuditRegressionTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
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
        coverStorageManager = AuditNoOpCoverStorage(),
        genreDao = db.genreDao(),
        contributorDao = db.contributorDao(),
        metadataSuggestionDao = db.metadataSuggestionDao(),
      )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun auditA_accentStrippedDerivativeAndAcademicContaminationIsolatedAndBarrier3Holds() {
    val jvIntent = QueryIntentParser.parse("Jogos Vorazes")
    val jvCandidates =
      listOf(
        SearchEditionCandidate(
          candidateId = "jv_real",
          workTitle = "Jogos Vorazes",
          contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
          publisher = "Rocco",
          isbn13 = "9788579800245",
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          workEditionCount = 85,
          workReadingLogCount = 9200,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
        SearchEditionCandidate(
          candidateId = "jv_reflexao",
          workTitle = "Jogos Vorazes",
          subtitle = "Uma reflexão sobre o mundo contemporâneo",
          contributors = listOf(CandidateContributor("Autor Acadêmico", ContributorRole.AUTHOR)),
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "jv_filosofia",
          workTitle = "Jogos Vorazes e a Filosofia",
          contributors = listOf(CandidateContributor("George A. Dunn", ContributorRole.AUTHOR)),
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "jv_analise",
          workTitle = "Análise do livro: Jogos Vorazes",
          contributors = listOf(CandidateContributor("Resumo Editorial", ContributorRole.AUTHOR)),
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "jv_guia_leitura",
          workTitle = "Jogos Vorazes: Guia de Leitura Crítica",
          contributors = listOf(CandidateContributor("Equipe Escolar", ContributorRole.AUTHOR)),
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "jv_en_study",
          workTitle = "The Hunger Games: Complete Study Guide",
          contributors = listOf(CandidateContributor("SparkNotes", ContributorRole.AUTHOR)),
          language = "en",
          canonicalLanguageTag = "en",
          languageTierRank = LanguageTier.EN.rank,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        ),
        SearchEditionCandidate(
          candidateId = "jv_companion_same_author_should_not_merge",
          workTitle = "Jogos Vorazes: Guia Ilustrado do Filme",
          contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        ),
      )

    val jvOutcome = WorkGroupingEngine.groupSeparated(jvCandidates, "pt-BR", jvIntent)
    assertEquals(1, jvOutcome.primaryGroups.size)
    assertEquals("Jogos Vorazes", jvOutcome.primaryGroups.first().canonicalTitle)
    assertEquals(listOf("jv_real"), jvOutcome.primaryGroups.first().editions.map { it.candidateId })

    val hpIntent = QueryIntentParser.parse("Harry Potter e a Pedra Filosofal")
    val hpOutcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates =
          listOf(
            SearchEditionCandidate(
              candidateId = "hp_real",
              workTitle = "Harry Potter e a Pedra Filosofal",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Rocco",
              isbn13 = "9788532530783",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            ),
            SearchEditionCandidate(
              candidateId = "hp_resumo",
              workTitle = "Resumo completo: Harry Potter e a Pedra Filosofal",
              contributors = listOf(CandidateContributor("ResumoCast", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "hp_analise",
              workTitle = "Análise crítica de Harry Potter e a Pedra Filosofal",
              contributors = listOf(CandidateContributor("Pesquisador", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
          ),
        preferredLanguage = "pt-BR",
        parsedIntent = hpIntent,
      )
    assertEquals(1, hpOutcome.primaryGroups.size)
    assertEquals(listOf("hp_real"), hpOutcome.primaryGroups.first().editions.map { it.candidateId })
    assertTrue(hpOutcome.derivativeGroups.flatMap { it.editions }.any { it.candidateId == "hp_analise" })
  }

  @Test
  fun auditB_delimiterlessTitleAuthorAndDetachedModifiersWorkAccurately() {
    // 1. "Coraline Neil Gaiman" cross-field exact match prioritizes Coraline ahead of other author books
    val coralineIntent = QueryIntentParser.parse("Coraline Neil Gaiman")
    val coralineOutcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates =
          listOf(
            SearchEditionCandidate(
              candidateId = "other_gaiman",
              workTitle = "O Oceano no Fim do Caminho",
              contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            ),
            SearchEditionCandidate(
              candidateId = "coraline_br",
              workTitle = "Coraline",
              contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
              publisher = "Intrínseca",
              isbn13 = "9788551006757",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
          ),
        preferredLanguage = "pt-BR",
        parsedIntent = coralineIntent,
      )
    assertTrue(coralineOutcome.primaryGroups.isNotEmpty())
    assertEquals("Coraline", coralineOutcome.primaryGroups.first().canonicalTitle)

    // 2. "Jogos Vorazes guia" detaches modifier and matches PT + bilingual EN guides
    val guiaIntent = QueryIntentParser.parse("Jogos Vorazes guia")
    assertEquals("Jogos Vorazes", guiaIntent.subjectQueryPart)
    assertEquals(listOf("guia"), guiaIntent.detachedModifierTokens)
    assertFalse(guiaIntent.canProbeStructuredAuthor)

    val guiaPlanGb = QueryPlanner.planGoogleBooks(guiaIntent, page = 1, pageSize = 20)
    assertTrue(guiaPlanGb.primaryQueries.any { it.qParam.contains("intitle:\"Jogos Vorazes\"") })
    assertTrue(guiaPlanGb.primaryQueries.any { it.qParam.contains("guide", ignoreCase = true) })

    val guiaOutcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates =
          listOf(
            SearchEditionCandidate(
              candidateId = "wd_bridge_jv",
              workTitle = "Jogos Vorazes",
              originalTitle = "The Hunger Games",
              contributors = listOf(CandidateContributor("Suzanne Collins", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              externalWorkKey = "/works/OL5735363W",
              externalIds = mapOf("wikidata_work" to "Q11679", "openlibrary_work" to "/works/OL5735363W"),
              sources = listOf(MetadataSource.WIKIDATA),
            ),
            SearchEditionCandidate(
              candidateId = "jv_official_guide_en",
              workTitle = "The Hunger Games: Tribute Guide",
              contributors = listOf(CandidateContributor("Emily Seife", ContributorRole.AUTHOR)),
              language = "en",
              canonicalLanguageTag = "en",
              languageTierRank = LanguageTier.EN.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "jv_guia_pt",
              workTitle = "Jogos Vorazes: O Guia Oficial Ilustrado do Filme",
              originalTitle = "The Hunger Games: Official Illustrated Movie Companion",
              contributors = listOf(CandidateContributor("Kate Egan", ContributorRole.AUTHOR)),
              publisher = "Rocco",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
          ),
        preferredLanguage = "pt-BR",
        parsedIntent = guiaIntent,
      )
    assertTrue(guiaOutcome.primaryGroups.any { it.canonicalTitle.contains("Guia", ignoreCase = true) })
    assertTrue(guiaOutcome.primaryGroups.any { it.canonicalTitle.contains("Tribute Guide", ignoreCase = true) })

    // 3. "Box Harry Potter" prioritizes box/collection bundles cleanly & keeps genuine "Guia do Mochileiro" intact
    val boxIntent = QueryIntentParser.parse("Box Harry Potter")
    assertEquals("Harry Potter", boxIntent.subjectQueryPart)
    assertTrue(boxIntent.wantsBoxOrCollection)
    assertFalse(boxIntent.canProbeStructuredAuthor)
    val boxOutcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates =
          listOf(
            SearchEditionCandidate(
              candidateId = "hp_single",
              workTitle = "Harry Potter e a Pedra Filosofal",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
            ),
            SearchEditionCandidate(
              candidateId = "hp_box",
              workTitle = "Box Harry Potter - Coleção Completa",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Rocco",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
            ),
          ),
        preferredLanguage = "pt-BR",
        parsedIntent = boxIntent,
      )
    assertEquals(TitleBundleKind.COLLECTION_OR_BOX, boxOutcome.primaryGroups.first().effectiveBundleKind)

    val genuineGuide = QueryIntentParser.parse("Guia do Mochileiro das Galaxias")
    assertNull(genuineGuide.subjectQueryPart)
  }

  @Test
  fun auditC_openLibraryRetainsMoreThan5EditionsAndIsolatesSlowEnrichment() = runBlocking {
    val nestedDocs =
      (1..12).joinToString(",") { idx ->
        val isbn = if (idx <= 8) "97885325${10000 + idx}" else "9780439${100000 + idx}"
        val lang = if (idx <= 8) "\"por\"" else "\"eng\""
        val pub = if (idx <= 8) "\"Rocco\"" else "\"Bloomsbury\""
        """
        {
          "key": "/books/OL${idx}M",
          "title": "Harry Potter e o Prisioneiro de Azkaban",
          "language": [$lang],
          "publisher": [$pub],
          "isbn": ["$isbn"]
        }
        """.trimIndent()
      }
    val searchPayload =
      """
      {
        "numFound": 1,
        "docs": [
          {
            "key": "/works/OL82586W",
            "title": "Harry Potter and the Prisoner of Azkaban",
            "author_name": ["J. K. Rowling"],
            "first_publish_year": 1999,
            "edition_count": 47,
            "readinglog_count": 5000,
            "language": ["eng", "por"],
            "editions": { "numFound": 47, "docs": [$nestedDocs] }
          }
        ]
      }
      """.trimIndent()

    val mockClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(searchPayload.toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    val olAdapter = OpenLibraryAdapter(client = mockClient, baseUrl = "https://openlibrary.test")
    val res =
      olAdapter.searchPaginated(
        AdapterSearchRequest(
          rawQuery = "Harry Potter e o Prisioneiro de Azkaban",
          parsedIntent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban"),
          page = 1,
          pageSize = 30,
        )
      )
    assertEquals(ProviderOutcomeState.SUCCESS, res.status.state)
    assertTrue("Expected >5 editions retained, got ${res.candidates.size}", res.candidates.size >= 12)

    // Verify slow /editions.json enrichment timeout does NOT discard search.json candidates!
    val server = ServerSocket(0)
    val acceptedThreads = mutableListOf<Thread>()
    val serverThread =
      thread(start = true, isDaemon = true) {
        while (!server.isClosed) {
          val sock = runCatching { server.accept() }.getOrNull() ?: break
          val handler =
            thread(start = true, isDaemon = true) {
              sock.use { s ->
                val reader = s.getInputStream().bufferedReader()
                val reqLine = runCatching { reader.readLine() }.getOrNull().orEmpty()
                while (true) {
                  val l = runCatching { reader.readLine() }.getOrNull() ?: break
                  if (l.isEmpty()) break
                }
                if (reqLine.contains("/editions.json")) {
                  runCatching { Thread.sleep(4_000L) }
                } else {
                  val twoEditionsSearch =
                    """
                    {
                      "numFound": 1,
                      "docs": [
                        {
                          "key": "/works/OL82586W",
                          "title": "Harry Potter",
                          "author_name": ["J. K. Rowling"],
                          "edition_count": 47,
                          "readinglog_count": 5000,
                          "language": ["eng", "por"],
                          "editions": {
                            "numFound": 2,
                            "docs": [
                              {"key": "/books/OL1M", "title": "Harry Potter", "language": ["eng"], "publisher": ["Bloomsbury"]},
                              {"key": "/books/OL2M", "title": "Harry Potter PT", "language": ["por"], "publisher": ["Rocco"], "isbn": ["9788532530783"]}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                  val bytes = twoEditionsSearch.toByteArray()
                  val out = s.getOutputStream()
                  out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                  out.write(bytes)
                  out.flush()
                }
              }
            }
          acceptedThreads.add(handler)
        }
      }

    try {
      val slowEnrichAdapter =
        OpenLibraryAdapter(
          client = BibliographicHttpClients.okHttpClient,
          baseUrl = "http://127.0.0.1:${server.localPort}",
        )
      val t0 = System.currentTimeMillis()
      val enrichRes =
        slowEnrichAdapter.searchPaginated(
          AdapterSearchRequest(
            rawQuery = "Harry Potter",
            parsedIntent = QueryIntentParser.parse("Harry Potter"),
            page = 1,
            pageSize = 30,
          )
        )
      val dt = System.currentTimeMillis() - t0
      assertEquals(ProviderOutcomeState.SUCCESS, enrichRes.status.state)
      assertTrue("search.json editions must be preserved even if editions.json stalls", enrichRes.candidates.size >= 2)
      assertTrue("Isolated enrichment should resolve well under 3s, took ${dt}ms", dt < 2_800L)
    } finally {
      server.close()
      serverThread.join(500)
      acceptedThreads.forEach { it.interrupt() }
    }
  }

  @Test
  fun auditE_cooperativeCallCancelAbortsBlockedHttpSocketAtBatchDeadline() = runBlocking {
    val server = ServerSocket(0)
    val serverThread =
      thread(start = true, isDaemon = true) {
        while (!server.isClosed) {
          val sock = runCatching { server.accept() }.getOrNull() ?: break
          thread(start = true, isDaemon = true) {
            sock.use {
              // Hang silently beyond OkHttp's readTimeout (10s) to verify OkHttp call.cancel() unblocks at ~3.2s
              runCatching { Thread.sleep(10_000L) }
            }
          }
        }
      }

    try {
      val longTimeoutClient =
        BibliographicHttpClients.okHttpClient.newBuilder()
          .readTimeout(12, TimeUnit.SECONDS)
          .connectTimeout(5, TimeUnit.SECONDS)
          .build()
      val hangingOlAdapter =
        OpenLibraryAdapter(
          client = longTimeoutClient,
          baseUrl = "http://127.0.0.1:${server.localPort}",
        )
      val fastGb =
        AuditFakeAdapter(MetadataSource.GOOGLE_BOOKS) {
          SourceAdapterResult(
            SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 1),
            listOf(
              SearchEditionCandidate(
                candidateId = "gb_hung",
                workTitle = "Coraline",
                contributors = listOf(CandidateContributor("Neil Gaiman", ContributorRole.AUTHOR)),
                language = "pt-BR",
                sources = listOf(MetadataSource.GOOGLE_BOOKS),
              )
            ),
          )
        }
      val emptyOther = { src: MetadataSource ->
        AuditFakeAdapter(src) { SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY)) }
      }
      val orchestrator =
        HybridSearchOrchestrator(
          openLibraryAdapter = hangingOlAdapter,
          googleBooksAdapter = fastGb,
          crossrefAdapter = emptyOther(MetadataSource.CROSSREF),
          wikidataAdapter = emptyOther(MetadataSource.WIKIDATA),
          brasilApiAdapter = emptyOther(MetadataSource.BRASIL_API),
          connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
          searchCacheDao = db.searchCacheDao(),
          editionDao = db.editionDao(),
          bookDao = db.bookDao(),
          libraryRepository = libraryRepo,
          primaryBatchDeadlineMs = 2_500L,
        )

      val t0 = System.currentTimeMillis()
      val responses = orchestrator.searchProgressive("Coraline", "pt-BR").toList()
      val elapsed = System.currentTimeMillis() - t0

      assertTrue("Cooperative call.cancel() must abort hung socket around 3.2s (< 4.5s), took ${elapsed}ms", elapsed < 4_500L)
      val finalResp = responses.last()
      assertEquals("Coraline", finalResp.workGroups.first().canonicalTitle)
      val olStatus = finalResp.sourceStatuses.first { it.source == MetadataSource.OPEN_LIBRARY }
      assertEquals(ProviderOutcomeState.ERROR, olStatus.state)
      assertTrue(
        olStatus.message?.contains("timeout", ignoreCase = true) == true ||
          olStatus.message?.contains("deadline", ignoreCase = true) == true
      )
    } finally {
      server.close()
      serverThread.join(500)
    }
  }

  @Test
  fun auditF_coverThumbnailSelectionWindowedEditionsAndTimeoutPillWorkInUi() {
    val urls =
      listOf(
        "https://books.google.com/books/content?id=XYZ&fife=w800",
        "https://books.google.com/books/content?id=XYZ&zoom=0",
        "https://covers.openlibrary.org/b/id/12345-L.jpg?default=false",
        "https://is1-ssl.mzstatic.com/image/thumb/Publication/600x900bb.jpg",
      )
    val compactUrls = CoverRanker.selectForDisplaySize(urls, maxWidthDp = 56)
    assertEquals(2, compactUrls.size)
    assertTrue(compactUrls.all { !it.contains("fife=w800") && !it.contains("zoom=0") })
    val hdUrls = CoverRanker.selectForDisplaySize(urls, maxWidthDp = 240)
    assertEquals(4, hdUrls.size)
    assertTrue(hdUrls.first().contains("fife=w800"))

    val editions =
      (1..15).map { idx ->
        SearchEditionCandidate(
          candidateId = "ed_$idx",
          workTitle = "O Senhor dos Anéis",
          contributors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
          publisher = "HarperCollins Brasil",
          publicationYear = 2000 + idx,
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          sources = listOf(MetadataSource.OPEN_LIBRARY),
        )
      }
    val bigGroup =
      SearchWorkGroup(
        groupId = "g_lotr",
        canonicalTitle = "O Senhor dos Anéis",
        primaryAuthors = listOf(CandidateContributor("J. R. R. Tolkien", ContributorRole.AUTHOR)),
        editions = editions,
        sources = listOf(MetadataSource.OPEN_LIBRARY),
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "O Senhor dos Anéis",
                hasSearched = true,
                expandedGroupIds = setOf("g_lotr"),
                response =
                  HybridSearchResponse(
                    query = "O Senhor dos Anéis",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(bigGroup),
                    sourceStatuses =
                      listOf(
                        SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 15),
                        SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.ERROR, message = "Batch deadline exceeded"),
                      ),
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
    // Timeout diagnostic pill shown distinctly from generic error
    composeTestRule.onNodeWithText("Google Books (timed out)").assertIsDisplayed()

    // Windowed editions start at 6 instead of inflating all 15
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_0"))
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_5"))
    composeTestRule.onNodeWithTag("search_edition_select_0_5").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_6").assertDoesNotExist()

    // Clicking "Show more editions" reveals next +8 window (total 14)
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_show_more_0"))
    composeTestRule.onNodeWithTag("search_edition_show_more_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_13"))
    composeTestRule.onNodeWithTag("search_edition_select_0_13").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_select_0_14").assertDoesNotExist()

    // Second click reveals final 15th edition and hides button
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_show_more_0"))
    composeTestRule.onNodeWithTag("search_edition_show_more_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_edition_select_0_14"))
    composeTestRule.onNodeWithTag("search_edition_select_0_14").assertIsDisplayed()
    composeTestRule.onNodeWithTag("search_edition_show_more_0").assertDoesNotExist()
  }

  @Test
  fun auditG_t13UntaggedPortugueseEditionsInferredOffsetPaginationAndExpansionMergeWork() = runBlocking {
    // 1. Untagged Portuguese edition title is inferred as PT_UNCERTAIN (not UNKNOWN)
    val inferredPt =
      com.example.core.model.BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Harry Potter e o Prisioneiro de Azkaban",
      )
    assertEquals("pt", inferredPt.normalizedCode)
    assertEquals(LanguageTier.PT_UNCERTAIN, inferredPt.tier)

    val nonPtTitle =
      com.example.core.model.BibliographicMath.assessLanguage(
        explicitLanguageRaw = null,
        isbn13Or10 = null,
        publisher = null,
        editionTitle = "Harry Potter y el prisionero de Azkaban",
      )
    assertEquals(LanguageTier.UNKNOWN, nonPtTitle.tier)

    // 2. OpenLibraryAdapter offset pagination + audiobook exclusion + GoogleBooks merge in orchestrator
    val mockClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val url = chain.request().url.toString()
          val jsonBody =
            when {
              url.contains("/works/OL82586W.json") ->
                """{"key":"/works/OL82586W","title":"Harry Potter and the Prisoner of Azkaban","authors":[{"author":{"key":"/authors/OL23919A"}}]}"""
              url.contains("/authors/OL23919A.json") ->
                """{"key":"/authors/OL23919A","name":"J. K. Rowling"}"""
              url.contains("/works/OL82586W/editions.json") && url.contains("offset=100") ->
                """
                {
                  "size": 125,
                  "entries": [
                    {
                      "key": "/books/OL_PT_COMEM",
                      "title": "Harry Potter e o Prisioneiro de Azkaban: Edição comemorativa de 20 anos",
                      "publishers": ["Rocco"],
                      "isbn_13": ["9788532531780"],
                      "publish_date": "2020"
                    }
                  ]
                }
                """.trimIndent()
              url.contains("/works/OL82586W/editions.json") ->
                """
                {
                  "size": 125,
                  "entries": [
                    {
                      "key": "/books/OL_PT_UNTAGGED",
                      "title": "Harry Potter e o Prisioneiro de Azkaban",
                      "publishers": ["Rocco"],
                      "isbn_13": ["9788532512062"],
                      "publish_date": "2000"
                    },
                    {
                      "key": "/books/OL_AUDIO_CD",
                      "title": "Harry Potter and the Prisoner of Azkaban",
                      "physical_format": "Audio CD",
                      "languages": [{"key": "/languages/eng"}],
                      "isbn_13": ["9780807282328"]
                    }
                  ]
                }
                """.trimIndent()
              else -> "{}"
            }
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(if (jsonBody == "{}") 404 else 200)
            .message("OK")
            .body(jsonBody.toResponseBody("application/json".toMediaType()))
            .build()
        }
        .build()

    val olAdapter = OpenLibraryAdapter(client = mockClient, baseUrl = "https://openlibrary.test")
    val gbExpandedAdapter =
      AuditFakeAdapter(MetadataSource.GOOGLE_BOOKS) { req ->
        SourceAdapterResult(
          SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, 2),
          listOf(
            SearchEditionCandidate(
              candidateId = "gb_hp3_livro3",
              workTitle = "Harry Potter e o Prisioneiro de Azkaban (Livro 3)",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Pottermore Publishing",
              isbn13 = "9781781103098",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "gb_hp3_audio",
              workTitle = "Harry Potter and the Prisoner of Azkaban (Audiobook)",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              language = "en",
              format = com.example.core.model.EditionFormat.OTHER,
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
          ),
        )
      }
    val emptyOther = { src: MetadataSource ->
      AuditFakeAdapter(src) { SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY)) }
    }
    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = olAdapter,
        googleBooksAdapter = gbExpandedAdapter,
        crossrefAdapter = emptyOther(MetadataSource.CROSSREF),
        wikidataAdapter = emptyOther(MetadataSource.WIKIDATA),
        brasilApiAdapter = emptyOther(MetadataSource.BRASIL_API),
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )

    val initialGroup =
      SearchWorkGroup(
        groupId = "wg_hp3_test",
        canonicalTitle = "Harry Potter e o Prisioneiro de Azkaban",
        originalTitle = "Harry Potter and the Prisoner of Azkaban",
        primaryAuthors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
        externalWorkKey = "/works/OL82586W",
        editions =
          listOf(
            SearchEditionCandidate(
              candidateId = "ol_seed",
              workTitle = "Harry Potter and the Prisoner of Azkaban",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              language = "en",
              canonicalLanguageTag = "en",
              languageTierRank = LanguageTier.EN.rank,
              isbn13 = "9780747546290",
              externalWorkKey = "/works/OL82586W",
              workEditionCount = 125,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            )
          ),
        totalAvailableEditionsHint = 125,
      )

    val expanded =
      orchestrator.expandWorkEditionsDetailed(
        workGroup = initialGroup,
        preferredLanguage = "pt-BR",
        originalQuery = "Harry Potter e o Prisioneiro de Azkaban",
      )

    assertEquals("wg_hp3_test", expanded.group.groupId)
    val isbns = expanded.group.editions.mapNotNull { it.isbn13 }.toSet()
    assertTrue("Expected untagged OL PT edition 9788532512062", "9788532512062" in isbns)
    assertTrue("Expected offset=100 commemorative OL edition 9788532531780", "9788532531780" in isbns)
    assertTrue("Expected Google Books '(Livro 3)' edition 9781781103098", "9781781103098" in isbns)
    assertFalse("Audio CD must be filtered out", "9780807282328" in isbns)
    assertFalse("Google Books audiobook must be filtered out", expanded.group.editions.any { it.candidateId == "gb_hp3_audio" })
  }

  @Test
  fun auditH_wdBridgeExcludedFromDisplaySingleVolumeBridgeSafeAndUiHidesExhaustedButton() {
    val hpIntent = QueryIntentParser.parse("Harry Potter e o Prisioneiro de Azkaban")
    val outcome =
      WorkGroupingEngine.groupSeparated(
        rawCandidates =
          listOf(
            SearchEditionCandidate(
              candidateId = "wd_bridge_hp3",
              workTitle = "Harry Potter e o Prisioneiro de Azkaban",
              originalTitle = "Harry Potter and the Prisoner of Azkaban",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              externalWorkKey = "/works/OL82586W",
              externalIds = mapOf("wikidata_work" to "Q47598"),
              sources = listOf(MetadataSource.WIKIDATA),
            ),
            SearchEditionCandidate(
              candidateId = "wd_bridge_orphan",
              workTitle = "Obra Sintética Isolada",
              contributors = listOf(CandidateContributor("Outro Autor", ContributorRole.AUTHOR)),
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.WIKIDATA),
            ),
            SearchEditionCandidate(
              candidateId = "hp3_rocco",
              workTitle = "Harry Potter e o Prisioneiro de Azkaban",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Rocco",
              isbn13 = "9788532512062",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              externalWorkKey = "/works/OL82586W",
              sources = listOf(MetadataSource.GOOGLE_BOOKS),
            ),
            SearchEditionCandidate(
              candidateId = "hp3_pottermore",
              workTitle = "Harry Potter e o Prisioneiro de Azkaban (Livro 3)",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Pottermore",
              isbn13 = "9781781103098",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.APPLE_BOOKS),
            ),
          ),
        preferredLanguage = "pt-BR",
        parsedIntent = hpIntent,
      )

    assertEquals(1, outcome.primaryGroups.size)
    val hpGroup = outcome.primaryGroups.first()
    assertEquals(2, hpGroup.editions.size)
    assertFalse(hpGroup.editions.any { it.candidateId.startsWith("wd_bridge_") })

    // Verify load more editions button hides when group is exhausted
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Harry Potter e o Prisioneiro de Azkaban",
                hasSearched = true,
                expandedGroupIds = setOf(hpGroup.groupId),
                exhaustedEditionGroupIds = setOf(hpGroup.groupId),
                response =
                  HybridSearchResponse(
                    query = "Harry Potter e o Prisioneiro de Azkaban",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(hpGroup),
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
    composeTestRule.onNodeWithTag("book_search_screen").performScrollToNode(hasTestTag("search_work_card_0"))
    composeTestRule.onNodeWithTag("search_edition_load_more_0").assertDoesNotExist()
  }

  @Test
  fun auditI_giantMultiEditionCacheBoundedProgressiveStreamingAndSecondaryTimeoutDiscreet() = runBlocking {
    val giantSynopses =
      listOf(
        com.example.core.model.SynopsisCandidate(
          source = MetadataSource.GOOGLE_BOOKS,
          text = "Sinopse extensa de Harry Potter e a Pedra Filosofal. ".repeat(60),
          language = "pt-br",
        )
      )
    val hugeCandidates =
      (1..200).map { idx ->
        SearchEditionCandidate(
          candidateId = "hp_huge_$idx",
          workTitle = "Harry Potter e a Pedra Filosofal",
          contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
          publisher = "Editora Rocco",
          publicationYear = 1998 + (idx % 26),
          totalPages = 223 + (idx % 40),
          isbn13 = "97885325${(10000 + idx)}",
          language = "pt",
          canonicalLanguageTag = "pt-br",
          languageTierRank = LanguageTier.PT_BR.rank,
          coverCandidates =
            (1..6).map { cIdx ->
              CoverCandidate("https://covers.example.com/$idx/$cIdx.jpg", MetadataSource.GOOGLE_BOOKS)
            },
          synopsisCandidates = giantSynopses,
          sources = listOf(MetadataSource.GOOGLE_BOOKS),
        )
      }

    val fastGb =
      AuditFakeAdapter(MetadataSource.GOOGLE_BOOKS) {
        SourceAdapterResult(
          SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.SUCCESS, hugeCandidates.size),
          hugeCandidates,
        )
      }
    val slowerOl =
      AuditFakeAdapter(MetadataSource.OPEN_LIBRARY) {
        kotlinx.coroutines.delay(350L)
        SourceAdapterResult(
          SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1),
          listOf(
            SearchEditionCandidate(
              candidateId = "ol_hp_extra",
              workTitle = "Harry Potter e a Pedra Filosofal",
              contributors = listOf(CandidateContributor("J. K. Rowling", ContributorRole.AUTHOR)),
              publisher = "Rocco",
              isbn13 = "9788532530783",
              language = "pt",
              canonicalLanguageTag = "pt-br",
              languageTierRank = LanguageTier.PT_BR.rank,
              sources = listOf(MetadataSource.OPEN_LIBRARY),
            )
          ),
        )
      }
    val emptyOther = { src: MetadataSource ->
      AuditFakeAdapter(src) { SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY)) }
    }

    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = slowerOl,
        googleBooksAdapter = fastGb,
        crossrefAdapter = emptyOther(MetadataSource.CROSSREF),
        wikidataAdapter = emptyOther(MetadataSource.WIKIDATA),
        brasilApiAdapter = emptyOther(MetadataSource.BRASIL_API),
        connectivityChecker = object : ConnectivityChecker { override fun isOnline(): Boolean = true },
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )

    val emissions = orchestrator.searchProgressive("Harry Potter e a Pedra Filosofal", "pt-BR").toList()
    assertTrue("Expected at least 3 progressive emissions (loading, partial, final), got ${emissions.size}", emissions.size >= 3)
    assertTrue("First streaming emission should be marked partial", emissions.first().isPartialResult)
    assertTrue(
      "Second streaming emission should carry partial Google Books groups",
      emissions[1].isPartialResult && emissions[1].workGroups.isNotEmpty(),
    )
    assertFalse("Final streaming emission should not be partial", emissions.last().isPartialResult)
    assertTrue(emissions.last().workGroups.isNotEmpty())

    // Verify bounded SQLite cache row size (< 850 KB limit avoids CursorWindow SQLiteBlobTooBigException)
    val cachedRow =
      db.searchCacheDao().getValidEntry(
        cacheKey = "v7:q:harry potter e a pedra filosofal:exl=:pref=pt:exp=false",
        nowMillis = System.currentTimeMillis(),
      )
    assertTrue("Cache entry must be saved cleanly", cachedRow != null)
    val payloadBytes = cachedRow!!.payloadJson.toByteArray(Charsets.UTF_8).size
    assertTrue("Cache payload exceeded 850KB safe threshold: $payloadBytes bytes", payloadBytes <= 850_000)

    // Verify secondary API timeout is hidden discreetly when primary works are present, while primary timeout stays visible
    val sampleGroup = emissions.last().workGroups.first()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
        DipeLibrisTheme {
          BookSearchScreen(
            uiState =
              BookSearchUiState(
                query = "Harry Potter e a Pedra Filosofal",
                hasSearched = true,
                response =
                  HybridSearchResponse(
                    query = "Harry Potter e a Pedra Filosofal",
                    isIsbnQuery = false,
                    isFromCache = false,
                    isOffline = false,
                    workGroups = listOf(sampleGroup),
                    sourceStatuses =
                      listOf(
                        SourceQueryStatus(MetadataSource.OPEN_LIBRARY, ProviderOutcomeState.SUCCESS, 1),
                        SourceQueryStatus(MetadataSource.GOOGLE_BOOKS, ProviderOutcomeState.ERROR, message = "Batch deadline exceeded"),
                        SourceQueryStatus(MetadataSource.APPLE_BOOKS, ProviderOutcomeState.ERROR, message = "Batch deadline exceeded"),
                      ),
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
    composeTestRule.onNodeWithText("Google Books (timed out)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Apple Books (timed out)").assertDoesNotExist()
  }
}
