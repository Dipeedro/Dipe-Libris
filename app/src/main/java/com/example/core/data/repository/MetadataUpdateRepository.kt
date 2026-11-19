package com.example.core.data.repository

import androidx.room.withTransaction
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.MetadataDiffAnalyzer
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.MetadataSuggestionDao
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.model.BibliographicMath
import com.example.core.model.MetadataSuggestionScope
import com.example.core.model.MetadataSuggestionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class LibraryUpdateCheckSummary(
  val booksChecked: Int,
  val newSuggestionsCount: Int,
)

interface MetadataUpdateRepository {
  fun observePendingSuggestionsCount(): Flow<Int>

  suspend fun checkUpdatesForWork(workId: Long, preferredLanguage: String? = null): Int

  suspend fun checkUpdatesForLibrary(preferredLanguage: String? = null): LibraryUpdateCheckSummary

  suspend fun acceptSuggestion(suggestionId: Long)

  suspend fun rejectSuggestion(suggestionId: Long)
}

class DefaultMetadataUpdateRepository(
  private val libraryRepository: LibraryRepository,
  private val orchestrator: HybridSearchOrchestrator,
  private val suggestionDao: MetadataSuggestionDao,
  private val bookDao: BookDao,
  private val editionDao: EditionDao,
  private val coverStorageManager: CoverStorageManager,
  private val database: DipeLibrisDatabase,
) : MetadataUpdateRepository {

  override fun observePendingSuggestionsCount(): Flow<Int> =
    suggestionDao.observeTotalPendingCount()

  override suspend fun checkUpdatesForWork(
    workId: Long,
    preferredLanguage: String?,
  ): Int {
    val detail = libraryRepository.observeBookDetail(workId).first() ?: return 0
    val isbns =
      detail.editions.flatMap { ed ->
        listOfNotNull(ed.edition.isbn13, ed.edition.isbn10, ed.edition.isbn)
      }.distinct()

    val candidates =
      orchestrator.fetchCandidatesForBookUpdate(
        title = detail.work.title,
        author = detail.work.author,
        isbns = isbns,
        preferredLanguage = preferredLanguage,
      )
    if (candidates.isEmpty()) return 0

    val existing = suggestionDao.getAllForWork(workId)
    val discovered =
      MetadataDiffAnalyzer.analyze(
        book = detail,
        candidates = candidates,
        existingSuggestions = existing,
        preferredLanguage = preferredLanguage,
      )
    if (discovered.isNotEmpty()) {
      suggestionDao.insertSuggestions(discovered)
    }
    return discovered.size
  }

  override suspend fun checkUpdatesForLibrary(preferredLanguage: String?): LibraryUpdateCheckSummary {
    val allBooks = libraryRepository.observeAllBooks().first()
    var totalNew = 0
    var checked = 0
    for (book in allBooks.take(25)) {
      val added = runCatching { checkUpdatesForWork(book.work.id, preferredLanguage) }.getOrDefault(0)
      totalNew += added
      checked++
    }
    return LibraryUpdateCheckSummary(booksChecked = checked, newSuggestionsCount = totalNew)
  }

  override suspend fun acceptSuggestion(suggestionId: Long) {
    val suggestion = suggestionDao.getById(suggestionId) ?: return
    if (suggestion.status != MetadataSuggestionStatus.PENDING) return

    val downloadedCoverPath =
      if (suggestion.fieldName == MetadataDiffAnalyzer.FIELD_COVER_URL) {
        coverStorageManager.downloadAndImportCover(suggestion.suggestedValue)
          ?: suggestion.suggestedValue
      } else {
        null
      }

    database.withTransaction {
      when (suggestion.scope) {
        MetadataSuggestionScope.WORK -> {
          val work = bookDao.getWorkById(suggestion.workId) ?: return@withTransaction
          val updated =
            when (suggestion.fieldName) {
              MetadataDiffAnalyzer.FIELD_SUBTITLE ->
                work.copy(subtitle = suggestion.suggestedValue, updatedAtEpochMillis = System.currentTimeMillis())
              MetadataDiffAnalyzer.FIELD_SYNOPSIS ->
                work.copy(
                  activeSynopsis = suggestion.suggestedValue,
                  synopsisSource = suggestion.sourceName,
                  updatedAtEpochMillis = System.currentTimeMillis(),
                )
              else -> work
            }
          bookDao.updateWork(updated)
        }

        MetadataSuggestionScope.EDITION -> {
          val editionId = suggestion.editionId ?: return@withTransaction
          val edition = editionDao.getEditionById(editionId) ?: return@withTransaction
          val updated =
            when (suggestion.fieldName) {
              MetadataDiffAnalyzer.FIELD_ISBN13 -> {
                val parsed = BibliographicMath.parseIsbn(suggestion.suggestedValue)
                edition.copy(
                  isbn = parsed?.isbn13 ?: suggestion.suggestedValue,
                  isbn13 = parsed?.isbn13 ?: suggestion.suggestedValue,
                  isbn10 = edition.isbn10 ?: parsed?.isbn10,
                )
              }
              MetadataDiffAnalyzer.FIELD_PUBLISHER ->
                edition.copy(publisherOrNote = suggestion.suggestedValue)
              MetadataDiffAnalyzer.FIELD_PUBLICATION_YEAR ->
                edition.copy(publicationYear = suggestion.suggestedValue.toIntOrNull())
              MetadataDiffAnalyzer.FIELD_TOTAL_PAGES ->
                edition.copy(totalPages = suggestion.suggestedValue.toIntOrNull())
              MetadataDiffAnalyzer.FIELD_SERIES_NAME -> {
                val parts = suggestion.suggestedValue.split("#")
                val seriesName = parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                val seriesVol = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                edition.copy(seriesName = seriesName, seriesVolume = seriesVol ?: edition.seriesVolume)
              }
              MetadataDiffAnalyzer.FIELD_TRANSLATOR ->
                edition.copy(translator = suggestion.suggestedValue)
              MetadataDiffAnalyzer.FIELD_COVER_URL ->
                edition.copy(
                  coverUri = downloadedCoverPath ?: suggestion.suggestedValue,
                  coverSource = suggestion.sourceName,
                )
              else -> edition
            }
          editionDao.updateEdition(updated)
        }
      }
      suggestionDao.updateStatus(suggestionId, MetadataSuggestionStatus.ACCEPTED)
    }
    libraryRepository.syncWorkFtsIndex(suggestion.workId)
  }

  override suspend fun rejectSuggestion(suggestionId: Long) {
    suggestionDao.updateStatus(suggestionId, MetadataSuggestionStatus.REJECTED)
  }
}
