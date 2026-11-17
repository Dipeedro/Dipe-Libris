package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.StatisticsRepository
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.CatalogBook
import com.example.core.model.DiaryEntry
import com.example.core.model.Shelf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface RecentActivityItem {
  val timestampMillis: Long

  data class BookCataloged(val book: CatalogBook) : RecentActivityItem {
    override val timestampMillis: Long = book.work.createdAtEpochMillis
  }

  data class DiaryRecorded(val entry: DiaryEntry) : RecentActivityItem {
    override val timestampMillis: Long = entry.createdAtEpochMillis
  }
}

data class HomeUiState(
  val currentlyReading: List<CatalogBook> = emptyList(),
  val recentActivity: List<RecentActivityItem> = emptyList(),
  val featuredShelves: List<Shelf> = emptyList(),
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
      libraryRepository.observeShelves(),
      statisticsRepository.observeArchiveSnapshot(),
    ) { reading, allBooks, entries, shelves, snapshot ->
      val activity =
        (allBooks.take(5).map { RecentActivityItem.BookCataloged(it) } +
            entries.take(5).map { RecentActivityItem.DiaryRecorded(it) })
          .sortedByDescending { it.timestampMillis }
          .take(6)

      HomeUiState(
        currentlyReading = reading,
        recentActivity = activity,
        featuredShelves = shelves.filter { it.isFeatured },
        snapshot = snapshot,
      )
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )
}
