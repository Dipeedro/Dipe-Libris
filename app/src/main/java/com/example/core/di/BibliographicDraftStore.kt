package com.example.core.di

import com.example.core.model.SearchEditionCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BookReviewMode {
  IMPORTED,
  MANUAL,
  ADD_EDITION_TO_WORK,
  ADD_COPY_TO_EDITION,
}

data class ReviewDraftPayload(
  val mode: BookReviewMode,
  val candidate: SearchEditionCandidate? = null,
  val targetWorkId: Long? = null,
  val targetEditionId: Long? = null,
  val prefillTitleOrIsbn: String? = null,
)

class BibliographicDraftStore {
  private val _pendingReviewDraft = MutableStateFlow<ReviewDraftPayload?>(null)
  val pendingReviewDraft: StateFlow<ReviewDraftPayload?> = _pendingReviewDraft.asStateFlow()

  private val _scannedIsbn = MutableStateFlow<String?>(null)
  val scannedIsbn: StateFlow<String?> = _scannedIsbn.asStateFlow()

  fun setReviewDraft(payload: ReviewDraftPayload) {
    _pendingReviewDraft.value = payload
  }

  fun consumeReviewDraft(): ReviewDraftPayload? {
    val current = _pendingReviewDraft.value
    _pendingReviewDraft.value = null
    return current
  }

  fun clearReviewDraft() {
    _pendingReviewDraft.value = null
  }

  fun setScannedIsbn(isbn: String) {
    _scannedIsbn.value = isbn
  }

  fun consumeScannedIsbn(): String? {
    val current = _scannedIsbn.value
    _scannedIsbn.value = null
    return current
  }
}
