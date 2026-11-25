package com.example.core.bibliographic.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Coordinates foreground bibliographic search activity with background cover upgrades so
 * CoverUpgradeCoordinator yields network, decode memory, and Room writes while a search runs.
 */
class SearchActivityCoordinator {
  private val activeSearchCount = AtomicInteger(0)
  private val _isSearchActive = MutableStateFlow(false)
  val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

  fun onSearchStarted() {
    if (activeSearchCount.incrementAndGet() > 0) {
      _isSearchActive.value = true
    }
  }

  fun onSearchFinished() {
    val remaining = activeSearchCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    if (remaining == 0) {
      _isSearchActive.value = false
    }
  }

  suspend fun awaitSearchIdle() {
    if (!_isSearchActive.value) return
    _isSearchActive.first { !it }
  }
}
