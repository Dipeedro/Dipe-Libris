package com.example.core.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.core.bibliographic.domain.DuplicateDetector
import com.example.core.bibliographic.domain.GenreSuggester
import com.example.core.bibliographic.domain.WorkEditMask
import com.example.core.data.local.db.BookCopyEntity
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.ContributionLinkEntity
import com.example.core.data.local.db.ContributorDao
import com.example.core.data.local.db.ContributorEntity
import com.example.core.data.local.db.ContributorLinkWithNameRow
import com.example.core.data.local.db.CopyDao
import com.example.core.data.local.db.DefaultGenres
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.db.GenreDao
import com.example.core.data.local.db.GenreEntity
import com.example.core.data.local.db.LibraryBookSummaryRow
import com.example.core.data.local.db.LibraryFtsEntity
import com.example.core.data.local.db.MetadataSuggestionDao
import com.example.core.data.local.db.MetadataSuggestionEntity
import com.example.core.data.local.db.ReadingJourneyDao
import com.example.core.data.local.db.ReadingProgressLogEntity
import com.example.core.data.local.db.ReadingSessionEntity
import com.example.core.data.local.db.ShelfDao
import com.example.core.data.local.db.WorkEntity
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.model.BibliographicMath
import com.example.core.model.BookCopy
import com.example.core.model.BookDetailAggregate
import com.example.core.model.BookReviewSubmission
import com.example.core.model.CandidateContributor
import com.example.core.model.CatalogBook
import com.example.core.model.ContributorRole
import com.example.core.model.CopyMediaType
import com.example.core.model.DuplicateAssessment
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.EditionWithBookCopies
import com.example.core.model.Genre
import com.example.core.model.MetadataSuggestion
import com.example.core.model.ReadingJourneySummary
import com.example.core.model.ReadingProgressLog
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingSession
import com.example.core.model.ReadingStatus
import com.example.core.model.Shelf
import com.example.core.model.Work
import com.example.core.model.WorkContributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LibraryRepository {
  fun observeAllBooks(): Flow<List<CatalogBook>>

  fun observeCurrentlyReading(): Flow<List<CatalogBook>>

  fun observeFavoriteBooks(): Flow<List<CatalogBook>>

  fun observeBook(workId: Long): Flow<CatalogBook?>

  fun observeBookDetail(workId: Long): Flow<BookDetailAggregate?>

  fun observeShelves(): Flow<List<Shelf>>

  fun observeGenres(): Flow<List<Genre>> = flowOf(emptyList())

  suspend fun createCustomGenre(name: String): Genre =
    throw UnsupportedOperationException("GenreDao not configured")

  suspend fun searchLocalLibrary(query: String): List<CatalogBook> = emptyList()

  suspend fun assessDuplicates(
    isbn13: String?,
    isbn10: String?,
    externalWorkKey: String?,
    title: String,
    author: String,
  ): DuplicateAssessment = DuplicateAssessment.NoMatch

  suspend fun saveBookFromReview(submission: BookReviewSubmission): Long =
    throw UnsupportedOperationException("Full review save not configured")

  suspend fun addEditionFromReview(workId: Long, submission: BookReviewSubmission): Long =
    throw UnsupportedOperationException("Full edition save not configured")

  suspend fun addCopyFromReview(editionId: Long, submission: BookReviewSubmission): Long =
    throw UnsupportedOperationException("Full copy save not configured")

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

  suspend fun updateWorkDetails(
    work: Work,
    contributors: List<WorkContributor> = emptyList(),
    editedMaskDelta: Long = 0L,
  ) {
    updateWorkMetadata(work.id, work.title, work.author)
  }

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

  suspend fun syncWorkFtsIndex(workId: Long) {}
}

class RoomLibraryRepository(
  private val bookDao: BookDao,
  private val shelfDao: ShelfDao,
  private val editionDao: EditionDao? = null,
  private val copyDao: CopyDao? = null,
  private val readingJourneyDao: ReadingJourneyDao? = null,
  private val database: DipeLibrisDatabase? = null,
  private val coverStorageManager: CoverStorageManager? = null,
  private val genreDao: GenreDao? = null,
  private val contributorDao: ContributorDao? = null,
  private val metadataSuggestionDao: MetadataSuggestionDao? = null,
) : LibraryRepository {

  private val json = com.example.core.bibliographic.network.BibliographicHttpClients.json

  private suspend fun <T> runInTransaction(block: suspend () -> T): T =
    if (database != null) database.withTransaction { block() } else block()

  private suspend fun ensureDefaultGenresSeeded() {
    val gDao = genreDao ?: return
    if (gDao.getAllGenres().isEmpty()) {
      val now = System.currentTimeMillis()
      DefaultGenres.seeds.forEach { seed ->
        gDao.insertGenre(
          GenreEntity(
            slug = seed.slug,
            namePt = seed.namePt,
            nameEn = seed.nameEn,
            isCustom = false,
            createdAtEpochMillis = now,
          )
        )
      }
    }
  }

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

    val contributorsFlow: Flow<List<ContributorLinkWithNameRow>> =
      contributorDao?.observeContributorsForWork(workId) ?: flowOf(emptyList())
    val genresFlow: Flow<List<GenreEntity>> =
      genreDao?.observeAllGenres() ?: flowOf(emptyList())
    val suggestionsFlow: Flow<List<MetadataSuggestionEntity>> =
      metadataSuggestionDao?.observePendingForWork(workId) ?: flowOf(emptyList())

    val coreFlow =
      combine(
        bookDao.observeWork(workId),
        edDao.observeEditionsForWork(workId),
        cDao.observeCopiesForWork(workId),
        jDao.observeSessionsForWork(workId),
        jDao.observeProgressLogsForWork(workId),
      ) { workEntity, editionEntities, copyEntities, sessionEntities, logEntities ->
        CoreDetailTuple(workEntity, editionEntities, copyEntities, sessionEntities, logEntities)
      }

    return combine(
        coreFlow,
        contributorsFlow,
        genresFlow,
        suggestionsFlow,
      ) { core, contributorRows, genreEntities, suggestionRows ->
        val workEntity = core.workEntity ?: return@combine null
        val genre =
          workEntity.primaryGenreId?.let { gid ->
            genreEntities.firstOrNull { it.id == gid }?.toDomain()
          }

        val copiesByEditionId = core.copyEntities.groupBy { it.editionId }
        val editionDomainList = core.editionEntities.map { it.toDomain() }
        val editionById = editionDomainList.associateBy { it.id }
        val copyDomainList = core.copyEntities.map { it.toDomain() }
        val copyById = copyDomainList.associateBy { it.id }
        val logsBySessionId = core.logEntities.groupBy { it.sessionId }

        val workLevelContributors =
          contributorRows
            .filter { it.editionId == null }
            .map { it.toDomain() }
        val editionContribMap =
          contributorRows
            .filter { it.editionId != null }
            .groupBy { it.editionId!! }

        val editionsWithCopies =
          editionDomainList.map { edition ->
            EditionWithBookCopies(
              edition = edition,
              copies = copiesByEditionId[edition.id].orEmpty().map { it.toDomain() },
              contributors = editionContribMap[edition.id].orEmpty().map { it.toDomain() },
            )
          }

        val journeys =
          core.sessionEntities.map { sessionEntity ->
            val copy = sessionEntity.copyId?.let { copyById[it] }
            val edition = copy?.let { editionById[it.editionId] }
            val logs = logsBySessionId[sessionEntity.id].orEmpty().map { it.toDomain() }
            ReadingJourneySummary(
              session = sessionEntity.toDomain(),
              copy = copy,
              edition = edition,
              logs = logs,
            )
          }

        val activeJourney = journeys.firstOrNull { it.session.isActive }

        BookDetailAggregate(
          work = workEntity.toDomain(primaryGenre = genre),
          editions = editionsWithCopies,
          activeJourney = activeJourney,
          journeys = journeys,
          contributors = contributorRows.map { it.toDomain() },
          pendingSuggestions = suggestionRows.map { it.toDomain() },
        )
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  private data class CoreDetailTuple(
    val workEntity: WorkEntity?,
    val editionEntities: List<EditionEntity>,
    val copyEntities: List<BookCopyEntity>,
    val sessionEntities: List<ReadingSessionEntity>,
    val logEntities: List<ReadingProgressLogEntity>,
  )

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

  override fun observeGenres(): Flow<List<Genre>> {
    val gDao = genreDao ?: return flowOf(emptyList())
    return gDao
      .observeAllGenres()
      .onStart { ensureDefaultGenresSeeded() }
      .distinctUntilChanged()
      .map { list -> list.map { it.toDomain() } }
      .flowOn(Dispatchers.Default)
  }

  override suspend fun createCustomGenre(name: String): Genre {
    val gDao = genreDao ?: error("GenreDao required")
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "Genre name cannot be blank" }
    ensureDefaultGenresSeeded()
    val slugBase =
      BibliographicMath.normalizeText(trimmed)
        .replace(' ', '-')
        .ifEmpty { "custom-${System.currentTimeMillis()}" }
    val existing = gDao.getGenreBySlug(slugBase)
    if (existing != null) return existing.toDomain()

    val slug = "custom-$slugBase"
    gDao.getGenreBySlug(slug)?.let { return it.toDomain() }

    val entity =
      GenreEntity(
        slug = slug,
        namePt = trimmed,
        nameEn = trimmed,
        isCustom = true,
        createdAtEpochMillis = System.currentTimeMillis(),
      )
    val id = gDao.insertGenre(entity)
    return if (id > 0L) entity.copy(id = id).toDomain()
    else gDao.getGenreBySlug(slug)?.toDomain() ?: entity.toDomain()
  }

  override suspend fun searchLocalLibrary(query: String): List<CatalogBook> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val ftsWorkIds =
      runCatching {
        val tokens =
          BibliographicMath.normalizeText(trimmed)
            .split(' ')
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) emptyList()
        else {
          val matchQuery = tokens.joinToString(" ") { "$it*" }
          bookDao.searchWorkIdsFts(matchQuery)
        }
      }.getOrDefault(emptyList())

    val allSummaries = bookDao.getAllCatalogBookSummariesSnapshot().map { it.toDomain() }
    val normalizedQuery = BibliographicMath.normalizeText(trimmed)
    val cleanedIsbnQuery = BibliographicMath.cleanIsbn(trimmed)

    val ftsSet = ftsWorkIds.toSet()
    val ftsResults = allSummaries.filter { it.work.id in ftsSet }

    val isbnResults =
      if (cleanedIsbnQuery.length >= 6 && editionDao != null) {
        val parsed = BibliographicMath.parseIsbn(cleanedIsbnQuery)
        val edMatches = editionDao.findEditionsByIsbn(parsed?.isbn13, parsed?.isbn10, cleanedIsbnQuery)
        val isbnWorkIds = edMatches.map { it.workId }.toSet()
        allSummaries.filter { it.work.id in isbnWorkIds }
      } else {
        emptyList()
      }

    val fallbackResults =
      if (normalizedQuery.isNotEmpty()) {
        allSummaries.filter { book ->
          val haystack =
            BibliographicMath.normalizeText(
              listOfNotNull(
                book.title,
                book.work.subtitle,
                book.author,
                book.primaryEdition?.publisherOrNote,
                book.primaryEdition?.translator,
                book.primaryEdition?.seriesName,
                book.primaryEdition?.isbn13,
                book.primaryEdition?.isbn10,
                book.primaryEdition?.isbn,
                book.primaryGenre?.namePt,
                book.primaryGenre?.nameEn,
              ).joinToString(" ")
            )
          normalizedQuery in haystack
        }
      } else {
        emptyList()
      }

    return (isbnResults + ftsResults + fallbackResults).distinctBy { it.work.id }
  }

  override suspend fun assessDuplicates(
    isbn13: String?,
    isbn10: String?,
    externalWorkKey: String?,
    title: String,
    author: String,
  ): DuplicateAssessment {
    val parsed = BibliographicMath.parseIsbn(isbn13 ?: isbn10)
    val effective13 = parsed?.isbn13 ?: isbn13
    val effective10 = parsed?.isbn10 ?: isbn10
    val edDao = editionDao
    val matchedEditions =
      if (edDao != null && (effective13 != null || effective10 != null)) {
        edDao.findEditionsByIsbn(effective13, effective10, effective13 ?: effective10)
      } else {
        emptyList()
      }
    val copyCounts =
      if (edDao != null && matchedEditions.isNotEmpty()) {
        matchedEditions.associate { it.id to edDao.countCopiesForEdition(it.id) }
      } else {
        emptyMap()
      }
    val localBooks = bookDao.getAllCatalogBookSummariesSnapshot().map { it.toDomain() }
    return DuplicateDetector.assessRaw(
      isbn13 = effective13,
      isbn10 = effective10,
      externalWorkKey = externalWorkKey,
      title = title,
      author = author,
      localCatalog = localBooks,
      matchedIsbnEditions = matchedEditions,
      copyCountsByEditionId = copyCounts,
    )
  }

  override suspend fun saveBookFromReview(submission: BookReviewSubmission): Long {
    ensureDefaultGenresSeeded()
    val resolvedCoverUri = resolveCoverUri(submission.selectedLocalCoverUri, submission.selectedRemoteCoverUrl)
    val parsedIsbn = BibliographicMath.parseIsbn(submission.isbn13 ?: submission.isbn10)
    val isbn13 = parsedIsbn?.isbn13 ?: submission.isbn13?.trim()?.takeIf { it.isNotEmpty() }
    val isbn10 = parsedIsbn?.isbn10 ?: submission.isbn10?.trim()?.takeIf { it.isNotEmpty() }
    val primaryAuthorSummary = formatPrimaryAuthors(submission.contributors)
    val translatorSummary = formatTranslators(submission.contributors)
    val resolvedGenreId =
      submission.primaryGenreId
        ?: genreDao?.let { dao ->
          val allGenres = dao.getAllGenres().map { it.toDomain() }
          GenreSuggester.matchGenre(submission.categories, allGenres, submission.title)?.id
        }
    val now = System.currentTimeMillis()
    val categoriesJson =
      submission.categories.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    val workId =
      runInTransaction {
        val insertedWorkId =
          bookDao.insertBookAggregate(
            work =
              WorkEntity(
                title = submission.title.trim(),
                author = primaryAuthorSummary,
                subtitle = submission.subtitle?.trim()?.takeIf { it.isNotEmpty() },
                originalTitle = submission.originalTitle?.trim()?.takeIf { it.isNotEmpty() },
                primaryGenreId = resolvedGenreId,
                activeSynopsis = submission.activeSynopsis?.trim()?.takeIf { it.isNotEmpty() },
                synopsisLanguage = submission.synopsisLanguage,
                synopsisSource = submission.synopsisSource,
                isSynopsisCustomized = submission.isSynopsisCustomized,
                externalWorkKey = submission.externalWorkKey,
                status = submission.status,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
              ),
            publisherOrNote = submission.publisherOrNote?.trim()?.takeIf { it.isNotEmpty() },
            publicationYear = submission.publicationYear,
            totalPages = submission.totalPages,
            isbn = isbn13 ?: isbn10,
            isbn10 = isbn10,
            isbn13 = isbn13,
            coverUri = resolvedCoverUri,
            coverSource = submission.coverSource,
            isCoverCustomized = submission.isCoverCustomized,
            language = BibliographicMath.normalizeLanguageCode(submission.language) ?: submission.language,
            format = submission.format,
            translator = translatorSummary,
            editionLabel = submission.editionLabel?.trim()?.takeIf { it.isNotEmpty() },
            seriesName = submission.seriesName?.trim()?.takeIf { it.isNotEmpty() },
            seriesVolume = submission.seriesVolume?.trim()?.takeIf { it.isNotEmpty() },
            editionSynopsis = submission.activeSynopsis?.trim()?.takeIf { it.isNotEmpty() },
            editionSynopsisSource = submission.synopsisSource,
            categoriesJson = categoriesJson,
            externalIdsJson = submission.externalIdsJson,
            initialCopyMediaType = submission.copyMediaType,
            initialCopyLabel = submission.copyLabel?.trim()?.takeIf { it.isNotEmpty() },
            initialCopyPhysicalFormat = submission.copyPhysicalFormat?.trim()?.takeIf { it.isNotEmpty() },
            initialCopyLocation = submission.copyLocationOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
            initialCopyCondition = submission.copyCondition,
            initialCopyOrigin = submission.copyOrigin,
            initialCopyAcquisitionMillis = submission.copyAcquisitionEpochMillis,
            initialCopyPriceMinorUnits = submission.copyPriceMinorUnits,
            initialCopyCurrencyCode = submission.copyCurrencyCode?.trim()?.takeIf { it.isNotEmpty() },
            initialCopyCustomCode = submission.copyCustomCode?.trim()?.takeIf { it.isNotEmpty() },
            initialCopyPersonalNote = submission.copyPersonalNote?.trim()?.takeIf { it.isNotEmpty() },
          )

        val primaryEditionId =
          editionDao?.getEditionsForWork(insertedWorkId)?.firstOrNull()?.id
        persistContributors(insertedWorkId, primaryEditionId, submission.contributors)
        syncWorkFtsIndexInternal(insertedWorkId)
        insertedWorkId
      }
    return workId
  }

  override suspend fun addEditionFromReview(
    workId: Long,
    submission: BookReviewSubmission,
  ): Long {
    val edDao = editionDao ?: error("EditionDao required")
    val cDao = copyDao ?: error("CopyDao required")
    val resolvedCoverUri = resolveCoverUri(submission.selectedLocalCoverUri, submission.selectedRemoteCoverUrl)
    val parsedIsbn = BibliographicMath.parseIsbn(submission.isbn13 ?: submission.isbn10)
    val isbn13 = parsedIsbn?.isbn13 ?: submission.isbn13?.trim()?.takeIf { it.isNotEmpty() }
    val isbn10 = parsedIsbn?.isbn10 ?: submission.isbn10?.trim()?.takeIf { it.isNotEmpty() }
    val translatorSummary = formatTranslators(submission.contributors)
    val now = System.currentTimeMillis()
    val categoriesJson =
      submission.categories.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    return runInTransaction {
      val existingCount = edDao.countEditionsForWork(workId)
      val editionId =
        edDao.insertEdition(
          EditionEntity(
            workId = workId,
            publisherOrNote = submission.publisherOrNote?.trim()?.takeIf { it.isNotEmpty() },
            publicationYear = submission.publicationYear,
            totalPages = submission.totalPages,
            isbn = isbn13 ?: isbn10,
            isbn10 = isbn10,
            isbn13 = isbn13,
            coverUri = resolvedCoverUri,
            coverSource = submission.coverSource,
            isCoverCustomized = submission.isCoverCustomized,
            language = BibliographicMath.normalizeLanguageCode(submission.language) ?: submission.language,
            format = submission.format,
            translator = translatorSummary,
            editionLabel = submission.editionLabel?.trim()?.takeIf { it.isNotEmpty() },
            seriesName = submission.seriesName?.trim()?.takeIf { it.isNotEmpty() },
            seriesVolume = submission.seriesVolume?.trim()?.takeIf { it.isNotEmpty() },
            synopsis = submission.activeSynopsis?.trim()?.takeIf { it.isNotEmpty() },
            synopsisSource = submission.synopsisSource,
            categoriesJson = categoriesJson,
            externalIdsJson = submission.externalIdsJson,
            isPrimary = existingCount == 0,
            createdAtEpochMillis = now,
          )
        )
      cDao.insertCopy(
        BookCopyEntity(
          editionId = editionId,
          mediaType = submission.copyMediaType,
          label = submission.copyLabel?.trim()?.takeIf { it.isNotEmpty() },
          physicalFormat = submission.copyPhysicalFormat?.trim()?.takeIf { it.isNotEmpty() },
          locationOrPlatform = submission.copyLocationOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
          acquisitionEpochMillis = submission.copyAcquisitionEpochMillis,
          condition = submission.copyCondition,
          acquisitionOrigin = submission.copyOrigin,
          pricePaidMinorUnits = submission.copyPriceMinorUnits,
          currencyCode = submission.copyCurrencyCode?.trim()?.takeIf { it.isNotEmpty() },
          customCopyCode = submission.copyCustomCode?.trim()?.takeIf { it.isNotEmpty() },
          personalNote = submission.copyPersonalNote?.trim()?.takeIf { it.isNotEmpty() },
          createdAtEpochMillis = now,
        )
      )
      persistEditionContributors(
        workId = workId,
        editionId = editionId,
        contributors = submission.contributors.filter { it.role.isEditionSpecificByDefault },
      )
      syncWorkFtsIndexInternal(workId)
      editionId
    }
  }

  override suspend fun addCopyFromReview(
    editionId: Long,
    submission: BookReviewSubmission,
  ): Long {
    val cDao = copyDao ?: error("CopyDao required")
    val edDao = editionDao ?: error("EditionDao required")
    val now = System.currentTimeMillis()
    return runInTransaction {
      val copyId =
        cDao.insertCopy(
          BookCopyEntity(
            editionId = editionId,
            mediaType = submission.copyMediaType,
            label = submission.copyLabel?.trim()?.takeIf { it.isNotEmpty() },
            physicalFormat = submission.copyPhysicalFormat?.trim()?.takeIf { it.isNotEmpty() },
            locationOrPlatform = submission.copyLocationOrPlatform?.trim()?.takeIf { it.isNotEmpty() },
            acquisitionEpochMillis = submission.copyAcquisitionEpochMillis,
            condition = submission.copyCondition,
            acquisitionOrigin = submission.copyOrigin,
            pricePaidMinorUnits = submission.copyPriceMinorUnits,
            currencyCode = submission.copyCurrencyCode?.trim()?.takeIf { it.isNotEmpty() },
            customCopyCode = submission.copyCustomCode?.trim()?.takeIf { it.isNotEmpty() },
            personalNote = submission.copyPersonalNote?.trim()?.takeIf { it.isNotEmpty() },
            createdAtEpochMillis = now,
          )
        )
      edDao.getEditionById(editionId)?.let { ed -> syncWorkFtsIndexInternal(ed.workId) }
      copyId
    }
  }

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
    ensureDefaultGenresSeeded()
    val now = System.currentTimeMillis()
    val parsedIsbn = BibliographicMath.parseIsbn(isbn)
    val normalizedLegacyIsbn = ReadingProgressMath.normalizeIsbn(isbn)
    return runInTransaction {
      val workId =
        bookDao.insertBookAggregate(
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
          isbn = normalizedLegacyIsbn,
          isbn10 = parsedIsbn?.isbn10,
          isbn13 = parsedIsbn?.isbn13,
          coverUri = coverUri?.takeIf { it.isNotBlank() },
          language = language?.trim()?.takeIf { it.isNotEmpty() },
          format = format,
          translator = translator?.trim()?.takeIf { it.isNotEmpty() },
          initialCopyMediaType = copyMediaType,
          initialCopyLabel = copyLabel?.trim()?.takeIf { it.isNotEmpty() },
          initialCopyLocation = copyLocation?.trim()?.takeIf { it.isNotEmpty() },
        )

      val contributors =
        buildList {
          author.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, name ->
            add(
              CandidateContributor(
                name = name,
                role = if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR,
              )
            )
          }
          translator?.split(",", ";")?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            add(CandidateContributor(name = name, role = ContributorRole.TRANSLATOR))
          }
        }
      val primaryEditionId = editionDao?.getEditionsForWork(workId)?.firstOrNull()?.id
      persistContributors(workId, primaryEditionId, contributors)
      syncWorkFtsIndexInternal(workId)
      workId
    }
  }

  override suspend fun updateWorkMetadata(workId: Long, title: String, author: String) {
    runInTransaction {
      val existing = bookDao.getWorkById(workId)
      if (existing != null) {
        val now = System.currentTimeMillis()
        bookDao.updateWork(
          existing.copy(
            title = title.trim(),
            author = author.trim(),
            userEditedMask = existing.userEditedMask or WorkEditMask.TITLE or WorkEditMask.AUTHOR,
            updatedAtEpochMillis = now,
          )
        )
        val authors =
          author.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { idx, n ->
            CandidateContributor(n, if (idx == 0) ContributorRole.AUTHOR else ContributorRole.CO_AUTHOR)
          }
        contributorDao?.deleteWorkLevelLinks(workId)
        persistWorkContributorsOnly(workId, authors)
      } else {
        bookDao.updateWorkMetadata(
          workId = workId,
          title = title.trim(),
          author = author.trim(),
          updatedAt = System.currentTimeMillis(),
        )
      }
      syncWorkFtsIndexInternal(workId)
    }
  }

  override suspend fun updateWorkDetails(
    work: Work,
    contributors: List<WorkContributor>,
    editedMaskDelta: Long,
  ) {
    runInTransaction {
      val existing = bookDao.getWorkById(work.id) ?: return@runInTransaction
      val now = System.currentTimeMillis()
      val updatedAuthorString =
        if (contributors.isNotEmpty()) {
          contributors
            .filter { it.role.isPrimaryWorkRole }
            .joinToString(", ") { it.name }
            .ifBlank { work.author.trim() }
        } else {
          work.author.trim()
        }
      bookDao.updateWork(
        existing.copy(
          title = work.title.trim(),
          author = updatedAuthorString,
          subtitle = work.subtitle?.trim()?.takeIf { it.isNotEmpty() },
          originalTitle = work.originalTitle?.trim()?.takeIf { it.isNotEmpty() },
          primaryGenreId = work.primaryGenreId,
          activeSynopsis = work.activeSynopsis?.trim()?.takeIf { it.isNotEmpty() },
          synopsisLanguage = work.synopsisLanguage,
          synopsisSource = work.synopsisSource,
          isSynopsisCustomized = work.isSynopsisCustomized,
          externalWorkKey = work.externalWorkKey,
          userEditedMask = existing.userEditedMask or work.userEditedMask or editedMaskDelta,
          updatedAtEpochMillis = now,
        )
      )
      if (contributors.isNotEmpty() && contributorDao != null) {
        contributorDao.deleteWorkLevelLinks(work.id)
        persistWorkContributorsOnly(
          workId = work.id,
          contributors =
            contributors
              .filter { it.editionId == null }
              .map { CandidateContributor(it.name, it.role) },
        )
      }
      syncWorkFtsIndexInternal(work.id)
    }
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

          ReadingStatus.ABANDONED, ReadingStatus.UNREAD -> {
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
    runInTransaction {
      bookDao.deleteFtsByWorkId(workId)
      bookDao.deleteWorkById(workId)
    }
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
    val parsedIsbn = BibliographicMath.parseIsbn(isbn)
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
            isbn10 = parsedIsbn?.isbn10,
            isbn13 = parsedIsbn?.isbn13,
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
      syncWorkFtsIndexInternal(workId)
      editionId
    }
  }

  override suspend fun updateEdition(edition: Edition) {
    val edDao = editionDao ?: error("EditionDao required")
    val previousCover = edDao.getEditionById(edition.id)?.coverUri
    val parsedIsbn = BibliographicMath.parseIsbn(edition.isbn13 ?: edition.isbn10 ?: edition.isbn)
    val categoriesJson =
      edition.categories.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
    runInTransaction {
      edDao.updateEdition(
        EditionEntity(
          id = edition.id,
          workId = edition.workId,
          publisherOrNote = edition.publisherOrNote?.trim()?.takeIf { it.isNotEmpty() },
          publicationYear = edition.publicationYear,
          totalPages = edition.totalPages,
          isbn = ReadingProgressMath.normalizeIsbn(edition.isbn ?: edition.isbn13 ?: edition.isbn10),
          isbn10 = parsedIsbn?.isbn10 ?: edition.isbn10,
          isbn13 = parsedIsbn?.isbn13 ?: edition.isbn13,
          coverUri = edition.coverUri?.takeIf { it.isNotBlank() },
          coverSource = edition.coverSource,
          isCoverCustomized = edition.isCoverCustomized,
          language = edition.language?.trim()?.takeIf { it.isNotEmpty() },
          format = edition.format,
          translator = edition.translator?.trim()?.takeIf { it.isNotEmpty() },
          editionLabel = edition.editionLabel?.trim()?.takeIf { it.isNotEmpty() },
          seriesName = edition.seriesName?.trim()?.takeIf { it.isNotEmpty() },
          seriesVolume = edition.seriesVolume?.trim()?.takeIf { it.isNotEmpty() },
          synopsis = edition.synopsis,
          synopsisSource = edition.synopsisSource,
          categoriesJson = categoriesJson,
          externalIdsJson = edition.externalIdsJson,
          isPrimary = edition.isPrimary,
          createdAtEpochMillis = edition.createdAtEpochMillis,
        )
      )
      if (edition.isPrimary) {
        edDao.setPrimaryEdition(edition.workId, edition.id)
      }
      syncWorkFtsIndexInternal(edition.workId)
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
      syncWorkFtsIndexInternal(workId)
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
      val edition = edDao.getEditionById(editionId)
      if (setAsActiveReadingCopy && jDao != null && edition != null) {
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
      edition?.let { syncWorkFtsIndexInternal(it.workId) }
      copyId
    }
  }

  override suspend fun updateCopy(copy: BookCopy) {
    val cDao = copyDao ?: error("CopyDao required")
    runInTransaction {
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
          condition = copy.condition,
          acquisitionOrigin = copy.acquisitionOrigin,
          pricePaidMinorUnits = copy.pricePaidMinorUnits,
          currencyCode = copy.currencyCode?.trim()?.takeIf { it.isNotEmpty() },
          customCopyCode = copy.customCopyCode?.trim()?.takeIf { it.isNotEmpty() },
          customAttributesJson = copy.customAttributesJson,
          createdAtEpochMillis = copy.createdAtEpochMillis,
        )
      )
      editionDao?.getEditionById(copy.editionId)?.let { syncWorkFtsIndexInternal(it.workId) }
    }
  }

  override suspend fun deleteCopy(copyId: Long) {
    val cDao = copyDao ?: error("CopyDao required")
    cDao.deleteCopyById(copyId)
  }

  override suspend fun syncWorkFtsIndex(workId: Long) {
    syncWorkFtsIndexInternal(workId)
  }

  private suspend fun syncWorkFtsIndexInternal(workId: Long) {
    val work = bookDao.getWorkById(workId) ?: return
    val editions = editionDao?.getEditionsForWork(workId).orEmpty()
    val copies = copyDao?.getCopiesForWork(workId).orEmpty()
    val contribRows = contributorDao?.getContributorsForWork(workId).orEmpty()
    val genre = work.primaryGenreId?.let { genreDao?.getGenreById(it) }

    val authors =
      (listOf(work.author) + contribRows.filter { it.role.isPrimaryWorkRole }.map { it.name })
        .distinct()
        .joinToString(" ")
    val translators =
      (editions.mapNotNull { it.translator } + contribRows.filter { !it.role.isPrimaryWorkRole }.map { it.name })
        .distinct()
        .joinToString(" ")
    val publishers =
      editions.mapNotNull { listOfNotNull(it.publisherOrNote, it.seriesName, it.editionLabel).joinToString(" ") }
        .joinToString(" ")
    val isbns =
      editions.flatMap { listOfNotNull(it.isbn, it.isbn10, it.isbn13) }
        .distinct()
        .joinToString(" ")
    val genreText = listOfNotNull(genre?.namePt, genre?.nameEn).joinToString(" ")
    val notesText = copies.mapNotNull { listOfNotNull(it.label, it.locationOrPlatform, it.personalNote, it.customCopyCode).joinToString(" ") }
      .joinToString(" ")

    runCatching {
      bookDao.deleteFtsByWorkId(workId)
      bookDao.upsertFts(
        LibraryFtsEntity(
          rowId = workId,
          title = work.title,
          subtitle = work.subtitle.orEmpty() + " " + work.originalTitle.orEmpty(),
          authorsText = authors,
          translatorsText = translators,
          publisherText = publishers,
          isbnsText = isbns,
          genreText = genreText,
          notesText = notesText,
        )
      )
    }
  }

  private suspend fun resolveCoverUri(
    localCoverUri: String?,
    remoteCoverUrl: String?,
  ): String? {
    if (!localCoverUri.isNullOrBlank()) {
      return if (localCoverUri.startsWith("content://") && coverStorageManager != null) {
        coverStorageManager.importCoverFromUri(Uri.parse(localCoverUri)) ?: localCoverUri
      } else {
        localCoverUri
      }
    }
    if (!remoteCoverUrl.isNullOrBlank()) {
      return coverStorageManager?.downloadAndImportCover(remoteCoverUrl) ?: remoteCoverUrl
    }
    return null
  }

  private suspend fun persistContributors(
    workId: Long,
    editionId: Long?,
    contributors: List<CandidateContributor>,
  ) {
    val cDao = contributorDao ?: return
    val workLevel = contributors.filter { !it.role.isEditionSpecificByDefault }
    val editionLevel = contributors.filter { it.role.isEditionSpecificByDefault }
    persistWorkContributorsOnly(workId, workLevel)
    if (editionId != null) {
      persistEditionContributors(workId, editionId, editionLevel)
    }
    // Fallback if only edition-level roles existed and we missed saving them to links
    if (editionId == null && editionLevel.isNotEmpty()) {
      editionLevel.forEachIndexed { idx, cand ->
        val cid = getOrCreateContributorId(cDao, cand.name) ?: return@forEachIndexed
        cDao.insertContributionLink(
          ContributionLinkEntity(
            workId = workId,
            editionId = null,
            contributorId = cid,
            role = cand.role,
            displayOrder = 100 + idx,
          )
        )
      }
    }
  }

  private suspend fun persistWorkContributorsOnly(
    workId: Long,
    contributors: List<CandidateContributor>,
  ) {
    val cDao = contributorDao ?: return
    contributors.forEachIndexed { index, cand ->
      val cid = getOrCreateContributorId(cDao, cand.name) ?: return@forEachIndexed
      cDao.insertContributionLink(
        ContributionLinkEntity(
          workId = workId,
          editionId = null,
          contributorId = cid,
          role = cand.role,
          displayOrder = index,
        )
      )
    }
  }

  private suspend fun persistEditionContributors(
    workId: Long,
    editionId: Long,
    contributors: List<CandidateContributor>,
  ) {
    val cDao = contributorDao ?: return
    contributors.forEachIndexed { index, cand ->
      val cid = getOrCreateContributorId(cDao, cand.name) ?: return@forEachIndexed
      cDao.insertContributionLink(
        ContributionLinkEntity(
          workId = workId,
          editionId = editionId,
          contributorId = cid,
          role = cand.role,
          displayOrder = 100 + index,
        )
      )
    }
  }

  private suspend fun getOrCreateContributorId(cDao: ContributorDao, rawName: String): Long? {
    val trimmed = rawName.trim()
    val norm = BibliographicMath.normalizeText(trimmed)
    if (trimmed.isEmpty() || norm.isEmpty()) return null
    cDao.findByNormalizedName(norm)?.let { return it.id }
    return runCatching {
      cDao.insertContributor(ContributorEntity(name = trimmed, normalizedName = norm))
    }.getOrNull() ?: cDao.findByNormalizedName(norm)?.id
  }

  private fun formatPrimaryAuthors(contributors: List<CandidateContributor>): String =
    contributors
      .filter { it.role.isPrimaryWorkRole }
      .joinToString(", ") { it.name.trim() }
      .ifBlank { contributors.firstOrNull()?.name?.trim().orEmpty() }

  private fun formatTranslators(contributors: List<CandidateContributor>): String? =
    contributors
      .filter { it.role == ContributorRole.TRANSLATOR }
      .joinToString(", ") { it.name.trim() }
      .takeIf { it.isNotEmpty() }

  private fun LibraryBookSummaryRow.toDomain(): CatalogBook {
    val domainGenre = primaryGenre?.toDomain()
    return CatalogBook(
      work = work.toDomain(primaryGenre = domainGenre),
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
      primaryGenre = domainGenre,
      pendingSuggestionsCount = pendingSuggestionsCount,
    )
  }

  private fun GenreEntity.toDomain(): Genre =
    Genre(
      id = id,
      slug = slug,
      namePt = namePt,
      nameEn = nameEn,
      isCustom = isCustom,
    )

  private fun WorkEntity.toDomain(primaryGenre: Genre? = null): Work =
    Work(
      id = id,
      title = title,
      author = author,
      subtitle = subtitle,
      originalTitle = originalTitle,
      primaryGenreId = primaryGenreId,
      primaryGenre = primaryGenre,
      activeSynopsis = activeSynopsis,
      synopsisLanguage = synopsisLanguage,
      synopsisSource = synopsisSource,
      isSynopsisCustomized = isSynopsisCustomized,
      externalWorkKey = externalWorkKey,
      userEditedMask = userEditedMask,
      status = status,
      isFavorite = isFavorite,
      typographyPresetOverride = typographyPresetOverride,
      createdAtEpochMillis = createdAtEpochMillis,
      updatedAtEpochMillis = updatedAtEpochMillis,
    )

  private fun EditionEntity.toDomain(): Edition {
    val decodedCategories =
      categoriesJson?.let { raw ->
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
      }.orEmpty()
    return Edition(
      id = id,
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
      synopsis = synopsis,
      synopsisSource = synopsisSource,
      categories = decodedCategories,
      externalIdsJson = externalIdsJson,
      isPrimary = isPrimary,
      createdAtEpochMillis = createdAtEpochMillis,
    )
  }

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
      condition = condition,
      acquisitionOrigin = acquisitionOrigin,
      pricePaidMinorUnits = pricePaidMinorUnits,
      currencyCode = currencyCode,
      customCopyCode = customCopyCode,
      customAttributesJson = customAttributesJson,
      createdAtEpochMillis = createdAtEpochMillis,
    )

  private fun ContributorLinkWithNameRow.toDomain(): WorkContributor =
    WorkContributor(
      contributorId = contributorId,
      name = name,
      role = role,
      editionId = editionId,
      displayOrder = displayOrder,
    )

  private fun MetadataSuggestionEntity.toDomain(): MetadataSuggestion =
    MetadataSuggestion(
      id = id,
      workId = workId,
      editionId = editionId,
      scope = scope,
      fieldName = fieldName,
      currentValue = currentValue,
      suggestedValue = suggestedValue,
      sourceName = sourceName,
      reason = reason,
      confidenceScore = confidenceScore,
      status = status,
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
