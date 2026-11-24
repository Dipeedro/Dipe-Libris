package com.example.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.JourneyCompletionSnapshot
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.ReadingJourneyRepository
import com.example.core.data.repository.StatisticsRepository
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.CatalogBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
  val currentlyReading: List<CatalogBook> = emptyList(),
  val snapshot: ArchiveSnapshot = ArchiveSnapshot(),
  val isLoading: Boolean = false,
)

class HomeViewModel(
  libraryRepository: LibraryRepository,
  statisticsRepository: StatisticsRepository,
  private val readingJourneyRepository: ReadingJourneyRepository? = null,
) : ViewModel() {

  val uiState: StateFlow<HomeUiState> =
    combine(
        libraryRepository.observeCurrentlyReading(),
        statisticsRepository.observeArchiveSnapshot(),
      ) { reading, snapshot ->
        HomeUiState(
          currentlyReading = reading,
          snapshot = snapshot,
          isLoading = false,
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
      )

  fun updateProgressByPage(
    book: CatalogBook,
    page: Int,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
    updatedTotalPages: Int? = null,
  ) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      val sessionId =
        book.activeSessionId ?: journeyRepo.startJourney(book.id, book.primaryCopy?.id)
      journeyRepo.updateProgressByPage(
        sessionId = sessionId,
        page = page,
        note = note,
        markFinishedIfComplete = markFinishedIfComplete,
        updatedTotalPages = updatedTotalPages,
      )
    }
  }

  fun updateProgressByPercent(
    book: CatalogBook,
    percent: Float,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
  ) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      val sessionId =
        book.activeSessionId ?: journeyRepo.startJourney(book.id, book.primaryCopy?.id)
      journeyRepo.updateProgressByPercent(
        sessionId = sessionId,
        percent = percent,
        note = note,
        markFinishedIfComplete = markFinishedIfComplete,
      )
    }
  }

  fun completeReading(book: CatalogBook) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      val sessionId =
        book.activeSessionId ?: journeyRepo.startJourney(book.id, book.primaryCopy?.id)
      journeyRepo.completeJourney(sessionId)
    }
  }

  fun completeReadingWithUndo(
    book: CatalogBook,
    note: String? = null,
    onCompleted: (JourneyCompletionSnapshot) -> Unit,
  ) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch {
      val sessionId =
        book.activeSessionId ?: journeyRepo.startJourney(book.id, book.primaryCopy?.id)
      val snapshot =
        journeyRepo.completeJourneyWithUndoSnapshot(sessionId = sessionId, note = note)
      if (snapshot != null) {
        onCompleted(snapshot)
      }
    }
  }

  fun undoCompletion(snapshot: JourneyCompletionSnapshot) {
    val journeyRepo = readingJourneyRepository ?: return
    viewModelScope.launch { journeyRepo.restoreJourneyCompletion(snapshot) }
  }
}
