package com.example.core.data.repository

import androidx.room.withTransaction
import com.example.core.data.local.db.BookCopyEntity
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.CopyDao
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.db.LibraryBookSummaryRow
import com.example.core.data.local.db.ReadingJourneyDao
import com.example.core.data.local.db.ReadingProgressLogEntity
import com.example.core.data.local.db.ReadingSessionEntity
import com.example.core.data.local.db.ShelfDao
import com.example.core.data.local.db.WorkEntity
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.model.BookCopy
import com.example.core.model.BookDetailAggregate
import com.example.core.model.CatalogBook
import com.example.core.model.CopyMediaType
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.EditionWithBookCopies
import com.example.core.model.ReadingJourneySummary
import com.example.core.model.ReadingProgressLog
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingSession
import com.example.core.model.ReadingStatus
import com.example.core.model.Shelf
import com.example.core.model.Work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface LibraryRepository {
  fun observeAllBooks(): Flow<List<CatalogBook>>

  fun observeCurrentlyReading(): Flow<List<CatalogBook>>

  fun observeFavoriteBooks(): Flow<List<CatalogBook>>

  fun observeBook(workId: Long): Flow<CatalogBook?>

  fun observeBookDetail(workId: Long): Flow<BookDetailAggregate?>

  fun observeShelves(): Flow<List<Shelf>>

  suspend fun addBook(
    title: String,
    author: String,
    totalPages: Int?,
    publicationYear: Int?,
    editionNote: String?,
    status: ReadingStatus,
    isbn: String? = null,
    coverUri: String? = null,
    language: String? = null,
    format: EditionFormat? = null,
    translator: String? = null,
    copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    copyLabel: String? = null,
    copyLocation: String? = null,
  ): Long

  suspend fun updateWorkMetadata(workId: Long, title: String, author: String)

  suspend fun updateReadingStatus(workId: Long, status: ReadingStatus)

  suspend fun toggleFavorite(workId: Long, currentFavorite: Boolean)

  suspend fun deleteBook(workId: Long)

  suspend fun addEdition(
    workId: Long,
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
    isbn: String?,
    coverUri: String?,
    language: String?,
    format: EditionFormat?,
    translator: String?,
    setAsPrimary: Boolean = false,
    alsoCreateCopy: Boolean = true,
    copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    copyLabel: String? = null,
  ): Long

  suspend fun updateEdition(edition: Edition)

  suspend fun setPrimaryEdition(workId: Long, editionId: Long)

  suspend fun deleteEdition(workId: Long, editionId: Long): Boolean

  suspend fun addCopy(
    editionId: Long,
    mediaType: CopyMediaType,
    label: String?,
    physicalFormat: String?,
    locationOrPlatform: String?,
    personalNote: String?,
    setAsActiveReadingCopy: Boolean = false,
  ): Long

  suspend fun updateCopy(copy: BookCopy)

  suspend fun deleteCopy(copyId: Long)
}

class RoomLibraryRepository(
  private val bookDao: BookDao,
  private val shelfDao: ShelfDao,
  private val editionDao: EditionDao? = null,
  private val copyDao: CopyDao? = null,
  private val readingJourneyDao: ReadingJourneyDao? = null,
  private val database: DipeLibrisDatabase? = null,
  private val coverStorageManager: CoverStorageManager? = null,
) : LibraryRepository {

  private suspend fun <T> runInTransaction(block: suspend () -> T): T =
    if (database != null) database.withTransaction { block() } else block()

  override fun observeAllBooks(): Flow<List<CatalogBook>> =
    bookDao
      .observeAllCatalogBookSummaries()
      .distinctUntilChanged()
      .map { list -> list.map { it.toDomain() } }
      .flowOn(Dispatchers.Default)

  override fun observeCurrentlyReading(): Flow<List<CatalogBook>> =
    bookDao
      .observeBookSummariesByStatus(ReadingStatus.READING)
      .distinctUntilChanged()
      .map { list -> list.map { it.toDomain() } }
      .flowOn(Dispatchers.Default)

  override fun observeFavoriteBooks(): Flow<List<CatalogBook>> =
    bookDao
      .observeFavoriteBookSummaries()
      .distinctUntilChanged()
      .map { list -> list.map { it.toDomain() } }
      .flowOn(Dispatchers.Default)

  override fun observeBook(workId: Long): Flow<CatalogBook?> =
    bookDao
      .observeCatalogBookSummary(workId)
      .distinctUntilChanged()
      .map { it?.toDomain() }
      .flowOn(Dispatchers.Default)

  override fun observeBookDetail(workId: Long): Flow<BookDetailAggregate?> {
    val edDao = editionDao
    val cDao = copyDao
    val jDao = readingJourneyDao
    if (edDao == null || cDao == null || jDao == null) {
      return observeBook(workId).map { summary ->
        summary?.let {
          BookDetailAggregate(
            work = it.work,
            editions =
              listOfNotNull(
                it.primaryEdition?.let { ed ->
                  EditionWithBookCopies(
                    edition = ed,
                    copies = listOfNotNull(it.primaryCopy),
                  )
                }
              ),
            activeJourney = null,
            journeys = emptyList(),
          )
        }
      }
    }

    return combine(
        bookDao.observeWork(workId),
        edDao.observeEditionsForWork(workId),
        cDao.observeCopiesForWork(workId),
        jDao.observeSessionsForWork(workId),
        jDao.observeProgressLogsForWork(workId),
      ) { workEntity, editionEntities, copyEntities, sessionEntities, logEntities ->
        if (workEntity == null) return@combine null

        val copiesByEditionId = copyEntities.groupBy { it.editionId }
        val editionDomainList = editionEntities.map { it.toDomain() }
        val editionById = editionDomainList.associateBy { it.id }
        val copyDomainList = copyEntities.map { it.toDomain() }
        val copyById = copyDomainList.associateBy { it.id }
        val logsBySessionId = logEntities.groupBy { it.sessionId }

        val editionsWithCopies =
          editionDomainList.map { edition ->
            EditionWithBookCopies(
              edition = edition,
              copies = copiesByEditionId[edition.id].orEmpty().map { it.toDomain() },
            )
          }

        val journeys =
          sessionEntities.map { sessionEntity ->
            val copy = sessionEntity.copyId?.let { copyById[it] }
            val edition = copy?.let { editionById[it.editionId] }
            val logs =
              logsBySessionId[sessionEntity.id]
                .orEmpty()
                .map { it.toDomain() }
            ReadingJourneySummary(
              session = sessionEntity.toDomain(),
              copy = copy,
              edition = edition,
              logs = logs,
            )
          }

        val activeJourney = journeys.firstOrNull { it.session.isActive }

        BookDetailAggregate(
          work = workEntity.toDomain(),
          editions = editionsWithCopies,
          activeJourney = activeJourney,
          journeys = journeys,
        )
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  override fun observeShelves(): Flow<List<Shelf>> =
    shelfDao
      .observeAllShelves()
      .distinctUntilChanged()
      .map { list ->
        list.map { entity ->
          Shelf(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            type = entity.type,
            isFeatured = entity.isFeatured,
          )
        }
      }
      .flowOn(Dispatchers.Default)

  override suspend fun addBook(
    title: String,
    author: String,
    totalPages: Int?,
    publicationYear: Int?,
    editionNote: String?,
    status: ReadingStatus,
    isbn: String?,
    coverUri: String?,
    language: String?,
    format: EditionFormat?,
    translator: String?,
    copyMediaType: CopyMediaType,
    copyLabel: String?,
    copyLocation: String?,
  ): Long {
    val now = System.currentTimeMillis()
    return bookDao.insertBookAggregate(
      work =
        WorkEntity(
          title = title.trim(),
          author = author.trim(),
          status = status,
          createdAtEpochMillis = now,
          updatedAtEpochMillis = now,
        ),
      publisherOrNote = editionNote?.trim()?.takeIf { it.isNotEmpty() },
      publicationYear = publicationYear,
      totalPages = totalPages,
      isbn = ReadingProgressMath.normalizeIsbn(isbn),
      coverUri = coverUri?.takeIf { it.isNotBlank() },
      language = language?.trim()?.takeIf { it.isNotEmpty() },
      format = format,
      translator = translator?.trim()?.takeIf { it.isNotEmpty() },
      initialCopyMediaType = copyMediaType,
      initialCopyLabel = copyLabel?.trim()?.takeIf { it.isNotEmpty() },
      initialCopyLocation = copyLocation?.trim()?.takeIf { it.isNotEmpty() },
    )
  }

  override suspend fun updateWorkMetadata(workId: Long, title: String, author: String) {
    bookDao.updateWorkMetadata(
      workId = workId,
      title = title.trim(),
      author = author.trim(),
      updatedAt = System.currentTimeMillis(),
    )
  }

  override suspend fun updateReadingStatus(workId: Long, status: ReadingStatus) {
    val now = System.currentTimeMillis()
    val jDao = readingJourneyDao
    val cDao = copyDao
    val edDao = editionDao

    runInTransaction {
      if (jDao != null && cDao != null && edDao != null) {
        val activeSession = jDao.getActiveSessionForWork(workId)
        when (status) {
          ReadingStatus.READING -> {
            if (activeSession == null) {
              val defaultCopy = cDao.getDefaultCopyForWork(workId)
              val edition = defaultCopy?.let { edDao.getEditionById(it.editionId) }
              val maxReread = jDao.getMaxRereadNumber(workId)
              val calc =
                ReadingProgressMath.calculateFromPage(
                  page = defaultCopy?.currentPage ?: 0,
                  totalPages = edition?.totalPages,
                )
              jDao.insertSession(
                ReadingSessionEntity(
                  workId = workId,
                  copyId = defaultCopy?.id,
                  status = ReadingStatus.READING,
                  rereadNumber = maxReread + 1,
                  currentPage = calc.currentPage,
                  totalPagesSnapshot = edition?.totalPages,
                  progressPercent = calc.progressPercent,
                  startEpochMillis = now,
                )
              )
            } else if (activeSession.status == ReadingStatus.PAUSED) {
              val pauseDelta =
                activeSession.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
              jDao.updateSession(
                activeSession.copy(
                  status = ReadingStatus.READING,
                  pausedAtEpochMillis = null,
                  accumulatedPausedMillis = activeSession.accumulatedPausedMillis + pauseDelta,
                )
              )
            }
          }

          ReadingStatus.PAUSED -> {
            if (activeSession != null && activeSession.status == ReadingStatus.READING) {
              jDao.updateSession(
                activeSession.copy(
                  status = ReadingStatus.PAUSED,
                  pausedAtEpochMillis = now,
                )
              )
            } else if (activeSession == null) {
              val defaultCopy = cDao.getDefaultCopyForWork(workId)
              val edition = defaultCopy?.let { edDao.getEditionById(it.editionId) }
              val maxReread = jDao.getMaxRereadNumber(workId)
              jDao.insertSession(
                ReadingSessionEntity(
                  workId = workId,
                  copyId = defaultCopy?.id,
                  status = ReadingStatus.PAUSED,
                  rereadNumber = maxReread + 1,
                  currentPage = defaultCopy?.currentPage ?: 0,
                  totalPagesSnapshot = edition?.totalPages,
                  progressPercent = 0f,
                  startEpochMillis = now,
                  pausedAtEpochMillis = now,
                )
              )
            }
          }

          ReadingStatus.FINISHED -> {
            if (activeSession != null) {
              val copy = activeSession.copyId?.let { cDao.getCopyById(it) }
              val edition = copy?.let { edDao.getEditionById(it.editionId) }
              val totalPages =
                ReadingProgressMath.effectiveTotalPages(
                  edition?.totalPages,
                  activeSession.totalPagesSnapshot,
                )
              val pauseDelta =
                activeSession.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
              val finalPage = totalPages ?: activeSession.currentPage
              jDao.updateSession(
                activeSession.copy(
                  status = ReadingStatus.FINISHED,
                  currentPage = finalPage,
                  totalPagesSnapshot = totalPages,
                  progressPercent = 100f,
                  endEpochMillis = now,
                  pausedAtEpochMillis = null,
                  accumulatedPausedMillis = activeSession.accumulatedPausedMillis + pauseDelta,
                )
              )
              if (copy != null && totalPages != null) {
                cDao.updateCopyCurrentPage(copy.id, totalPages)
              }
            } else {
              val defaultCopy = cDao.getDefaultCopyForWork(workId)
              val edition = defaultCopy?.let { edDao.getEditionById(it.editionId) }
              val maxReread = jDao.getMaxRereadNumber(workId)
              jDao.insertSession(
                ReadingSessionEntity(
                  workId = workId,
                  copyId = defaultCopy?.id,
                  status = ReadingStatus.FINISHED,
                  rereadNumber = maxReread + 1,
                  currentPage = edition?.totalPages ?: 0,
                  totalPagesSnapshot = edition?.totalPages,
                  progressPercent = 100f,
                  startEpochMillis = now,
                  endEpochMillis = now,
                )
              )
            }
          }

          ReadingStatus.ABANDONED -> {
            if (activeSession != null) {
              val pauseDelta =
                activeSession.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
              jDao.updateSession(
                activeSession.copy(
                  status = ReadingStatus.ABANDONED,
                  endEpochMillis = now,
                  pausedAtEpochMillis = null,
                  accumulatedPausedMillis = activeSession.accumulatedPausedMillis + pauseDelta,
                )
              )
            }
          }

          ReadingStatus.UNREAD -> {
            if (activeSession != null) {
              val pauseDelta =
                activeSession.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
              jDao.updateSession(
                activeSession.copy(
                  status = ReadingStatus.ABANDONED,
                  endEpochMillis = now,
                  pausedAtEpochMillis = null,
                  accumulatedPausedMillis = activeSession.accumulatedPausedMillis + pauseDelta,
                )
              )
            }
          }
        }
      }
      bookDao.updateWorkStatus(workId, status, now)
    }
  }

  override suspend fun toggleFavorite(workId: Long, currentFavorite: Boolean) {
    bookDao.updateWorkFavorite(workId, !currentFavorite, System.currentTimeMillis())
  }

  override suspend fun deleteBook(workId: Long) {
    val covers = editionDao?.getCoverUrisForWork(workId).orEmpty()
    bookDao.deleteWorkById(workId)
    covers.forEach { coverStorageManager?.deleteCoverIfManaged(it) }
  }

  override suspend fun addEdition(
    workId: Long,
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
    isbn: String?,
    coverUri: String?,
    language: String?,
    format: EditionFormat?,
    translator: String?,
    setAsPrimary: Boolean,
    alsoCreateCopy: Boolean,
    copyMediaType: CopyMediaType,
    copyLabel: String?,
  ): Long {
    val edDao = editionDao ?: error("EditionDao required")
    val cDao = copyDao ?: error("CopyDao required")
    val now = System.currentTimeMillis()
    return runInTransaction {
      val existingCount = edDao.countEditionsForWork(workId)
      val shouldBePrimary = setAsPrimary || existingCount == 0
      val editionId =
        edDao.insertEdition(
          EditionEntity(
            workId = workId,
            publisherOrNote = publisherOrNote?.trim()?.takeIf { it.isNotEmpty() },
            publicationYear = publicationYear,
            totalPages = totalPages,
            isbn = ReadingProgressMath.normalizeIsbn(isbn),
            coverUri = coverUri?.takeIf { it.isNotBlank() },
            language = language?.trim()?.takeIf { it.isNotEmpty() },
            format = format,
            translator = translator?.trim()?.takeIf { it.isNotEmpty() },
            isPrimary = shouldBePrimary,
            createdAtEpochMillis = now,
          )
        )
      if (shouldBePrimary && existingCount > 0) {
        edDao.setPrimaryEdition(workId, editionId)
      }
      if (alsoCreateCopy) {
        cDao.insertCopy(
          BookCopyEntity(
            editionId = editionId,
            mediaType = copyMediaType,
            label = copyLabel?.trim()?.takeIf { it.isNotEmpty() },
            createdAtEpochMillis = now,
          )
        )
      }
      editionId
    }
  }

  override suspend fun updateEdition(edition: Edition) {
    val edDao = editionDao ?: error("EditionDao required")
    val previousCover = edDao.getEditionById(edition.id)?.coverUri
    runInTransaction {
      edDao.updateEdition(
        EditionEntity(
          id = edition.id,
          workId = edition.workId,
          publisherOrNote = edition.publisherOrNote?.trim()?.takeIf { it.isNotEmpty() },
          publicationYear = edition.publicationYear,
          totalPages = edition.totalPages,
          isbn = ReadingProgressMath.normalizeIsbn(edition.isbn),
          coverUri = edition.coverUri?.takeIf { it.isNotBlank() },
          language = edition.language?.trim()?.takeIf { it.isNotEmpty() },
          format = edition.format,
          translator = edition.translator?.trim()?.takeIf { it.isNotEmpty() },
          isPrimary = edition.isPrimary,
          createdAtEpochMillis = edition.createdAtEpochMillis,
        )
      )
      if (edition.isPrimary) {
        edDao.setPrimaryEdition(edition.workId, edition.id)
      }
    }
    if (previousCover != null && previousCover != edition.coverUri) {
      coverStorageManager?.deleteCoverIfManaged(previousCover)
    }
  }

  override suspend fun setPrimaryEdition(workId: Long, editionId: Long) {
    val edDao = editionDao ?: error("EditionDao required")
    edDao.setPrimaryEdition(workId, editionId)
  }

  override suspend fun deleteEdition(workId: Long, editionId: Long): Boolean {
    val edDao = editionDao ?: error("EditionDao required")
    val existing = edDao.getEditionById(editionId) ?: return false
    var deleted = false
    runInTransaction {
      val count = edDao.countEditionsForWork(workId)
      if (count <= 1) {
        deleted = false
        return@runInTransaction
      }
      edDao.deleteEditionById(editionId)
      deleted = true
    }
    if (deleted) {
      coverStorageManager?.deleteCoverIfManaged(existing.coverUri)
    }
    return deleted
  }

  override suspend fun addCopy(
    editionId: Long,
    mediaType: CopyMediaType,
    label: String?,
    physicalFormat: String?,
    locationOrPlatform: String?,
    personalNote: String?,
    setAsActiveReadingCopy: Boolean,
  ): Long {
    val cDao = copyDao ?: error("CopyDao required")
    val edDao = editionDao ?: error("EditionDao required")
    val jDao = readingJourneyDao
    val now = System.currentTimeMillis()
    return runInTransaction {
      val copyId =
        cDao.insertCopy(
          BookCopyEntity(
            editionId = editionId,
            mediaType = mediaType,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            physicalFormat = physicalFormat?.trim()?.takeIf { it.isNotEmpty() },
            locationOrPlatform = locationOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
            personalNote = personalNote?.trim()?.takeIf { it.isNotEmpty() },
            createdAtEpochMillis = now,
          )
        )
      if (setAsActiveReadingCopy && jDao != null) {
        val edition = edDao.getEditionById(editionId)
        if (edition != null) {
          val activeSession = jDao.getActiveSessionForWork(edition.workId)
          if (activeSession != null) {
            val totalPages =
              ReadingProgressMath.effectiveTotalPages(
                edition.totalPages,
                activeSession.totalPagesSnapshot,
              )
            val calc =
              ReadingProgressMath.calculateFromPage(
                page = activeSession.currentPage,
                totalPages = totalPages,
                fallbackPercent = activeSession.progressPercent,
              )
            jDao.updateSession(
              activeSession.copy(
                copyId = copyId,
                totalPagesSnapshot = totalPages,
                currentPage = calc.currentPage,
                progressPercent = calc.progressPercent,
              )
            )
          }
        }
      }
      copyId
    }
  }

  override suspend fun updateCopy(copy: BookCopy) {
    val cDao = copyDao ?: error("CopyDao required")
    cDao.updateCopy(
      BookCopyEntity(
        id = copy.id,
        editionId = copy.editionId,
        currentPage = copy.currentPage,
        physicalFormat = copy.physicalFormat?.trim()?.takeIf { it.isNotEmpty() },
        personalNote = copy.personalNote?.trim()?.takeIf { it.isNotEmpty() },
        label = copy.label?.trim()?.takeIf { it.isNotEmpty() },
        mediaType = copy.mediaType,
        locationOrPlatform = copy.locationOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
        acquisitionEpochMillis = copy.acquisitionEpochMillis,
        createdAtEpochMillis = copy.createdAtEpochMillis,
      )
    )
  }

  override suspend fun deleteCopy(copyId: Long) {
    val cDao = copyDao ?: error("CopyDao required")
    cDao.deleteCopyById(copyId)
  }

  private fun LibraryBookSummaryRow.toDomain(): CatalogBook =
    CatalogBook(
      work = work.toDomain(),
      primaryEdition = primaryEdition?.toDomain(),
      primaryCopy = primaryCopy?.toDomain(),
      editionCount = editionCount.coerceAtLeast(if (primaryEdition != null) 1 else 0),
      copyCount = copyCount,
      activeSessionId = activeSessionId,
      activeProgressPercent = activeProgressPercent,
      activeCurrentPage = activeCurrentPage,
      activeTotalPages = activeTotalPages,
      activeRereadNumber = activeRereadNumber,
      activeCopyMediaType = activeCopyMediaType,
      activeCopyLabel = activeCopyLabel,
    )

  private fun WorkEntity.toDomain(): Work =
    Work(
      id = id,
      title = title,
      author = author,
      status = status,
      isFavorite = isFavorite,
      typographyPresetOverride = typographyPresetOverride,
      createdAtEpochMillis = createdAtEpochMillis,
      updatedAtEpochMillis = updatedAtEpochMillis,
    )

  private fun EditionEntity.toDomain(): Edition =
    Edition(
      id = id,
      workId = workId,
      publisherOrNote = publisherOrNote,
      publicationYear = publicationYear,
      totalPages = totalPages,
      isbn = isbn,
      coverUri = coverUri,
      language = language,
      format = format,
      translator = translator,
      isPrimary = isPrimary,
      createdAtEpochMillis = createdAtEpochMillis,
    )

  private fun BookCopyEntity.toDomain(): BookCopy =
    BookCopy(
      id = id,
      editionId = editionId,
      currentPage = currentPage,
      physicalFormat = physicalFormat,
      personalNote = personalNote,
      label = label,
      mediaType = mediaType,
      locationOrPlatform = locationOrPlatform,
      acquisitionEpochMillis = acquisitionEpochMillis,
      createdAtEpochMillis = createdAtEpochMillis,
    )

  private fun ReadingSessionEntity.toDomain(): ReadingSession =
    ReadingSession(
      id = id,
      workId = workId,
      copyId = copyId,
      rereadNumber = rereadNumber,
      status = status,
      currentPage = currentPage,
      totalPagesSnapshot = totalPagesSnapshot,
      progressPercent = progressPercent,
      startEpochMillis = startEpochMillis,
      endEpochMillis = endEpochMillis,
      pausedAtEpochMillis = pausedAtEpochMillis,
      accumulatedPausedMillis = accumulatedPausedMillis,
      notes = notes,
    )

  private fun ReadingProgressLogEntity.toDomain(): ReadingProgressLog =
    ReadingProgressLog(
      id = id,
      sessionId = sessionId,
      recordedAtEpochMillis = recordedAtEpochMillis,
      pageNumber = pageNumber,
      progressPercent = progressPercent,
      deltaPages = deltaPages,
      note = note,
    )
}
