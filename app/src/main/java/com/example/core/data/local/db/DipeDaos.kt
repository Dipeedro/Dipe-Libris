package com.example.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

private const val LIBRARY_BOOK_SUMMARY_SELECT = """
  SELECT
    w.*,
    pe.id AS pe_id,
    pe.workId AS pe_workId,
    pe.publisherOrNote AS pe_publisherOrNote,
    pe.publicationYear AS pe_publicationYear,
    pe.totalPages AS pe_totalPages,
    pe.isbn AS pe_isbn,
    pe.coverUri AS pe_coverUri,
    pe.language AS pe_language,
    pe.format AS pe_format,
    pe.translator AS pe_translator,
    pe.isPrimary AS pe_isPrimary,
    pe.createdAtEpochMillis AS pe_createdAtEpochMillis,
    pc.id AS pc_id,
    pc.editionId AS pc_editionId,
    pc.currentPage AS pc_currentPage,
    pc.physicalFormat AS pc_physicalFormat,
    pc.personalNote AS pc_personalNote,
    pc.label AS pc_label,
    pc.mediaType AS pc_mediaType,
    pc.locationOrPlatform AS pc_locationOrPlatform,
    pc.acquisitionEpochMillis AS pc_acquisitionEpochMillis,
    pc.createdAtEpochMillis AS pc_createdAtEpochMillis,
    (SELECT COUNT(*) FROM editions e_cnt WHERE e_cnt.workId = w.id) AS editionCount,
    (
      SELECT COUNT(*)
      FROM book_copies c_cnt
      INNER JOIN editions e_c ON c_cnt.editionId = e_c.id
      WHERE e_c.workId = w.id
    ) AS copyCount,
    rs.id AS activeSessionId,
    rs.progressPercent AS activeProgressPercent,
    rs.currentPage AS activeCurrentPage,
    COALESCE(pe.totalPages, rs.totalPagesSnapshot) AS activeTotalPages,
    rs.rereadNumber AS activeRereadNumber,
    pc.mediaType AS activeCopyMediaType,
    pc.label AS activeCopyLabel
  FROM works w
  LEFT JOIN reading_sessions rs ON rs.id = (
    SELECT rs_sub.id
    FROM reading_sessions rs_sub
    WHERE rs_sub.workId = w.id AND rs_sub.status IN ('READING', 'PAUSED')
    ORDER BY rs_sub.rereadNumber DESC, rs_sub.id DESC
    LIMIT 1
  )
  LEFT JOIN book_copies pc ON pc.id = COALESCE(
    rs.copyId,
    (
      SELECT c_sub.id
      FROM book_copies c_sub
      INNER JOIN editions e_sub ON c_sub.editionId = e_sub.id
      WHERE e_sub.workId = w.id
      ORDER BY e_sub.isPrimary DESC, e_sub.id ASC, c_sub.id ASC
      LIMIT 1
    )
  )
  LEFT JOIN editions pe ON pe.id = COALESCE(
    pc.editionId,
    (
      SELECT e_sub.id
      FROM editions e_sub
      WHERE e_sub.workId = w.id
      ORDER BY e_sub.isPrimary DESC, e_sub.id ASC
      LIMIT 1
    )
  )
"""

@Dao
interface BookDao {
  @Query("$LIBRARY_BOOK_SUMMARY_SELECT ORDER BY w.createdAtEpochMillis DESC")
  fun observeAllCatalogBookSummaries(): Flow<List<LibraryBookSummaryRow>>

  @Query("$LIBRARY_BOOK_SUMMARY_SELECT WHERE w.status = :status ORDER BY w.updatedAtEpochMillis DESC")
  fun observeBookSummariesByStatus(status: ReadingStatus): Flow<List<LibraryBookSummaryRow>>

  @Query("$LIBRARY_BOOK_SUMMARY_SELECT WHERE w.isFavorite = 1 ORDER BY w.updatedAtEpochMillis DESC")
  fun observeFavoriteBookSummaries(): Flow<List<LibraryBookSummaryRow>>

  @Query("$LIBRARY_BOOK_SUMMARY_SELECT WHERE w.id = :workId LIMIT 1")
  fun observeCatalogBookSummary(workId: Long): Flow<LibraryBookSummaryRow?>

  @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
  fun observeWork(workId: Long): Flow<WorkEntity?>

  @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
  suspend fun getWorkById(workId: Long): WorkEntity?

  @Query("SELECT COUNT(*) FROM works")
  fun observeTotalWorksCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM works WHERE status = :status")
  fun observeWorksCountByStatus(status: ReadingStatus): Flow<Int>

  @Query("SELECT COUNT(*) FROM editions")
  fun observeTotalEditionsCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM book_copies")
  fun observeTotalCopiesCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertWork(work: WorkEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEdition(edition: EditionEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCopy(copy: BookCopyEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReadingSession(session: ReadingSessionEntity): Long

  @Transaction
  suspend fun insertBookAggregate(
    work: WorkEntity,
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
    isbn: String? = null,
    coverUri: String? = null,
    language: String? = null,
    format: EditionFormat? = null,
    translator: String? = null,
    initialCopyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    initialCopyLabel: String? = null,
    initialCopyLocation: String? = null,
  ): Long {
    val workId = insertWork(work)
    val editionId =
      insertEdition(
        EditionEntity(
          workId = workId,
          publisherOrNote = publisherOrNote,
          publicationYear = publicationYear,
          totalPages = totalPages,
          isbn = isbn,
          coverUri = coverUri,
          language = language,
          format = format,
          translator = translator,
          isPrimary = true,
          createdAtEpochMillis = work.createdAtEpochMillis,
        )
      )
    val copyId =
      insertCopy(
        BookCopyEntity(
          editionId = editionId,
          label = initialCopyLabel,
          mediaType = initialCopyMediaType,
          locationOrPlatform = initialCopyLocation,
          createdAtEpochMillis = work.createdAtEpochMillis,
        )
      )
    if (work.status == ReadingStatus.READING || work.status == ReadingStatus.PAUSED || work.status == ReadingStatus.FINISHED) {
      val pct = if (work.status == ReadingStatus.FINISHED) 100f else 0f
      val currentPage = if (work.status == ReadingStatus.FINISHED) (totalPages ?: 0) else 0
      insertReadingSession(
        ReadingSessionEntity(
          workId = workId,
          copyId = copyId,
          status = work.status,
          rereadNumber = 1,
          currentPage = currentPage,
          totalPagesSnapshot = totalPages,
          progressPercent = pct,
          startEpochMillis = work.createdAtEpochMillis,
          endEpochMillis = if (work.status == ReadingStatus.FINISHED) work.createdAtEpochMillis else null,
          pausedAtEpochMillis = if (work.status == ReadingStatus.PAUSED) work.createdAtEpochMillis else null,
        )
      )
    }
    return workId
  }

  @Query("UPDATE works SET status = :status, updatedAtEpochMillis = :updatedAt WHERE id = :workId")
  suspend fun updateWorkStatus(workId: Long, status: ReadingStatus, updatedAt: Long)

  @Query("UPDATE works SET isFavorite = :isFavorite, updatedAtEpochMillis = :updatedAt WHERE id = :workId")
  suspend fun updateWorkFavorite(workId: Long, isFavorite: Boolean, updatedAt: Long)

  @Query("UPDATE works SET title = :title, author = :author, updatedAtEpochMillis = :updatedAt WHERE id = :workId")
  suspend fun updateWorkMetadata(workId: Long, title: String, author: String, updatedAt: Long)

  @Query("DELETE FROM works WHERE id = :workId")
  suspend fun deleteWorkById(workId: Long)
}

@Dao
interface EditionDao {
  @Query("SELECT * FROM editions WHERE workId = :workId ORDER BY isPrimary DESC, id ASC")
  fun observeEditionsForWork(workId: Long): Flow<List<EditionEntity>>

  @Query("SELECT * FROM editions WHERE id = :editionId LIMIT 1")
  suspend fun getEditionById(editionId: Long): EditionEntity?

  @Query("SELECT coverUri FROM editions WHERE workId = :workId AND coverUri IS NOT NULL")
  suspend fun getCoverUrisForWork(workId: Long): List<String>

  @Query("SELECT COUNT(*) FROM editions WHERE workId = :workId")
  suspend fun countEditionsForWork(workId: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEdition(edition: EditionEntity): Long

  @Update
  suspend fun updateEdition(edition: EditionEntity)

  @Query("UPDATE editions SET isPrimary = CASE WHEN id = :editionId THEN 1 ELSE 0 END WHERE workId = :workId")
  suspend fun setPrimaryEdition(workId: Long, editionId: Long)

  @Query("DELETE FROM editions WHERE id = :editionId")
  suspend fun deleteEditionById(editionId: Long)
}

@Dao
interface CopyDao {
  @Query(
    """
    SELECT c.*
    FROM book_copies c
    INNER JOIN editions e ON c.editionId = e.id
    WHERE e.workId = :workId
    ORDER BY c.createdAtEpochMillis ASC, c.id ASC
    """
  )
  fun observeCopiesForWork(workId: Long): Flow<List<BookCopyEntity>>

  @Query("SELECT * FROM book_copies WHERE id = :copyId LIMIT 1")
  suspend fun getCopyById(copyId: Long): BookCopyEntity?

  @Query(
    """
    SELECT c.*
    FROM book_copies c
    INNER JOIN editions e ON c.editionId = e.id
    WHERE e.workId = :workId
    ORDER BY e.isPrimary DESC, e.id ASC, c.id ASC
    LIMIT 1
    """
  )
  suspend fun getDefaultCopyForWork(workId: Long): BookCopyEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCopy(copy: BookCopyEntity): Long

  @Update
  suspend fun updateCopy(copy: BookCopyEntity)

  @Query("UPDATE book_copies SET currentPage = :currentPage WHERE id = :copyId")
  suspend fun updateCopyCurrentPage(copyId: Long, currentPage: Int)

  @Query("DELETE FROM book_copies WHERE id = :copyId")
  suspend fun deleteCopyById(copyId: Long)
}

@Dao
interface ReadingJourneyDao {
  @Query("SELECT * FROM reading_sessions WHERE workId = :workId ORDER BY rereadNumber DESC, id DESC")
  fun observeSessionsForWork(workId: Long): Flow<List<ReadingSessionEntity>>

  @Query(
    """
    SELECT l.*
    FROM reading_progress_logs l
    INNER JOIN reading_sessions s ON l.sessionId = s.id
    WHERE s.workId = :workId
    ORDER BY l.recordedAtEpochMillis DESC, l.id DESC
    """
  )
  fun observeProgressLogsForWork(workId: Long): Flow<List<ReadingProgressLogEntity>>

  @Query("SELECT * FROM reading_sessions WHERE id = :sessionId LIMIT 1")
  suspend fun getSessionById(sessionId: Long): ReadingSessionEntity?

  @Query(
    """
    SELECT * FROM reading_sessions
    WHERE workId = :workId AND status IN ('READING', 'PAUSED')
    ORDER BY rereadNumber DESC, id DESC
    LIMIT 1
    """
  )
  suspend fun getActiveSessionForWork(workId: Long): ReadingSessionEntity?

  @Query("SELECT COALESCE(MAX(rereadNumber), 0) FROM reading_sessions WHERE workId = :workId")
  suspend fun getMaxRereadNumber(workId: Long): Int

  @Query("SELECT COUNT(*) FROM reading_sessions WHERE status = 'FINISHED'")
  fun observeCompletedJourneysCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM reading_sessions WHERE rereadNumber > 1")
  fun observeRereadsCount(): Flow<Int>

  @Query(
    """
    SELECT COALESCE(SUM(
      CASE
        WHEN currentPage > 0 THEN currentPage
        WHEN status = 'FINISHED' AND totalPagesSnapshot IS NOT NULL THEN totalPagesSnapshot
        ELSE 0
      END
    ), 0)
    FROM reading_sessions
    """
  )
  fun observeTotalPagesRead(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertSession(session: ReadingSessionEntity): Long

  @Update
  suspend fun updateSession(session: ReadingSessionEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertProgressLog(log: ReadingProgressLogEntity): Long
}

@Dao
interface DiaryDao {
  @Query(
    """
    SELECT d.*, w.title AS linkedBookTitle, w.author AS linkedBookAuthor
    FROM diary_entries d
    INNER JOIN works w ON d.workId = w.id
    ORDER BY d.createdAtEpochMillis DESC
    """
  )
  fun observeAllEntriesWithBook(): Flow<List<DiaryEntryWithBookRow>>

  @Query(
    """
    SELECT d.*, w.title AS linkedBookTitle, w.author AS linkedBookAuthor
    FROM diary_entries d
    INNER JOIN works w ON d.workId = w.id
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
