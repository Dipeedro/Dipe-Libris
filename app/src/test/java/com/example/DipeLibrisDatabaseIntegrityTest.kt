package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DiaryEntryEntity
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
  fun deletingBookCascadesEditionsCopiesAndDiaryNotes() = runBlocking {
    val workId =
      libraryRepo.addBook(
        title = "A Paixão Segundo G.H.",
        author = "Clarice Lispector",
        totalPages = 192,
        publicationYear = 1964,
        editionNote = null,
        status = ReadingStatus.READING,
      )
    diaryRepo.addEntry("Anotação essencial de leitura.", workId)

    assertEquals(1, countRows("works"))
    assertEquals(1, countRows("editions"))
    assertEquals(1, countRows("book_copies"))
    assertEquals(1, countRows("diary_entries"))

    libraryRepo.deleteBook(workId)

    assertEquals(0, countRows("works"))
    assertEquals(0, countRows("editions"))
    assertEquals(0, countRows("book_copies"))
    assertEquals(0, countRows("diary_entries"))
    assertTrue(diaryRepo.observeAllEntries().first().isEmpty())
  }

  @Test
  fun insertingDiaryEntryWithInvalidWorkIdFailsForeignKeyConstraint() = runBlocking {
    try {
      db.diaryDao().insertEntry(DiaryEntryEntity(workId = 999_999L, content = "Nota sem livro"))
      fail("Expected SQLiteConstraintException when inserting diary entry without existing work")
    } catch (_: SQLiteConstraintException) {
      // Expected constraint enforcement
    }
    assertEquals(0, countRows("diary_entries"))
  }

  @Test
  fun migration1To2RemovesOrphanNotesAndPreservesBookLinkedNotes() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_v1_v2_test.db"
    context.deleteDatabase(dbName)

    val dbFile = context.getDatabasePath(dbName)
    dbFile.parentFile?.mkdirs()
    val rawV1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      rawV1.execSQL(
        """
        CREATE TABLE works (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          title TEXT NOT NULL,
          author TEXT NOT NULL,
          status TEXT NOT NULL,
          isFavorite INTEGER NOT NULL,
          typographyPresetOverride TEXT,
          createdAtEpochMillis INTEGER NOT NULL,
          updatedAtEpochMillis INTEGER NOT NULL
        )
        """.trimIndent()
      )
      rawV1.execSQL(
        """
        CREATE TABLE editions (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          workId INTEGER NOT NULL,
          publisherOrNote TEXT,
          publicationYear INTEGER,
          totalPages INTEGER,
          isbn TEXT,
          coverUri TEXT,
          FOREIGN KEY(workId) REFERENCES works(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      rawV1.execSQL("CREATE INDEX index_editions_workId ON editions (workId)")
      rawV1.execSQL(
        """
        CREATE TABLE book_copies (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          editionId INTEGER NOT NULL,
          currentPage INTEGER NOT NULL,
          physicalFormat TEXT,
          personalNote TEXT,
          FOREIGN KEY(editionId) REFERENCES editions(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      rawV1.execSQL("CREATE INDEX index_book_copies_editionId ON book_copies (editionId)")
      rawV1.execSQL(
        """
        CREATE TABLE shelves (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          description TEXT,
          type TEXT NOT NULL,
          isFeatured INTEGER NOT NULL,
          createdAtEpochMillis INTEGER NOT NULL
        )
        """.trimIndent()
      )
      rawV1.execSQL(
        """
        CREATE TABLE book_shelf_cross_ref (
          workId INTEGER NOT NULL,
          shelfId INTEGER NOT NULL,
          PRIMARY KEY(workId, shelfId),
          FOREIGN KEY(workId) REFERENCES works(id) ON UPDATE NO ACTION ON DELETE CASCADE,
          FOREIGN KEY(shelfId) REFERENCES shelves(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      rawV1.execSQL("CREATE INDEX index_book_shelf_cross_ref_shelfId ON book_shelf_cross_ref (shelfId)")
      rawV1.execSQL(
        """
        CREATE TABLE diary_entries (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          workId INTEGER,
          content TEXT NOT NULL,
          createdAtEpochMillis INTEGER NOT NULL,
          FOREIGN KEY(workId) REFERENCES works(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )
        """.trimIndent()
      )
      rawV1.execSQL("CREATE INDEX index_diary_entries_workId ON diary_entries (workId)")
      rawV1.execSQL(
        """
        CREATE TABLE reading_sessions (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          workId INTEGER NOT NULL,
          startEpochMillis INTEGER NOT NULL,
          endEpochMillis INTEGER,
          rereadNumber INTEGER NOT NULL,
          FOREIGN KEY(workId) REFERENCES works(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
      )
      rawV1.execSQL("CREATE INDEX index_reading_sessions_workId ON reading_sessions (workId)")

      rawV1.execSQL(
        "INSERT INTO works (id, title, author, status, isFavorite, createdAtEpochMillis, updatedAtEpochMillis) VALUES (1, 'Macunaíma', 'Mário de Andrade', 'READING', 0, 1000, 1000)"
      )
      rawV1.execSQL(
        "INSERT INTO diary_entries (id, workId, content, createdAtEpochMillis) VALUES (10, 1, 'Nota vinculada válida.', 2000)"
      )
      rawV1.execSQL(
        "INSERT INTO diary_entries (id, workId, content, createdAtEpochMillis) VALUES (11, NULL, 'Nota sem livro.', 3000)"
      )
      rawV1.execSQL(
        "INSERT INTO diary_entries (id, workId, content, createdAtEpochMillis) VALUES (12, 999, 'Nota órfã.', 4000)"
      )
      rawV1.version = 1
    } finally {
      rawV1.close()
    }

    val migratedDb =
      Room.databaseBuilder(context, DipeLibrisDatabase::class.java, dbName)
        .addMigrations(*DipeLibrisDatabase.MIGRATIONS)
        .allowMainThreadQueries()
        .build()
    try {
      val repo = RoomDiaryRepository(migratedDb.diaryDao())
      val surviving = repo.observeAllEntries().first()
      assertEquals(1, surviving.size)
      assertEquals(1L, surviving.first().workId)
      assertEquals("Nota vinculada válida.", surviving.first().content)

      migratedDb.bookDao().deleteWorkById(1L)
      assertTrue(repo.observeAllEntries().first().isEmpty())
    } finally {
      migratedDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun productionDatabaseBuilderOpensWithoutDestructiveMigration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val builtDb = DipeLibrisDatabase.build(context)
    try {
      val writable = builtDb.openHelper.writableDatabase
      assertTrue(writable.isOpen)
      assertEquals(2, writable.version)
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
