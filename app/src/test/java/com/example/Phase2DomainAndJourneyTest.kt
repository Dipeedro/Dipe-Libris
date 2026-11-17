package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomReadingJourneyRepository
import com.example.core.data.repository.RoomStatisticsRepository
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingStatus
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2DomainAndJourneyTest {

  private lateinit var context: Context
  private lateinit var db: DipeLibrisDatabase
  private lateinit var coverStorage: InternalCoverStorageManager
  private lateinit var libraryRepo: RoomLibraryRepository
  private lateinit var journeyRepo: RoomReadingJourneyRepository
  private lateinit var diaryRepo: RoomDiaryRepository
  private lateinit var statsRepo: RoomStatisticsRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    coverStorage = InternalCoverStorageManager(context)
    libraryRepo =
      RoomLibraryRepository(
        bookDao = db.bookDao(),
        shelfDao = db.shelfDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        readingJourneyDao = db.readingJourneyDao(),
        database = db,
        coverStorageManager = coverStorage,
      )
    journeyRepo =
      RoomReadingJourneyRepository(
        bookDao = db.bookDao(),
        editionDao = db.editionDao(),
        copyDao = db.copyDao(),
        journeyDao = db.readingJourneyDao(),
        database = db,
      )
    diaryRepo = RoomDiaryRepository(db.diaryDao())
    statsRepo =
      RoomStatisticsRepository(
        bookDao = db.bookDao(),
        diaryDao = db.diaryDao(),
        readingJourneyDao = db.readingJourneyDao(),
      )
  }

  @After
  fun tearDown() {
    db.close()
    File(context.filesDir, "covers").deleteRecursively()
  }

  @Test
  fun readingProgressMathHandlesPagesPercentDurationsAndIsbn() {
    val fromPage = ReadingProgressMath.calculateFromPage(page = 160, totalPages = 320)
    assertEquals(160, fromPage.currentPage)
    assertEquals(50f, fromPage.progressPercent, 0.01f)

    val clampedPage = ReadingProgressMath.calculateFromPage(page = 999, totalPages = 320)
    assertEquals(320, clampedPage.currentPage)
    assertEquals(100f, clampedPage.progressPercent, 0.01f)

    val fromPercent = ReadingProgressMath.calculateFromPercent(percent = 25f, totalPages = 400)
    assertEquals(100, fromPercent.currentPage)
    assertEquals(25f, fromPercent.progressPercent, 0.01f)

    val noTotal = ReadingProgressMath.calculateFromPercent(percent = 60f, totalPages = null, fallbackPage = 12)
    assertEquals(12, noTotal.currentPage)
    assertEquals(60f, noTotal.progressPercent, 0.01f)

    val netPaused =
      ReadingProgressMath.effectiveDurationMillis(
        startEpochMillis = 1_000L,
        endEpochMillis = null,
        pausedAtEpochMillis = 6_000L,
        accumulatedPausedMillis = 2_000L,
        nowEpochMillis = 11_000L,
      )
    // Raw 10_000 - accumulated 2_000 - active pause 5_000 = 3_000
    assertEquals(3_000L, netPaused)

    assertTrue(ReadingProgressMath.isValidIsbn(""))
    assertTrue(ReadingProgressMath.isValidIsbn("978-85-359-0277-8"))
    assertTrue(ReadingProgressMath.isValidIsbn("0-306-40615-2"))
    assertTrue(ReadingProgressMath.isValidIsbn("043942089X"))
    assertFalse(ReadingProgressMath.isValidIsbn("978-85-359-0277-0"))
    assertFalse(ReadingProgressMath.isValidIsbn("12345"))
  }

  @Test
  fun coverStorageImportsValidImageAndDeletesManagedFilesOnly(): Unit = runBlocking {
    val tempBitmapFile = File(context.cacheDir, "sample_cover.png")
    val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
    FileOutputStream(tempBitmapFile).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bitmap.recycle()

    val importedPath = coverStorage.importCoverFromUri(Uri.fromFile(tempBitmapFile))
    assertNotNull(importedPath)
    val managedFile = File(importedPath!!)
    assertTrue(managedFile.exists())

    // Deleting an unmanaged file outside coversDir must be a no-op
    coverStorage.deleteCoverIfManaged(tempBitmapFile.absolutePath)
    assertTrue(tempBitmapFile.exists())

    // Deleting the managed file removes it
    coverStorage.deleteCoverIfManaged(importedPath)
    assertFalse(managedFile.exists())
    tempBitmapFile.delete()
  }

  @Test
  fun readingJourneysProgressRereadsAndCopyHistoryArePreserved() = runBlocking {
    val workId =
      libraryRepo.addBook(
        title = "A Hora da Estrela",
        author = "Clarice Lispector",
        totalPages = 100,
        publicationYear = 1977,
        editionNote = "Rocco",
        status = ReadingStatus.UNREAD,
        isbn = "978-85-359-0277-8",
        language = "Português",
        format = EditionFormat.PAPERBACK,
        copyMediaType = CopyMediaType.PHYSICAL,
        copyLabel = "Edição capa amarela",
      )

    val secondEditionId =
      libraryRepo.addEdition(
        workId = workId,
        publisherOrNote = "Kindle BR",
        publicationYear = 2020,
        totalPages = 200,
        isbn = null,
        coverUri = null,
        language = "Português",
        format = EditionFormat.EBOOK,
        translator = null,
        setAsPrimary = false,
        alsoCreateCopy = true,
        copyMediaType = CopyMediaType.DIGITAL,
        copyLabel = "Kindle",
      )

    var detail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(2, detail.editions.size)
    val firstCopyId = detail.editions.first { it.edition.isPrimary }.copies.first().id
    val kindleCopyId = detail.editions.first { it.edition.id == secondEditionId }.copies.first().id

    // Start first journey on first copy
    val session1Id = journeyRepo.startJourney(workId, firstCopyId)
    journeyRepo.updateProgressByPage(session1Id, page = 40, note = "Meio do caminho")
    journeyRepo.pauseJourney(session1Id)
    journeyRepo.resumeJourney(session1Id)
    journeyRepo.updateProgressByPercent(session1Id, percent = 100f, note = "Concluído")

    detail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(ReadingStatus.FINISHED, detail.work.status)
    assertNull(detail.activeJourney)
    assertEquals(1, detail.journeys.size)
    assertEquals(2, detail.journeys.first().logs.size)
    assertEquals(100, detail.journeys.first().session.currentPage)

    // Start reread on second edition's digital copy
    val session2Id = journeyRepo.startReread(workId, kindleCopyId, notes = "Releitura digital")
    journeyRepo.updateProgressByPercent(session2Id, percent = 25f, note = "Capítulo 2")

    detail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(ReadingStatus.READING, detail.work.status)
    assertEquals(2, detail.journeys.size)
    assertNotNull(detail.activeJourney)
    assertEquals(2, detail.activeJourney!!.session.rereadNumber)
    assertEquals(50, detail.activeJourney!!.session.currentPage)
    assertEquals(25f, detail.activeJourney!!.session.progressPercent, 0.1f)

    // Deleting copy 1 keeps journey 1 preserved with copyId = null
    libraryRepo.deleteCopy(firstCopyId)
    detail = libraryRepo.observeBookDetail(workId).first()!!
    assertEquals(2, detail.journeys.size)
    assertNull(detail.journeys.first { it.session.id == session1Id }.copy)

    val snapshot = statsRepo.observeArchiveSnapshot().first()
    assertEquals(2, snapshot.editionsCount)
    assertEquals(1, snapshot.copiesCount)
    assertEquals(1, snapshot.completedJourneysCount)
    assertEquals(1, snapshot.rereadsCount)
    assertEquals(150, snapshot.totalPagesRead)
  }

  @Test
  fun largeCatalogSummaryQueryRemainsFastAndAccurateFor300Works() = runBlocking {
    for (i in 1..300) {
      val workId =
        libraryRepo.addBook(
          title = "Obra $i",
          author = "Autor ${i % 25}",
          totalPages = 200 + (i % 150),
          publicationYear = 1950 + (i % 70),
          editionNote = "Editora $i",
          status = if (i % 10 == 0) ReadingStatus.READING else ReadingStatus.UNREAD,
        )
      libraryRepo.addEdition(
        workId = workId,
        publisherOrNote = "Edição 2",
        publicationYear = 2022,
        totalPages = 240,
        isbn = null,
        coverUri = null,
        language = "PT",
        format = EditionFormat.EBOOK,
        translator = null,
        alsoCreateCopy = true,
        copyMediaType = CopyMediaType.DIGITAL,
      )
    }

    var summariesCount = 0
    val elapsedMs = measureTimeMillis {
      val books = libraryRepo.observeAllBooks().first()
      summariesCount = books.size
      assertEquals(2, books.first().editionCount)
      assertEquals(2, books.first().copyCount)
    }

    assertEquals(300, summariesCount)
    assertTrue("Expected summary query < 500ms, took ${elapsedMs}ms", elapsedMs < 500L)
  }
}
