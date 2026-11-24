package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.bibliographic.domain.CoverRanker
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.network.BibliographicSourceAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.SourceAdapterResult
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverPlaceholderDetector
import com.example.core.data.local.media.CoverUpgradeCoordinator
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.local.preferences.DataStorePreferencesRepository
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.designsystem.DipeLibrisTheme
import com.example.core.designsystem.components.BookCoverArtwork
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CoverCandidate
import com.example.core.model.MetadataSource
import com.example.core.model.ProviderOutcomeState
import com.example.core.model.ReadingStatus
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SourceQueryStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoverPipelineForensicTest {

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
  fun coverRanker_compactSelectionNeverCorruptsFrontcoversAndPreservesSourceDiversity() {
    val googleCandidates =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrl = "http://books.google.com/books/content?id=Vol123&printsec=frontcover&img=1&zoom=1&edge=curl",
        matchesIsbn = true,
        matchesPublisherYear = true,
        matchesLanguage = true,
      )
    val openLibraryCover =
      CoverCandidate(
        imageUrl = "https://covers.openlibrary.org/b/id/987654-L.jpg?default=false",
        source = MetadataSource.OPEN_LIBRARY,
        matchesIsbn = true,
        matchesPublisherYear = true,
        matchesLanguage = true,
        widthHint = 333,
        heightHint = 500,
      )

    val rankedCombined = CoverRanker.rankAndDeduplicate(googleCandidates + openLibraryCover)
    // Verified Open Library -L cover should beat speculative synthetic Google URLs
    assertEquals(MetadataSource.OPEN_LIBRARY, rankedCombined.first().source)

    // Even when Google candidates come first in an ordered list, compact selection must:
    // 1. NEVER emit /frontcovers/...?zoom=1
    // 2. NEVER emit fife=w800 or zoom=0
    // 3. Preserve Open Library fallback instead of filling both slots with Google variants
    val googleFirstUrls = googleCandidates.map { it.imageUrl } + openLibraryCover.imageUrl
    val compactUrls = CoverRanker.selectForDisplaySize(googleFirstUrls, maxWidthDp = 52)
    assertEquals(2, compactUrls.size)
    assertFalse(compactUrls.any { "/frontcovers/" in it && "zoom=1" in it })
    assertFalse(compactUrls.any { "fife=w800" in it || "zoom=0" in it })
    assertTrue(compactUrls.any { "covers.openlibrary.org" in it && "-M.jpg" in it })
    assertTrue(compactUrls.any { "books.google.com/books/content" in it && "zoom=1" in it })

    // Verify Google Books multi-link preservation and Apple query-string support
    val multiLinks =
      CoverRanker.buildGoogleBooksCoverCandidates(
        rawUrls =
          listOf(
            "http://books.google.com/books/content?id=Vol123&zoom=3",
            "http://books.google.com/books/content?id=Vol123&zoom=1",
          ),
        matchesIsbn = true,
        matchesPublisherYear = false,
        matchesLanguage = true,
      )
    assertTrue(multiLinks.any { "zoom=3" in it.imageUrl })
    assertTrue(multiLinks.any { "zoom=1" in it.imageUrl })

    val appleWithQuery =
      CoverRanker.buildAppleBooksCoverCandidates(
        artworkUrl100 = "https://is1-ssl.mzstatic.com/image/thumb/Publication/100x100bb.jpg?token=abc",
        matchesIsbn = false,
        matchesPublisherYear = false,
        matchesLanguage = false,
      )
    assertTrue(appleWithQuery.any { "1200x1800bb.jpg?token=abc" in it.imageUrl })
    val compactApple = CoverRanker.selectForDisplaySize(appleWithQuery.map { it.imageUrl }, maxWidthDp = 44)
    assertTrue(compactApple.first().contains("200x300bb.jpg?token=abc"))
  }

  @Test
  fun placeholderDetector_rejectsLargeGoogleImageNotAvailableAndAcceptsRealCovers() {
    val googlePlaceholderPng = createGoogleStylePlaceholderPng(400, 600)
    // Confirm test fixture is > 11KB & > 48px so old heuristics would have accepted it!
    assertTrue(googlePlaceholderPng.size > 11_000)

    assertTrue(
      CoverPlaceholderDetector.isGooglePlaceholderBytes(
        url = "https://books.google.com/books/publisher/content/images/frontcovers/XYZ?fife=w800",
        contentType = "image/png",
        bytes = googlePlaceholderPng,
      )
    )

    val placeholderBmp = createGoogleStylePlaceholderBitmap(240, 360)
    assertTrue(
      CoverPlaceholderDetector.isKnownGooglePlaceholderDrawable(
        BitmapDrawable(context.resources, placeholderBmp)
      )
    )
    placeholderBmp.recycle()

    // Non-image HTML payload must also be rejected
    assertTrue(
      CoverPlaceholderDetector.isGooglePlaceholderBytes(
        url = "https://books.google.com/books/content?id=XYZ&zoom=1",
        contentType = "text/html; charset=utf-8",
        bytes = "<html><body>404</body></html>".toByteArray(),
      )
    )

    // Real colorful book cover must NOT be rejected
    val realColorfulJpeg = createColorfulCoverJpeg(320, 480)
    assertFalse(
      CoverPlaceholderDetector.isGooglePlaceholderBytes(
        url = "https://books.google.com/books/publisher/content/images/frontcovers/REAL?fife=w800",
        contentType = "image/jpeg",
        bytes = realColorfulJpeg,
      )
    )

    // Minimalist white cover with bold black top/bottom title & author typography must NOT be rejected
    val whiteEditorialCoverJpeg = createWhiteEditorialCoverJpeg(320, 480)
    assertFalse(
      CoverPlaceholderDetector.isGooglePlaceholderBytes(
        url = "https://books.google.com/books/content?id=EDIT&zoom=0",
        contentType = "image/jpeg",
        bytes = whiteEditorialCoverJpeg,
      )
    )
  }

  @Test
  fun coverStorageManager_rejectsLargeGooglePlaceholderAndFallsBackToRealCover(): Unit = runBlocking {
    val largePlaceholderPng = createGoogleStylePlaceholderPng(400, 600)
    val realColorfulJpeg = createColorfulCoverJpeg(360, 540)

    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val u = chain.request().url.toString()
          val (mime, bytes) =
            if ("frontcovers" in u || "zoom=0" in u) {
              "image/png" to largePlaceholderPng
            } else {
              "image/jpeg" to realColorfulJpeg
            }
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", mime)
            .body(bytes.toResponseBody(mime.toMediaType()))
            .build()
        }
        .build()

    val manager = InternalCoverStorageManager(context = context, okHttpClient = fakeClient)
    val savedPath =
      manager.downloadBestAvailableCover(
        imageUrls =
          listOf(
            "https://books.google.com/books/publisher/content/images/frontcovers/BAD1?fife=w800",
            "https://books.google.com/books/content?id=BAD1&zoom=0",
            "https://covers.openlibrary.org/b/id/12345-L.jpg?default=false",
          ),
        minPreferredWidth = 300,
      )
    assertNotNull(savedPath)
    val dims = manager.getLocalCoverDimensions(savedPath)
    assertEquals(360 to 540, dims)
    manager.deleteCoverIfManaged(savedPath)
  }

  @Test
  fun coverUpgradeCoordinator_retriesAfterOfflineStartupAndNeverOverwritesWithPlaceholder() = runBlocking {
    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    val lowResFile = File(coversDir, "cover_lowres_test.jpg")
    FileOutputStream(lowResFile).use { out -> out.write(createColorfulCoverJpeg(128, 192)) }

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
      title = "O Pequeno Príncipe",
      author = "Antoine de Saint-Exupéry",
      totalPages = 96,
      publicationYear = 2015,
      editionNote = "Agir",
      isbn = "9788522030972",
      coverUri = lowResFile.absolutePath,
      status = ReadingStatus.UNREAD,
    )

    var isOnline = false
    var serveRealUpgrade = false
    val largePlaceholderPng = createGoogleStylePlaceholderPng(420, 630)
    val realHdJpeg = createColorfulCoverJpeg(480, 720)

    val fakeCoverClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val (mime, bytes) =
            if (!serveRealUpgrade) {
              "image/png" to largePlaceholderPng
            } else {
              "image/jpeg" to realHdJpeg
            }
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", mime)
            .body(bytes.toResponseBody(mime.toMediaType()))
            .build()
        }
        .build()

    val coverStorage = InternalCoverStorageManager(context = context, okHttpClient = fakeCoverClient)
    val fakeOlAdapter =
      object : BibliographicSourceAdapter {
        override val source: MetadataSource = MetadataSource.OPEN_LIBRARY
        override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String): SourceAdapterResult =
          SourceAdapterResult(
            SourceQueryStatus(source, ProviderOutcomeState.SUCCESS, 1),
            listOf(
              SearchEditionCandidate(
                candidateId = "ol_pp",
                workTitle = "O Pequeno Príncipe",
                contributors = listOf(CandidateContributor("Antoine de Saint-Exupéry", ContributorRole.AUTHOR)),
                isbn13 = "9788522030972",
                coverCandidates =
                  listOf(
                    CoverCandidate(
                      imageUrl = "https://books.google.com/books/publisher/content/images/frontcovers/PP1?fife=w800",
                      source = MetadataSource.GOOGLE_BOOKS,
                      matchesIsbn = true,
                    )
                  ),
                sources = listOf(MetadataSource.GOOGLE_BOOKS),
              )
            ),
          )

        override suspend fun searchByQuery(query: String): SourceAdapterResult =
          SourceAdapterResult(SourceQueryStatus(source, ProviderOutcomeState.EMPTY))
      }
    val emptyAdapter = { src: MetadataSource ->
      object : BibliographicSourceAdapter {
        override val source: MetadataSource = src
        override suspend fun searchByIsbn(isbn13: String?, isbn10: String?, rawIsbn: String) =
          SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
        override suspend fun searchByQuery(query: String) =
          SourceAdapterResult(SourceQueryStatus(src, ProviderOutcomeState.EMPTY))
      }
    }

    val connectivity =
      object : ConnectivityChecker {
        override fun isOnline(): Boolean = isOnline
      }
    val orchestrator =
      HybridSearchOrchestrator(
        openLibraryAdapter = fakeOlAdapter,
        googleBooksAdapter = emptyAdapter(MetadataSource.GOOGLE_BOOKS),
        crossrefAdapter = emptyAdapter(MetadataSource.CROSSREF),
        wikidataAdapter = emptyAdapter(MetadataSource.WIKIDATA),
        brasilApiAdapter = emptyAdapter(MetadataSource.BRASIL_API),
        connectivityChecker = connectivity,
        searchCacheDao = db.searchCacheDao(),
        editionDao = db.editionDao(),
        bookDao = db.bookDao(),
        libraryRepository = libraryRepo,
      )
    val coordinator =
      CoverUpgradeCoordinator(
        context = context,
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        coverStorageManager = coverStorage,
        orchestrator = orchestrator,
        preferencesRepository = DataStorePreferencesRepository(context),
        connectivityChecker = connectivity,
      )

    // 1. Offline call must not lock hasStartedSession permanently
    coordinator.upgradeLowResCoversIfNeeded()
    val editionAfterOffline = db.editionDao().getAutoManagedCoverEditions().single()
    assertEquals(lowResFile.absolutePath, editionAfterOffline.coverUri)

    // 2. Online call receiving large Google placeholder must NOT overwrite low-res cover
    isOnline = true
    serveRealUpgrade = false
    coordinator.upgradeLowResCoversIfNeeded()
    val editionAfterPlaceholder = db.editionDao().getAutoManagedCoverEditions().single()
    assertEquals(lowResFile.absolutePath, editionAfterPlaceholder.coverUri)
    assertTrue(lowResFile.exists())

    // 3. Reset cooldown + hasStartedSession via fresh coordinator with real HD cover -> upgrades cleanly
    context.getSharedPreferences("dipe_cover_upgrade_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    serveRealUpgrade = true
    val coordinator2 =
      CoverUpgradeCoordinator(
        context = context,
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        coverStorageManager = coverStorage,
        orchestrator = orchestrator,
        preferencesRepository = DataStorePreferencesRepository(context),
        connectivityChecker = connectivity,
      )
    coordinator2.upgradeLowResCoversIfNeeded()
    val editionAfterReal = db.editionDao().getAutoManagedCoverEditions().single()
    assertTrue(editionAfterReal.coverUri != lowResFile.absolutePath)
    val newDims = coverStorage.getLocalCoverDimensions(editionAfterReal.coverUri)
    assertEquals(480 to 720, newDims)
    coverStorage.deleteCoverIfManaged(editionAfterReal.coverUri)
  }

  @Test
  fun bookCoverArtwork_rendersTypographicFallbackWhenNoPhotoLoads() {
    composeTestRule.setContent {
      DipeLibrisTheme {
        BookCoverArtwork(
          title = "Dom Casmurro",
          author = "Machado de Assis",
          isFavorite = false,
          coverUri = null,
          compact = true,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
      .onNodeWithContentDescription(context.getString(R.string.cd_book_cover, "Dom Casmurro"))
      .assertIsDisplayed()
  }

  private fun createGoogleStylePlaceholderBitmap(width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(238, 238, 238))
    val borderPaint =
      Paint().apply {
        color = Color.rgb(190, 190, 190)
        style = Paint.Style.STROKE
        strokeWidth = 3f
      }
    canvas.drawRect(width * 0.22f, height * 0.34f, width * 0.78f, height * 0.66f, borderPaint)
    val textPaint =
      Paint().apply {
        color = Color.rgb(130, 130, 130)
        textSize = width * 0.11f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
      }
    canvas.drawText("image", width * 0.5f, height * 0.45f, textPaint)
    canvas.drawText("not", width * 0.5f, height * 0.53f, textPaint)
    canvas.drawText("available", width * 0.5f, height * 0.61f, textPaint)
    return bmp
  }

  private fun createGoogleStylePlaceholderPng(width: Int, height: Int): ByteArray {
    val bmp = createGoogleStylePlaceholderBitmap(width, height)
    val raw =
      ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
      }
    bmp.recycle()
    // Ensure payload size exceeds the old 11KB threshold to verify visual detector (not byte count) catches it
    return if (raw.size > 11_500) raw else raw + ByteArray(12_000 - raw.size) { 0 }
  }

  private fun createColorfulCoverJpeg(width: Int, height: Int): ByteArray {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(28, 72, 126))
    val accentPaint = Paint().apply { color = Color.rgb(225, 174, 72) }
    canvas.drawRect(width * 0.12f, height * 0.15f, width * 0.88f, height * 0.42f, accentPaint)
    return ByteArrayOutputStream().use { out ->
      bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
      bmp.recycle()
      out.toByteArray()
    }
  }

  private fun createWhiteEditorialCoverJpeg(width: Int, height: Int): ByteArray {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    val inkPaint =
      Paint().apply {
        color = Color.rgb(18, 18, 18)
        style = Paint.Style.FILL
      }
    // Bold black title block in upper third + author block in lower band
    canvas.drawRect(width * 0.12f, height * 0.08f, width * 0.88f, height * 0.28f, inkPaint)
    canvas.drawRect(width * 0.22f, height * 0.82f, width * 0.78f, height * 0.90f, inkPaint)
    return ByteArrayOutputStream().use { out ->
      bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
      bmp.recycle()
      out.toByteArray()
    }
  }
}
