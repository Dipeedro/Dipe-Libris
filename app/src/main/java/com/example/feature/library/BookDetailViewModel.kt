package com.example.feature.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.ReadingJourneyRepository
import com.example.core.model.BookCopy
import com.example.core.model.BookDetailAggregate
import com.example.core.model.CatalogBook
import com.example.core.model.CopyMediaType
import com.example.core.model.DiaryEntry
import com.example.core.model.Edition
import com.example.core.model.EditionFormat
import com.example.core.model.EditionWithBookCopies
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface BookDetailUiState {
  @Immutable data object Loading : BookDetailUiState

  @Immutable
  data class Loaded(
    val detail: BookDetailAggregate,
    val diaryNotes: List<DiaryEntry>,
    val focusedEditionId: Long? = null,
    val isDeleting: Boolean = false,
  ) : BookDetailUiState {
    constructor(
      book: CatalogBook,
      diaryNotes: List<DiaryEntry>,
      isDeleting: Boolean = false,
    ) : this(
      detail =
        BookDetailAggregate(
          work = book.work,
          editions =
            listOfNotNull(
              book.primaryEdition?.let { ed ->
                EditionWithBookCopies(
                  edition = ed,
                  copies = listOfNotNull(book.primaryCopy),
                )
              }
            ),
          activeJourney = null,
          journeys = emptyList(),
        ),
      diaryNotes = diaryNotes,
      focusedEditionId = book.primaryEdition?.id,
      isDeleting = isDeleting,
    )

    val book: CatalogBook
      get() {
        val activeEd =
          detail.activeJourney?.edition
            ?: detail.editions.firstOrNull { it.edition.isPrimary }?.edition
            ?: detail.editions.firstOrNull()?.edition
        val activeCopy =
          detail.activeJourney?.copy
            ?: detail.editions.firstOrNull { it.edition.id == activeEd?.id }?.copies?.firstOrNull()
        return CatalogBook(
          work = detail.work,
          primaryEdition = activeEd,
          primaryCopy = activeCopy,
          editionCount = detail.editions.size,
          copyCount = detail.totalCopiesCount,
          activeSessionId = detail.activeJourney?.session?.id,
          activeProgressPercent = detail.activeJourney?.session?.progressPercent,
          activeCurrentPage = detail.activeJourney?.session?.currentPage,
          activeTotalPages = activeEd?.totalPages ?: detail.activeJourney?.session?.totalPagesSnapshot,
          activeRereadNumber = detail.activeJourney?.session?.rereadNumber,
          activeCopyMediaType = activeCopy?.mediaType,
          activeCopyLabel = activeCopy?.label,
        )
      }
  }

  @Immutable data object NotFound : BookDetailUiState
}

class BookDetailViewModel(
  private val workId: Long,
  private val libraryRepository: LibraryRepository,
  diaryRepository: DiaryRepository,
  private val readingJourneyRepository: ReadingJourneyRepository? = null,
  private val coverStorageManager: CoverStorageManager? = null,
) : ViewModel() {

  private val isDeleting = MutableStateFlow(false)
  private val focusedEditionId = MutableStateFlow<Long?>(null)

  val uiState: StateFlow<BookDetailUiState> =
    combine(
        libraryRepository.observeBookDetail(workId),
        diaryRepository.observeEntriesForWork(workId),
        focusedEditionId,
        isDeleting,
      ) { detail, notes, requestedEditionId, deleting ->
        if (detail == null) {
          BookDetailUiState.NotFound
        } else {
          val resolvedEditionId =
            requestedEditionId?.takeIf { id -> detail.editions.any { it.edition.id == id } }
              ?: detail.activeJourney?.edition?.id
              ?: detail.editions.firstOrNull { it.edition.isPrimary }?.edition?.id
              ?: detail.editions.firstOrNull()?.edition?.id

          BookDetailUiState.Loaded(
            detail = detail,
            diaryNotes = notes,
            focusedEditionId = resolvedEditionId,
            isDeleting = deleting,
          )
        }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookDetailUiState.Loading,
      )

  fun selectEdition(editionId: Long) {
    focusedEditionId.value = editionId
  }

  fun toggleFavorite(currentFavorite: Boolean) {
    viewModelScope.launch { libraryRepository.toggleFavorite(workId, currentFavorite) }
  }

  fun updateStatus(status: ReadingStatus) {
    viewModelScope.launch { libraryRepository.updateReadingStatus(workId, status) }
  }

  fun updateWorkMetadata(title: String, author: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      libraryRepository.updateWorkMetadata(workId = workId, title = title, author = author)
      onDone()
    }
  }

  fun addEdition(
    publisherOrNote: String?,
    publicationYear: Int?,
    totalPages: Int?,
    isbn: String?,
    pickedCoverUri: Uri?,
    language: String?,
    format: EditionFormat?,
    translator: String?,
    setAsPrimary: Boolean,
    alsoCreateCopy: Boolean = true,
    copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    copyLabel: String? = null,
    onDone: (Long) -> Unit = {},
  ) {
    viewModelScope.launch {
      val importedPath =
        pickedCoverUri?.let { uri -> coverStorageManager?.importCoverFromUri(uri) }
      val newEditionId =
        libraryRepository.addEdition(
          workId = workId,
          publisherOrNote = publisherOrNote,
          publicationYear = publicationYear,
          totalPages = totalPages,
          isbn = isbn,
          coverUri = importedPath,
          language = language,
          format = format,
          translator = translator,
          setAsPrimary = setAsPrimary,
          alsoCreateCopy = alsoCreateCopy,
          copyMediaType = copyMediaType,
          copyLabel = copyLabel,
        )
      focusedEditionId.value = newEditionId
      onDone(newEditionId)
    }
  }

  fun updateEdition(
    edition: Edition,
    pickedCoverUri: Uri?,
    removeCover: Boolean,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      val finalCoverUri =
        when {
          pickedCoverUri != null -> coverStorageManager?.importCoverFromUri(pickedCoverUri) ?: edition.coverUri
          removeCover -> null
          else -> edition.coverUri
        }
      libraryRepository.updateEdition(edition.copy(coverUri = finalCoverUri))
      onDone()
    }
  }

  fun setPrimaryEdition(editionId: Long) {
    viewModelScope.launch { libraryRepository.setPrimaryEdition(workId, editionId) }
  }

  fun deleteEdition(editionId: Long, onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
      val deleted = libraryRepository.deleteEdition(workId, editionId)
      if (deleted && focusedEditionId.value == editionId) {
        focusedEditionId.value = null
      }
      onResult(deleted)
    }
  }

  fun addCopy(
    editionId: Long,
    mediaType: CopyMediaType,
    label: String?,
    physicalFormat: String?,
    locationOrPlatform: String?,
    personalNote: String?,
    setAsActiveReadingCopy: Boolean,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      libraryRepository.addCopy(
        editionId = editionId,
        mediaType = mediaType,
        label = label,
        physicalFormat = physicalFormat,
        locationOrPlatform = locationOrPlatform,
        personalNote = personalNote,
        setAsActiveReadingCopy = setAsActiveReadingCopy,
      )
      onDone()
    }
  }

  fun updateCopy(copy: BookCopy, setAsActiveReadingCopy: Boolean = false, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      libraryRepository.updateCopy(copy)
      if (setAsActiveReadingCopy) {
        val currentSession = (uiState.value as? BookDetailUiState.Loaded)?.detail?.activeJourney?.session
        if (currentSession != null) {
          readingJourneyRepository?.switchActiveCopy(currentSession.id, copy.id)
        }
      }
      onDone()
    }
  }

  fun deleteCopy(copyId: Long, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      libraryRepository.deleteCopy(copyId)
      onDone()
    }
  }

  fun startJourney(copyId: Long?, notes: String? = null, onDone: () -> Unit = {}) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      journeyRepo.startJourney(workId = workId, copyId = copyId, notes = notes)
      onDone()
    }
  }

  fun startReread(copyId: Long?, notes: String? = null, onDone: () -> Unit = {}) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      journeyRepo.startReread(workId = workId, copyId = copyId, notes = notes)
      onDone()
    }
  }

  fun updateProgressByPage(
    sessionId: Long,
    page: Int,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
  ) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      journeyRepo.updateProgressByPage(
        sessionId = sessionId,
        page = page,
        note = note,
        markFinishedIfComplete = markFinishedIfComplete,
      )
    }
  }

  fun updateProgressByPercent(
    sessionId: Long,
    percent: Float,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
  ) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      journeyRepo.updateProgressByPercent(
        sessionId = sessionId,
        percent = percent,
        note = note,
        markFinishedIfComplete = markFinishedIfComplete,
      )
    }
  }

  fun pauseJourney(sessionId: Long) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.pauseJourney(sessionId) }
  }

  fun resumeJourney(sessionId: Long) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.resumeJourney(sessionId) }
  }

  fun completeJourney(sessionId: Long) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.completeJourney(sessionId) }
  }

  fun abandonJourney(sessionId: Long) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.abandonJourney(sessionId) }
  }

  fun switchActiveCopy(sessionId: Long, copyId: Long) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.switchActiveCopy(sessionId, copyId) }
  }

  fun deleteBook(onDeleted: () -> Unit) {
    if (isDeleting.value) return
    isDeleting.value = true
    viewModelScope.launch {
      try {
        libraryRepository.deleteBook(workId)
        onDeleted()
      } finally {
        isDeleting.value = false
      }
    }
  }
}
