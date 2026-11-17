package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.db.WorkEntity
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DipeLibrisDatabaseIntegrityTest {

  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository
  private lateinit var diaryRepo: RoomDiaryRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    libraryRepo = RoomLibraryRepository(db.bookDao(), db.shelfDao())
    diaryRepo = RoomDiaryRepository(db.diaryDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun addBookPersistsWorkEditionAndCopyAtomically() = runBlocking {
    val workId =
      libraryRepo.addBook(
        title = "Memórias Póstumas de Brás Cubas",
        author = "Machado de Assis",
        totalPages = 320,
        publicationYear = 1881,
        editionNote = "Editora 34",
        status = ReadingStatus.FINISHED,
      )

    val book = libraryRepo.observeBook(workId).first()
    assertNotNull(book)
    assertEquals("Memórias Póstumas de Brás Cubas", book!!.title)
    assertNotNull(book.primaryEdition)
    assertEquals(320, book.primaryEdition!!.totalPages)
    assertEquals(1881, book.primaryEdition!!.publicationYear)
    assertNotNull(book.primaryCopy)
    assertEquals(book.primaryEdition!!.id, book.primaryCopy!!.editionId)

    assertEquals(1, countRows("works"))
    assertEquals(1, countRows("editions"))
    assertEquals(1, countRows("book_copies"))
  }

  @Test
  fun transactionRollsBackWorkWhenEditionInsertionFails() = runBlocking {
    try {
      db.withTransaction {
        db.bookDao()
          .insertWork(
            WorkEntity(
              title = "Obra Incompleta",
              author = "Autor Teste",
              status = ReadingStatus.UNREAD,
            )
          )
        // Violates foreign key constraint on works(id)
        db.bookDao().insertEdition(EditionEntity(workId = 999_999L, totalPages = 120))
      }
      fail("Expected SQLiteConstraintException due to invalid foreign key")
    } catch (_: SQLiteConstraintException) {
      // Expected rollback
    }

    assertEquals(0, countRows("works"))
    assertEquals(0, countRows("editions"))
    assertEquals(0, countRows("book_copies"))
  }

  @Test
  fun duplicatePrimaryKeyAbortsInsteadOfReplacing() = runBlocking {
    val id =
      db.bookDao()
        .insertWork(
          WorkEntity(
            id = 42L,
            title = "Original Title",
            author = "Original Author",
          )
        )
    assertEquals(42L, id)

    try {
      db.bookDao()
        .insertWork(
          WorkEntity(
            id = 42L,
            title = "Overwrite Attempt",
            author = "Another Author",
          )
        )
      fail("Expected SQLiteConstraintException on duplicate primary key with ABORT")
    } catch (_: SQLiteConstraintException) {
      // Expected
    }

    val stored = libraryRepo.observeBook(42L).first()
    assertEquals("Original Title", stored?.title)
  }

  @Test
  fun deletingBookCascadesEditionsAndCopiesWhilePreservingDiaryNotesWithNullWorkId() =
    runBlocking {
      val workId =
        libraryRepo.addBook(
          title = "A Paixão Segundo G.H.",
          author = "Clarice Lispector",
          totalPages = 192,
          publicationYear = 1964,
          editionNote = null,
          status = ReadingStatus.READING,
        )
      val entryId = diaryRepo.addEntry("Anotação essencial de leitura.", workId)

      assertEquals(1, countRows("works"))
      assertEquals(1, countRows("editions"))
      assertEquals(1, countRows("book_copies"))
      assertEquals(1, countRows("diary_entries"))

      libraryRepo.deleteBook(workId)

      assertEquals(0, countRows("works"))
      assertEquals(0, countRows("editions"))
      assertEquals(0, countRows("book_copies"))

      val entries = diaryRepo.observeAllEntries().first()
      assertEquals(1, entries.size)
      assertEquals(entryId, entries.first().id)
      assertEquals("Anotação essencial de leitura.", entries.first().content)
      assertNull(entries.first().workId)
      assertNull(entries.first().linkedBookTitle)
    }

  @Test
  fun productionDatabaseBuilderOpensWithoutDestructiveMigration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val builtDb = DipeLibrisDatabase.build(context)
    try {
      val writable = builtDb.openHelper.writableDatabase
      assertTrue(writable.isOpen)
      assertEquals(1, writable.version)
    } finally {
      builtDb.close()
      context.deleteDatabase(DipeLibrisDatabase.DATABASE_NAME)
    }
  }

  private fun countRows(table: String): Int {
    db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
      assertTrue(cursor.moveToFirst())
      return cursor.getInt(0)
    }
  }
}
