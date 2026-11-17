package com.example.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.model.CatalogBook
import com.example.core.model.DiaryEntry
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BookDetailUiState {
  data object Loading : BookDetailUiState

  data class Loaded(
    val book: CatalogBook,
    val diaryNotes: List<DiaryEntry>,
    val isDeleting: Boolean = false,
  ) : BookDetailUiState

  data object NotFound : BookDetailUiState
}

class BookDetailViewModel(
  private val workId: Long,
  private val libraryRepository: LibraryRepository,
  diaryRepository: DiaryRepository,
) : ViewModel() {

  private val isDeleting = MutableStateFlow(false)

  val uiState: StateFlow<BookDetailUiState> =
    combine(
      libraryRepository.observeBook(workId),
      diaryRepository.observeEntriesForWork(workId),
      isDeleting,
    ) { book, notes, deleting ->
      if (book == null) {
        BookDetailUiState.NotFound
      } else {
        BookDetailUiState.Loaded(
          book = book,
          diaryNotes = notes,
          isDeleting = deleting,
        )
      }
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookDetailUiState.Loading,
      )

  fun toggleFavorite(currentFavorite: Boolean) {
    viewModelScope.launch {
      libraryRepository.toggleFavorite(workId, currentFavorite)
    }
  }

  fun updateStatus(status: ReadingStatus) {
    viewModelScope.launch {
      libraryRepository.updateReadingStatus(workId, status)
    }
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
