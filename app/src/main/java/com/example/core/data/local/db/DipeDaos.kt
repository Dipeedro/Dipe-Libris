package com.example.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
import com.example.core.model.MetadataSuggestionStatus
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
    pe.isbn10 AS pe_isbn10,
    pe.isbn13 AS pe_isbn13,
    pe.coverUri AS pe_coverUri,
    pe.coverSource AS pe_coverSource,
    pe.isCoverCustomized AS pe_isCoverCustomized,
    pe.language AS pe_language,
    pe.format AS pe_format,
    pe.translator AS pe_translator,
    pe.editionLabel AS pe_editionLabel,
    pe.seriesName AS pe_seriesName,
    pe.seriesVolume AS pe_seriesVolume,
    pe.synopsis AS pe_synopsis,
    pe.synopsisSource AS pe_synopsisSource,
    pe.categoriesJson AS pe_categoriesJson,
    pe.externalIdsJson AS pe_externalIdsJson,
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
    pc.condition AS pc_condition,
    pc.acquisitionOrigin AS pc_acquisitionOrigin,
    pc.pricePaidMinorUnits AS pc_pricePaidMinorUnits,
    pc.currencyCode AS pc_currencyCode,
    pc.customCopyCode AS pc_customCopyCode,
    pc.customAttributesJson AS pc_customAttributesJson,
    pc.createdAtEpochMillis AS pc_createdAtEpochMillis,
    g.id AS g_id,
    g.slug AS g_slug,
    g.namePt AS g_namePt,
    g.nameEn AS g_nameEn,
    g.isCustom AS g_isCustom,
    g.createdAtEpochMillis AS g_createdAtEpochMillis,
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
    pc.label AS activeCopyLabel,
    (
      SELECT COUNT(*)
      FROM metadata_update_suggestions ms
      WHERE ms.workId = w.id AND ms.status = 'PENDING'
    ) AS pendingSuggestionsCount
  FROM works w
  LEFT JOIN genres g ON g.id = w.primaryGenreId
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

  @Query("$LIBRARY_BOOK_SUMMARY_SELECT WHERE w.id IN (:workIds)")
  suspend fun getCatalogBookSummariesByIds(workIds: List<Long>): List<LibraryBookSummaryRow>

  @Query("$LIBRARY_BOOK_SUMMARY_SELECT ORDER BY w.updatedAtEpochMillis DESC")
  suspend fun getAllCatalogBookSummariesSnapshot(): List<LibraryBookSummaryRow>

  @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
  fun observeWork(workId: Long): Flow<WorkEntity?>

  @Query("SELECT * FROM works WHERE id = :workId LIMIT 1")
  suspend fun getWorkById(workId: Long): WorkEntity?

  @Query("SELECT * FROM works WHERE externalWorkKey = :externalWorkKey LIMIT 1")
  suspend fun findWorkByExternalKey(externalWorkKey: String): WorkEntity?

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

  @Update
  suspend fun updateWork(work: WorkEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEdition(edition: EditionEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCopy(copy: BookCopyEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertReadingSession(session: ReadingSessionEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFts(entry: LibraryFtsEntity)

  @Query("DELETE FROM library_fts WHERE rowid = :workId")
  suspend fun deleteFtsByWorkId(workId: Long)

  @Query("SELECT rowid FROM library_fts WHERE library_fts MATCH :ftsQuery")
  suspend fun searchWorkIdsFts(ftsQuery: String): List<Long>

  @Transaction
  suspend fun insertBookAggregate(
    work: WorkEntity,
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
    isbn: String? = null,
    isbn10: String? = null,
    isbn13: String? = null,
    coverUri: String? = null,
    coverSource: String? = null,
    isCoverCustomized: Boolean = false,
    language: String? = null,
    format: EditionFormat? = null,
    translator: String? = null,
    editionLabel: String? = null,
    seriesName: String? = null,
    seriesVolume: String? = null,
    editionSynopsis: String? = null,
    editionSynopsisSource: String? = null,
    categoriesJson: String? = null,
    externalIdsJson: String? = null,
    initialCopyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    initialCopyLabel: String? = null,
    initialCopyPhysicalFormat: String? = null,
    initialCopyLocation: String? = null,
    initialCopyCondition: com.example.core.model.CopyCondition? = null,
    initialCopyOrigin: com.example.core.model.CopyOrigin? = null,
    initialCopyAcquisitionMillis: Long? = null,
    initialCopyPriceMinorUnits: Long? = null,
    initialCopyCurrencyCode: String? = null,
    initialCopyCustomCode: String? = null,
    initialCopyPersonalNote: String? = null,
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
          isbn10 = isbn10,
          isbn13 = isbn13,
          coverUri = coverUri,
          coverSource = coverSource,
          isCoverCustomized = isCoverCustomized,
          language = language,
          format = format,
          translator = translator,
          editionLabel = editionLabel,
          seriesName = seriesName,
          seriesVolume = seriesVolume,
          synopsis = editionSynopsis,
          synopsisSource = editionSynopsisSource,
          categoriesJson = categoriesJson,
          externalIdsJson = externalIdsJson,
          isPrimary = true,
          createdAtEpochMillis = work.createdAtEpochMillis,
        )
      )
    val copyId =
      insertCopy(
        BookCopyEntity(
          editionId = editionId,
          label = initialCopyLabel,
          physicalFormat = initialCopyPhysicalFormat,
          mediaType = initialCopyMediaType,
          locationOrPlatform = initialCopyLocation,
          acquisitionEpochMillis = initialCopyAcquisitionMillis,
          condition = initialCopyCondition,
          acquisitionOrigin = initialCopyOrigin,
          pricePaidMinorUnits = initialCopyPriceMinorUnits,
          currencyCode = initialCopyCurrencyCode,
          customCopyCode = initialCopyCustomCode,
          personalNote = initialCopyPersonalNote,
          createdAtEpochMillis = work.createdAtEpochMillis,
        )
      )
    if (
      work.status == ReadingStatus.READING ||
        work.status == ReadingStatus.PAUSED ||
        work.status == ReadingStatus.FINISHED
    ) {
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

  @Query("SELECT * FROM editions WHERE workId = :workId ORDER BY isPrimary DESC, id ASC")
  suspend fun getEditionsForWork(workId: Long): List<EditionEntity>

  @Query("SELECT * FROM editions WHERE id = :editionId LIMIT 1")
  suspend fun getEditionById(editionId: Long): EditionEntity?

  @Query(
    """
    SELECT * FROM editions
    WHERE (:isbn13 IS NOT NULL AND isbn13 = :isbn13)
       OR (:isbn10 IS NOT NULL AND isbn10 = :isbn10)
       OR (:cleanedRaw IS NOT NULL AND REPLACE(REPLACE(UPPER(isbn), '-', ''), ' ', '') = :cleanedRaw)
    ORDER BY isPrimary DESC, id ASC
    """
  )
  suspend fun findEditionsByIsbn(
    isbn13: String?,
    isbn10: String?,
    cleanedRaw: String?,
  ): List<EditionEntity>

  @Query(
    """
    SELECT * FROM editions
    WHERE isbn13 IN (:isbns) OR isbn10 IN (:isbns)
    ORDER BY isPrimary DESC, id ASC
    """
  )
  suspend fun findEditionsByIsbnIn(isbns: List<String>): List<EditionEntity>

  @Query("SELECT coverUri FROM editions WHERE workId = :workId AND coverUri IS NOT NULL")
  suspend fun getCoverUrisForWork(workId: Long): List<String>

  @Query("SELECT COUNT(*) FROM editions WHERE workId = :workId")
  suspend fun countEditionsForWork(workId: Long): Int

  @Query("SELECT COUNT(*) FROM book_copies WHERE editionId = :editionId")
  suspend fun countCopiesForEdition(editionId: Long): Int

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

  @Query(
    """
    SELECT c.*
    FROM book_copies c
    INNER JOIN editions e ON c.editionId = e.id
    WHERE e.workId = :workId
    ORDER BY c.createdAtEpochMillis ASC, c.id ASC
    """
  )
  suspend fun getCopiesForWork(workId: Long): List<BookCopyEntity>

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
interface GenreDao {
  @Query("SELECT * FROM genres ORDER BY isCustom ASC, namePt ASC")
  fun observeAllGenres(): Flow<List<GenreEntity>>

  @Query("SELECT * FROM genres ORDER BY isCustom ASC, namePt ASC")
  suspend fun getAllGenres(): List<GenreEntity>

  @Query("SELECT * FROM genres WHERE id = :genreId LIMIT 1")
  suspend fun getGenreById(genreId: Long): GenreEntity?

  @Query("SELECT * FROM genres WHERE slug = :slug LIMIT 1")
  suspend fun getGenreBySlug(slug: String): GenreEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertGenre(genre: GenreEntity): Long
}

@Dao
interface ContributorDao {
  @Query(
    """
    SELECT cl.id AS linkId, cl.workId, cl.editionId, cl.contributorId, c.name, cl.role, cl.displayOrder
    FROM contribution_links cl
    INNER JOIN contributors c ON cl.contributorId = c.id
    WHERE cl.workId = :workId
    ORDER BY cl.displayOrder ASC, cl.id ASC
    """
  )
  fun observeContributorsForWork(workId: Long): Flow<List<ContributorLinkWithNameRow>>

  @Query(
    """
    SELECT cl.id AS linkId, cl.workId, cl.editionId, cl.contributorId, c.name, cl.role, cl.displayOrder
    FROM contribution_links cl
    INNER JOIN contributors c ON cl.contributorId = c.id
    WHERE cl.workId = :workId
    ORDER BY cl.displayOrder ASC, cl.id ASC
    """
  )
  suspend fun getContributorsForWork(workId: Long): List<ContributorLinkWithNameRow>

  @Query("SELECT * FROM contributors WHERE normalizedName = :normalizedName LIMIT 1")
  suspend fun findByNormalizedName(normalizedName: String): ContributorEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertContributor(contributor: ContributorEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertContributionLink(link: ContributionLinkEntity): Long

  @Query("DELETE FROM contribution_links WHERE workId = :workId AND editionId IS NULL")
  suspend fun deleteWorkLevelLinks(workId: Long)

  @Query("DELETE FROM contribution_links WHERE editionId = :editionId")
  suspend fun deleteEditionLevelLinks(editionId: Long)
}

@Dao
interface SearchCacheDao {
  @Query("SELECT * FROM search_cache_entries WHERE cacheKey = :cacheKey AND expiresAtEpochMillis >= :nowMillis LIMIT 1")
  suspend fun getValidEntry(cacheKey: String, nowMillis: Long): SearchCacheEntity?

  @Query("SELECT * FROM search_cache_entries WHERE cacheKey = :cacheKey LIMIT 1")
  suspend fun getAnyEntry(cacheKey: String): SearchCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertEntry(entry: SearchCacheEntity)

  @Query("UPDATE search_cache_entries SET lastAccessedEpochMillis = :accessedAt WHERE cacheKey = :cacheKey")
  suspend fun touchEntry(cacheKey: String, accessedAt: Long)

  @Query("DELETE FROM search_cache_entries WHERE cacheKey = :cacheKey")
  suspend fun deleteByKey(cacheKey: String)

  @Query("DELETE FROM search_cache_entries WHERE expiresAtEpochMillis < :nowMillis")
  suspend fun deleteExpired(nowMillis: Long)

  @Query(
    """
    DELETE FROM search_cache_entries
    WHERE cacheKey NOT IN (
      SELECT cacheKey FROM search_cache_entries
      ORDER BY lastAccessedEpochMillis DESC
      LIMIT :keepLimit
    )
    """
  )
  suspend fun trimBeyond(keepLimit: Int)
}

@Dao
interface MetadataSuggestionDao {
  @Query(
    """
    SELECT * FROM metadata_update_suggestions
    WHERE workId = :workId AND status = 'PENDING'
    ORDER BY confidenceScore DESC, createdAtEpochMillis DESC
    """
  )
  fun observePendingForWork(workId: Long): Flow<List<MetadataSuggestionEntity>>

  @Query("SELECT * FROM metadata_update_suggestions WHERE workId = :workId")
  suspend fun getAllForWork(workId: Long): List<MetadataSuggestionEntity>

  @Query("SELECT * FROM metadata_update_suggestions WHERE id = :suggestionId LIMIT 1")
  suspend fun getById(suggestionId: Long): MetadataSuggestionEntity?

  @Query("SELECT COUNT(*) FROM metadata_update_suggestions WHERE status = 'PENDING'")
  fun observeTotalPendingCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertSuggestions(entries: List<MetadataSuggestionEntity>)

  @Query("UPDATE metadata_update_suggestions SET status = :status WHERE id = :suggestionId")
  suspend fun updateStatus(suggestionId: Long, status: MetadataSuggestionStatus)

  @Query("DELETE FROM metadata_update_suggestions WHERE workId = :workId AND status = 'PENDING'")
  suspend fun clearPendingForWork(workId: Long)
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

  @Query("DELETE FROM reading_progress_logs WHERE id = :logId")
  suspend fun deleteProgressLogById(logId: Long)
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
