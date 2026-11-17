package com.example.feature.diary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.model.CatalogBook
import com.example.core.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface DiaryUiState {
  @Immutable data object Loading : DiaryUiState

  @Immutable
  data class Loaded(
    val book: CatalogBook,
    val entries: List<DiaryEntry>,
  ) : DiaryUiState

  @Immutable data object NotFound : DiaryUiState
}

class DiaryViewModel(
  private val workId: Long,
  private val diaryRepository: DiaryRepository,
  libraryRepository: LibraryRepository,
) : ViewModel() {

  val uiState: StateFlow<DiaryUiState> =
    combine(
        libraryRepository.observeBook(workId),
        diaryRepository.observeEntriesForWork(workId),
      ) { book, entries ->
        if (book == null) {
          DiaryUiState.NotFound
        } else {
          DiaryUiState.Loaded(
            book = book,
            entries = entries,
          )
        }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiaryUiState.Loading,
      )

  fun addEntry(content: String, onSaved: () -> Unit = {}) {
    if (content.isBlank()) return
    viewModelScope.launch {
      diaryRepository.addEntry(content = content, workId = workId)
      onSaved()
    }
  }

  fun deleteEntry(entryId: Long) {
    viewModelScope.launch { diaryRepository.deleteEntry(entryId) }
  }
}
