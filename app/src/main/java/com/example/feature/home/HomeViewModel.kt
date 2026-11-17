package com.example.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.StatisticsRepository
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.CatalogBook
import com.example.core.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
sealed interface RecentActivityItem {
  val stableKey: String
  val timestampMillis: Long

  @Immutable
  data class BookCataloged(val book: CatalogBook) : RecentActivityItem {
    override val stableKey: String = "book_${book.id}"
    override val timestampMillis: Long = book.work.createdAtEpochMillis
  }

  @Immutable
  data class DiaryRecorded(val entry: DiaryEntry) : RecentActivityItem {
    override val stableKey: String = "diary_${entry.id}"
    override val timestampMillis: Long = entry.createdAtEpochMillis
  }
}

@Immutable
data class HomeUiState(
  val currentlyReading: List<CatalogBook> = emptyList(),
  val recentActivity: List<RecentActivityItem> = emptyList(),
  val snapshot: ArchiveSnapshot = ArchiveSnapshot(),
)

class HomeViewModel(
  libraryRepository: LibraryRepository,
  diaryRepository: DiaryRepository,
  statisticsRepository: StatisticsRepository,
) : ViewModel() {

  val uiState: StateFlow<HomeUiState> =
    combine(
        libraryRepository.observeCurrentlyReading(),
        libraryRepository.observeAllBooks(),
        diaryRepository.observeAllEntries(),
        statisticsRepository.observeArchiveSnapshot(),
      ) { reading, allBooks, entries, snapshot ->
        val activity =
          (allBooks.take(5).map { RecentActivityItem.BookCataloged(it) } +
              entries.take(5).map { RecentActivityItem.DiaryRecorded(it) })
            .sortedByDescending { it.timestampMillis }
            .take(6)

        HomeUiState(
          currentlyReading = reading,
          recentActivity = activity,
          snapshot = snapshot,
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )
}
