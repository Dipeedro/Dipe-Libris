package com.example.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.StatisticsRepository
import com.example.core.model.ArchiveSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(
  statisticsRepository: StatisticsRepository,
) : ViewModel() {

  val snapshot: StateFlow<ArchiveSnapshot> =
    statisticsRepository.observeArchiveSnapshot().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ArchiveSnapshot(),
    )
}
