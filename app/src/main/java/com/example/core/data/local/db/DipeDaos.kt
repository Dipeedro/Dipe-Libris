package com.example.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
  @Transaction
  @Query("SELECT * FROM works ORDER BY createdAtEpochMillis DESC")
  fun observeAllCatalogBooks(): Flow<List<WorkWithEditions>>

  @Transaction
  @Query("SELECT * FROM works WHERE status = :status ORDER BY updatedAtEpochMillis DESC")
  fun observeBooksByStatus(status: ReadingStatus): Flow<List<WorkWithEditions>>

  @Transaction
  @Query("SELECT * FROM works WHERE isFavorite = 1 ORDER BY updatedAtEpochMillis DESC")
  fun observeFavoriteBooks(): Flow<List<WorkWithEditions>>

  @Transaction
  @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
  fun observeCatalogBook(workId: Long): Flow<WorkWithEditions?>

  @Query("SELECT COUNT(*) FROM works")
  fun observeTotalWorksCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM works WHERE status = :status")
  fun observeWorksCountByStatus(status: ReadingStatus): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertWork(work: WorkEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEdition(edition: EditionEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCopy(copy: BookCopyEntity): Long

  @Transaction
  suspend fun insertBookAggregate(
    work: WorkEntity,
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
  ): Long {
    val workId = insertWork(work)
    val editionId =
      insertEdition(
        EditionEntity(
          workId = workId,
          publisherOrNote = publisherOrNote,
          publicationYear = publicationYear,
          totalPages = totalPages,
        )
      )
    insertCopy(BookCopyEntity(editionId = editionId))
    return workId
  }

  @Query("UPDATE works SET status = :status, updatedAtEpochMillis = :updatedAt WHERE id = :workId")
  suspend fun updateWorkStatus(workId: Long, status: ReadingStatus, updatedAt: Long)

  @Query("UPDATE works SET isFavorite = :isFavorite, updatedAtEpochMillis = :updatedAt WHERE id = :workId")
  suspend fun updateWorkFavorite(workId: Long, isFavorite: Boolean, updatedAt: Long)

  @Query("DELETE FROM works WHERE id = :workId")
  suspend fun deleteWorkById(workId: Long)
}

@Dao
interface DiaryDao {
  @Query(
    """
    SELECT d.*, w.title AS linkedBookTitle, w.author AS linkedBookAuthor
    FROM diary_entries d
    LEFT JOIN works w ON d.workId = w.id
    ORDER BY d.createdAtEpochMillis DESC
    """
  )
  fun observeAllEntriesWithBook(): Flow<List<DiaryEntryWithBookRow>>

  @Query(
    """
    SELECT d.*, w.title AS linkedBookTitle, w.author AS linkedBookAuthor
    FROM diary_entries d
    LEFT JOIN works w ON d.workId = w.id
    WHERE d.workId = :workId
    ORDER BY d.createdAtEpochMillis DESC
    """
  )
  fun observeEntriesForWork(workId: Long): Flow<List<DiaryEntryWithBookRow>>

  @Query("SELECT COUNT(*) FROM diary_entries")
  fun observeEntriesCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEntry(entry: DiaryEntryEntity): Long

  @Query("DELETE FROM diary_entries WHERE id = :entryId")
  suspend fun deleteEntryById(entryId: Long)
}

@Dao
interface ShelfDao {
  @Query("SELECT * FROM shelves ORDER BY isFeatured DESC, createdAtEpochMillis DESC")
  fun observeAllShelves(): Flow<List<ShelfEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertShelf(shelf: ShelfEntity): Long
}
