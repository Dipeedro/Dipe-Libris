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
data class DiaryUiState(
  val entries: List<DiaryEntry> = emptyList(),
  val availableBooks: List<CatalogBook> = emptyList(),
)

class DiaryViewModel(
  private val diaryRepository: DiaryRepository,
  libraryRepository: LibraryRepository,
) : ViewModel() {

  val uiState: StateFlow<DiaryUiState> =
    combine(
        diaryRepository.observeAllEntries(),
        libraryRepository.observeAllBooks(),
      ) { entries, books ->
        DiaryUiState(
          entries = entries,
          availableBooks = books,
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiaryUiState(),
      )

  fun addEntry(content: String, workId: Long?, onSaved: () -> Unit = {}) {
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
