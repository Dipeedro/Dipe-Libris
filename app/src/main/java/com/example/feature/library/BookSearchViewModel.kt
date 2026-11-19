package com.example.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.di.BibliographicDraftStore
import com.example.core.di.BookReviewMode
import com.example.core.di.ReviewDraftPayload
import com.example.core.model.DuplicateAssessment
import com.example.core.model.Genre
import com.example.core.model.HybridSearchResponse
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PendingDuplicatePrompt(
  val candidate: SearchEditionCandidate,
  val assessment: DuplicateAssessment,
)

@Immutable
data class BookSearchUiState(
  val query: String = "",
  val isSearching: Boolean = false,
  val hasSearched: Boolean = false,
  val response: HybridSearchResponse? = null,
  val expandedGroupIds: Set<String> = emptySet(),
  val loadingMoreEditionsForGroups: Set<String> = emptySet(),
  val genresBySlug: Map<String, Genre> = emptyMap(),
  val duplicatePrompt: PendingDuplicatePrompt? = null,
)

class BookSearchViewModel(
  initialQuery: String?,
  private val orchestrator: HybridSearchOrchestrator,
  private val libraryRepository: LibraryRepository,
  private val preferencesRepository: PreferencesRepository,
  private val draftStore: BibliographicDraftStore,
) : ViewModel() {

  private val query = MutableStateFlow(initialQuery?.trim().orEmpty())
  private val isSearching = MutableStateFlow(false)
  private val hasSearched = MutableStateFlow(false)
  private val response = MutableStateFlow<HybridSearchResponse?>(null)
  private val expandedGroupIds = MutableStateFlow<Set<String>>(emptySet())
  private val loadingMoreEditions = MutableStateFlow<Set<String>>(emptySet())
  private val duplicatePrompt = MutableStateFlow<PendingDuplicatePrompt?>(null)

  val uiState: StateFlow<BookSearchUiState> =
    combine(
        query,
        isSearching,
        hasSearched,
        response,
        combine(
          expandedGroupIds,
          loadingMoreEditions,
          libraryRepository.observeGenres(),
          duplicatePrompt,
        ) { expanded, loadingMore, genres, dupPrompt ->
          QuadState(expanded, loadingMore, genres.associateBy { it.slug }, dupPrompt)
        },
      ) { currentQuery, searching, searched, searchResponse, quad ->
        BookSearchUiState(
          query = currentQuery,
          isSearching = searching,
          hasSearched = searched,
          response = searchResponse,
          expandedGroupIds = quad.expanded,
          loadingMoreEditionsForGroups = quad.loadingMore,
          genresBySlug = quad.genresBySlug,
          duplicatePrompt = quad.duplicatePrompt,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookSearchUiState(query = initialQuery?.trim().orEmpty()),
      )

  private data class QuadState(
    val expanded: Set<String>,
    val loadingMore: Set<String>,
    val genresBySlug: Map<String, Genre>,
    val duplicatePrompt: PendingDuplicatePrompt?,
  )

  init {
    val scanned = draftStore.consumeScannedIsbn()
    val seedQuery = scanned ?: initialQuery?.trim()?.takeIf { it.isNotEmpty() }
    if (!seedQuery.isNullOrEmpty()) {
      query.value = seedQuery
      submitSearch()
    }
  }

  fun onResumeCheckScannedIsbn() {
    val scanned = draftStore.consumeScannedIsbn() ?: return
    query.value = scanned
    submitSearch()
  }

  fun setQuery(newQuery: String) {
    query.value = newQuery
  }

  fun submitSearch(expandSecondarySources: Boolean = false) {
    val q = query.value.trim()
    if (q.isEmpty()) return
    viewModelScope.launch {
      isSearching.value = true
      try {
        val preferredLang = preferencesRepository.preferences.first().language.tag
        val result =
          orchestrator.search(
            rawQuery = q,
            preferredLanguage = preferredLang,
            expandSecondary = expandSecondarySources,
          )
        response.value = result
        hasSearched.value = true
        // Auto-expand if there's only 1 work group or if this was an ISBN lookup
        if (result.workGroups.size == 1 || result.isIsbnQuery) {
          expandedGroupIds.value = result.workGroups.take(1).map { it.groupId }.toSet()
        } else {
          expandedGroupIds.value = emptySet()
        }
      } finally {
        isSearching.value = false
      }
    }
  }

  fun toggleGroupExpanded(groupId: String) {
    expandedGroupIds.value =
      expandedGroupIds.value.let { current ->
        if (groupId in current) current - groupId else current + groupId
      }
  }

  fun loadMoreEditionsForWork(group: SearchWorkGroup) {
    if (group.externalWorkKey == null || group.groupId in loadingMoreEditions.value) return
    viewModelScope.launch {
      loadingMoreEditions.value = loadingMoreEditions.value + group.groupId
      try {
        val preferredLang = preferencesRepository.preferences.first().language.tag
        val updatedGroup = orchestrator.expandWorkEditions(group, preferredLang)
        val currentResp = response.value ?: return@launch
        val updatedGroups =
          currentResp.workGroups.map { existing ->
            if (existing.groupId == group.groupId) updatedGroup else existing
          }
        response.value = currentResp.copy(workGroups = updatedGroups)
      } finally {
        loadingMoreEditions.value = loadingMoreEditions.value - group.groupId
      }
    }
  }

  fun selectEditionCandidate(
    candidate: SearchEditionCandidate,
    onReadyForReview: () -> Unit,
  ) {
    viewModelScope.launch {
      val assessment =
        libraryRepository.assessDuplicates(
          isbn13 = candidate.isbn13,
          isbn10 = candidate.isbn10,
          externalWorkKey = candidate.externalWorkKey,
          title = candidate.workTitle,
          author = candidate.primaryAuthorsText,
        )
      when (assessment) {
        DuplicateAssessment.NoMatch -> {
          draftStore.setReviewDraft(
            ReviewDraftPayload(
              mode = BookReviewMode.IMPORTED,
              candidate = candidate,
            )
          )
          onReadyForReview()
        }
        else -> {
          duplicatePrompt.value = PendingDuplicatePrompt(candidate = candidate, assessment = assessment)
        }
      }
    }
  }

  fun dismissDuplicatePrompt() {
    duplicatePrompt.value = null
  }

  fun proceedAddCopyToExistingEdition(
    candidate: SearchEditionCandidate,
    existingEditionId: Long,
    existingWorkId: Long,
    onReadyForReview: () -> Unit,
  ) {
    duplicatePrompt.value = null
    draftStore.setReviewDraft(
      ReviewDraftPayload(
        mode = BookReviewMode.ADD_COPY_TO_EDITION,
        candidate = candidate,
        targetWorkId = existingWorkId,
        targetEditionId = existingEditionId,
      )
    )
    onReadyForReview()
  }

  fun proceedAddEditionToExistingWork(
    candidate: SearchEditionCandidate,
    existingWorkId: Long,
    onReadyForReview: () -> Unit,
  ) {
    duplicatePrompt.value = null
    draftStore.setReviewDraft(
      ReviewDraftPayload(
        mode = BookReviewMode.ADD_EDITION_TO_WORK,
        candidate = candidate,
        targetWorkId = existingWorkId,
      )
    )
    onReadyForReview()
  }

  fun proceedSaveAsSeparateWork(
    candidate: SearchEditionCandidate,
    onReadyForReview: () -> Unit,
  ) {
    duplicatePrompt.value = null
    draftStore.setReviewDraft(
      ReviewDraftPayload(
        mode = BookReviewMode.IMPORTED,
        candidate = candidate,
      )
    )
    onReadyForReview()
  }

  fun startManualRegistration(onReadyForReview: () -> Unit) {
    val trimmed = query.value.trim().takeIf { it.isNotEmpty() }
    draftStore.setReviewDraft(
      ReviewDraftPayload(
        mode = BookReviewMode.MANUAL,
        prefillTitleOrIsbn = trimmed,
      )
    )
    onReadyForReview()
  }
}
