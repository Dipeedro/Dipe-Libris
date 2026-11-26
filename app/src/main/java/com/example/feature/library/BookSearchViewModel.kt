package com.example.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.domain.SearchActivityCoordinator
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.di.BibliographicDraftStore
import com.example.core.di.BookReviewMode
import com.example.core.di.ReviewDraftPayload
import com.example.core.model.CatalogBook
import com.example.core.model.DuplicateAssessment
import com.example.core.model.Genre
import com.example.core.model.HybridSearchResponse
import com.example.core.model.SearchEditionCandidate
import com.example.core.model.SearchWorkGroup
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
  val localLiveMatches: List<CatalogBook> = emptyList(),
  val isLoadingMoreResults: Boolean = false,
  val isSecondaryLanguagesExpanded: Boolean = false,
  val isDerivativeWorksExpanded: Boolean = false,
  val expandedSecondaryEditionGroupIds: Set<String> = emptySet(),
  val exhaustedEditionGroupIds: Set<String> = emptySet(),
  val editionWindowLimits: Map<String, Int> = emptyMap(),
  val hasPartialFailureWarning: Boolean = false,
  val hasSearchError: Boolean = false,
  val selectedEdition: SearchEditionCandidate? = null,
)

class BookSearchViewModel(
  initialQuery: String?,
  private val orchestrator: HybridSearchOrchestrator,
  private val libraryRepository: LibraryRepository,
  private val preferencesRepository: PreferencesRepository,
  private val draftStore: BibliographicDraftStore,
  private val searchActivityCoordinator: SearchActivityCoordinator? = null,
) : ViewModel() {

  private val query = MutableStateFlow(initialQuery?.trim().orEmpty())
  private val isSearching = MutableStateFlow(false)
  private val hasSearched = MutableStateFlow(false)
  private val response = MutableStateFlow<HybridSearchResponse?>(null)
  private val expandedGroupIds = MutableStateFlow<Set<String>>(emptySet())
  private val loadingMoreEditions = MutableStateFlow<Set<String>>(emptySet())
  private val duplicatePrompt = MutableStateFlow<PendingDuplicatePrompt?>(null)
  private val localLiveMatches = MutableStateFlow<List<CatalogBook>>(emptyList())
  private val isLoadingMoreResults = MutableStateFlow(false)
  private val isSecondaryLanguagesExpanded = MutableStateFlow(false)
  private val isDerivativeWorksExpanded = MutableStateFlow(false)
  private val expandedSecondaryEditionGroupIds = MutableStateFlow<Set<String>>(emptySet())
  private val exhaustedEditionGroupIds = MutableStateFlow<Set<String>>(emptySet())
  private val editionWindowLimits = MutableStateFlow<Map<String, Int>>(emptyMap())
  private val hasPartialFailureWarning = MutableStateFlow(false)
  private val hasSearchError = MutableStateFlow(false)
  private val selectedEdition = MutableStateFlow<SearchEditionCandidate?>(null)

  private val searchGenerationId = AtomicLong(0L)
  private var localSearchJob: Job? = null
  private var searchJob: Job? = null
  private var paginationJob: Job? = null
  private var userToggledGroupInCurrentSearch: Boolean = false

  private val editionExpansionState =
    combine(
      expandedSecondaryEditionGroupIds,
      exhaustedEditionGroupIds,
      editionWindowLimits,
      hasPartialFailureWarning,
      hasSearchError,
    ) { secEditions, exhausted, windowLimits, partialWarning, searchError ->
      EditionAndErrorState(
        expandedSecondaryEditionGroupIds = secEditions,
        exhaustedEditionGroupIds = exhausted,
        editionWindowLimits = windowLimits,
        hasPartialFailureWarning = partialWarning,
        hasSearchError = searchError,
      )
    }

  private val extraState =
    combine(
      localLiveMatches,
      isLoadingMoreResults,
      isSecondaryLanguagesExpanded,
      isDerivativeWorksExpanded,
      editionExpansionState,
    ) { localMatches, loadingMore, secondaryExpanded, derivativeExpanded, expansion ->
      ExtraSearchState(
        localMatches = localMatches,
        loadingMoreResults = loadingMore,
        secondaryExpanded = secondaryExpanded,
        derivativeExpanded = derivativeExpanded,
        expandedSecondaryEditionGroupIds = expansion.expandedSecondaryEditionGroupIds,
        exhaustedEditionGroupIds = expansion.exhaustedEditionGroupIds,
        editionWindowLimits = expansion.editionWindowLimits,
        hasPartialFailureWarning = expansion.hasPartialFailureWarning,
        hasSearchError = expansion.hasSearchError,
      )
    }

  private val detailState =
    combine(
      expandedGroupIds,
      loadingMoreEditions,
      libraryRepository.observeGenres(),
      duplicatePrompt,
      selectedEdition,
    ) { expanded, loadingMore, genres, dupPrompt, selected ->
      QuadState(expanded, loadingMore, genres.associateBy { it.slug }, dupPrompt, selected)
    }

  val uiState: StateFlow<BookSearchUiState> =
    combine(
        query,
        isSearching,
        hasSearched,
        response,
        combine(detailState, extraState) { quad, extra -> quad to extra },
      ) { currentQuery, searching, searched, searchResponse, combinedPair ->
        val (quad, extra) = combinedPair
        BookSearchUiState(
          query = currentQuery,
          isSearching = searching,
          hasSearched = searched,
          response = searchResponse,
          expandedGroupIds = quad.expanded,
          loadingMoreEditionsForGroups = quad.loadingMore,
          genresBySlug = quad.genresBySlug,
          duplicatePrompt = quad.duplicatePrompt,
          localLiveMatches = extra.localMatches,
          isLoadingMoreResults = extra.loadingMoreResults,
          isSecondaryLanguagesExpanded = extra.secondaryExpanded,
          isDerivativeWorksExpanded = extra.derivativeExpanded,
          expandedSecondaryEditionGroupIds = extra.expandedSecondaryEditionGroupIds,
          exhaustedEditionGroupIds = extra.exhaustedEditionGroupIds,
          editionWindowLimits = extra.editionWindowLimits,
          hasPartialFailureWarning = extra.hasPartialFailureWarning,
          hasSearchError = extra.hasSearchError,
          selectedEdition = quad.selectedEdition,
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
    val selectedEdition: SearchEditionCandidate?,
  )

  private data class EditionAndErrorState(
    val expandedSecondaryEditionGroupIds: Set<String>,
    val exhaustedEditionGroupIds: Set<String>,
    val editionWindowLimits: Map<String, Int>,
    val hasPartialFailureWarning: Boolean,
    val hasSearchError: Boolean,
  )

  private data class ExtraSearchState(
    val localMatches: List<CatalogBook>,
    val loadingMoreResults: Boolean,
    val secondaryExpanded: Boolean,
    val derivativeExpanded: Boolean,
    val expandedSecondaryEditionGroupIds: Set<String>,
    val exhaustedEditionGroupIds: Set<String>,
    val editionWindowLimits: Map<String, Int>,
    val hasPartialFailureWarning: Boolean,
    val hasSearchError: Boolean,
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

  /**
   * Instantaneous local-only library feedback while typing (no external API calls until Buscar/Enter).
   */
  fun setQuery(newQuery: String) {
    query.value = newQuery
    localSearchJob?.cancel()
    val trimmed = newQuery.trim()
    if (trimmed.isEmpty()) {
      localLiveMatches.value = emptyList()
      return
    }
    localSearchJob =
      viewModelScope.launch {
        delay(80L)
        localLiveMatches.value = runCatching { orchestrator.findLocalMatchesOnly(trimmed) }.getOrDefault(emptyList())
      }
  }

  fun submitSearch(expandSecondarySources: Boolean = false) {
    val q = query.value.trim()
    if (q.isEmpty()) return

    val activeGeneration = searchGenerationId.incrementAndGet()
    searchJob?.cancel()
    paginationJob?.cancel()
    isLoadingMoreResults.value = false
    userToggledGroupInCurrentSearch = false
    expandedGroupIds.value = emptySet()
    isSecondaryLanguagesExpanded.value = false
    isDerivativeWorksExpanded.value = false
    expandedSecondaryEditionGroupIds.value = emptySet()
    exhaustedEditionGroupIds.value = emptySet()
    editionWindowLimits.value = emptyMap()
    loadingMoreEditions.value = emptySet()
    hasPartialFailureWarning.value = false
    hasSearchError.value = false
    selectedEdition.value = null

    searchJob =
      viewModelScope.launch {
        isSearching.value = true
        searchActivityCoordinator?.onSearchStarted()
        try {
          val preferredLang = preferencesRepository.preferences.first().language.tag
          orchestrator
            .searchProgressive(
              rawQuery = q,
              preferredLanguage = preferredLang,
              expandSecondary = expandSecondarySources,
            )
            .collect { partialOrFinal ->
              if (searchGenerationId.get() != activeGeneration) return@collect
              response.value = partialOrFinal
              hasSearched.value = true
              hasSearchError.value = false
              if (!partialOrFinal.isPartialResult) {
                hasPartialFailureWarning.value = false
              }
              localLiveMatches.value = partialOrFinal.localMatches

              // Auto-expand only on completion if there is 1 work group or ISBN lookup and user hasn't clicked yet
              if (!partialOrFinal.isPartialResult && !userToggledGroupInCurrentSearch) {
                val allVisible =
                  partialOrFinal.workGroups
                    .ifEmpty { partialOrFinal.secondaryWorkGroups }
                    .ifEmpty { partialOrFinal.derivativeWorkGroups }
                if (allVisible.size == 1 || partialOrFinal.isIsbnQuery) {
                  expandedGroupIds.value = allVisible.take(1).map { it.groupId }.toSet()
                }
              }
              isSearching.value = partialOrFinal.isPartialResult
            }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Exception) {
          if (searchGenerationId.get() == activeGeneration) {
            val currentResp = response.value
            val hasVisibleResults =
              currentResp != null && (
                currentResp.workGroups.isNotEmpty() ||
                  currentResp.secondaryWorkGroups.isNotEmpty() ||
                  currentResp.derivativeWorkGroups.isNotEmpty() ||
                  currentResp.localMatches.isNotEmpty()
                )
            if (hasVisibleResults) {
              hasPartialFailureWarning.value = true
            } else {
              hasSearchError.value = true
              hasSearched.value = true
            }
          }
        } finally {
          searchActivityCoordinator?.onSearchFinished()
          if (searchGenerationId.get() == activeGeneration) {
            isSearching.value = false
          }
        }
      }
  }

  fun dismissPartialFailureWarning() {
    hasPartialFailureWarning.value = false
  }

  fun loadNextPage() {
    val currentResp = response.value ?: return
    if (isSearching.value || isLoadingMoreResults.value || !currentResp.hasMoreResults) return
    val activeGeneration = searchGenerationId.get()
    paginationJob?.cancel()
    paginationJob =
      viewModelScope.launch {
        isLoadingMoreResults.value = true
        searchActivityCoordinator?.onSearchStarted()
        try {
          val preferredLang = preferencesRepository.preferences.first().language.tag
          val updated =
            orchestrator.loadNextPage(
              currentResponse = currentResp,
              preferredLanguage = preferredLang,
              expandSecondary = currentResp.expandedSecondarySources,
            )
          if (searchGenerationId.get() == activeGeneration) {
            response.value = updated
            hasPartialFailureWarning.value = false
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Exception) {
          if (searchGenerationId.get() == activeGeneration) {
            hasPartialFailureWarning.value = true
          }
        } finally {
          searchActivityCoordinator?.onSearchFinished()
          isLoadingMoreResults.value = false
        }
      }
  }

  fun toggleSecondaryLanguagesExpanded() {
    isSecondaryLanguagesExpanded.value = !isSecondaryLanguagesExpanded.value
  }

  fun toggleDerivativeWorksExpanded() {
    isDerivativeWorksExpanded.value = !isDerivativeWorksExpanded.value
  }

  fun toggleGroupExpanded(groupId: String) {
    userToggledGroupInCurrentSearch = true
    expandedGroupIds.value =
      expandedGroupIds.value.let { current ->
        if (groupId in current) current - groupId else current + groupId
      }
  }

  fun toggleSelectedEdition(candidate: SearchEditionCandidate) {
    selectedEdition.value =
      if (selectedEdition.value?.candidateId == candidate.candidateId) null else candidate
  }

  fun toggleGroupSecondaryEditionsExpanded(groupId: String) {
    expandedSecondaryEditionGroupIds.value =
      expandedSecondaryEditionGroupIds.value.let { current ->
        if (groupId in current) current - groupId else current + groupId
      }
  }

  fun expandLocalEditionsWindow(groupId: String) {
    val current = editionWindowLimits.value
    val nextLimit = (current[groupId] ?: DEFAULT_EDITION_WINDOW_SIZE) + EDITION_WINDOW_INCREMENT
    editionWindowLimits.value = current + (groupId to nextLimit)
  }

  fun loadMoreEditionsForWork(group: SearchWorkGroup) {
    if ((group.externalWorkKey == null && group.canonicalTitle.isBlank()) ||
      group.groupId in loadingMoreEditions.value ||
      group.groupId in exhaustedEditionGroupIds.value
    ) {
      return
    }
    viewModelScope.launch {
      loadingMoreEditions.value = loadingMoreEditions.value + group.groupId
      try {
        val preferredLang = preferencesRepository.preferences.first().language.tag
        val currentQuery = response.value?.query
        val expandedResult = orchestrator.expandWorkEditionsDetailed(group, preferredLang, currentQuery)
        val updatedGroup = expandedResult.group
        if (!expandedResult.hasMoreToLoad) {
          exhaustedEditionGroupIds.value = exhaustedEditionGroupIds.value + group.groupId
        }
        if (updatedGroup.editions.size > group.editions.size) {
          val currentLimits = editionWindowLimits.value
          val prevLimit = currentLimits[group.groupId] ?: DEFAULT_EDITION_WINDOW_SIZE
          if (updatedGroup.editions.size > prevLimit) {
            editionWindowLimits.value = currentLimits + (group.groupId to updatedGroup.editions.size)
          }
        }
        val currentResp = response.value ?: return@launch
        val updatedPrimary =
          currentResp.workGroups.map { existing ->
            if (existing.groupId == group.groupId) updatedGroup else existing
          }
        val updatedSecondary =
          currentResp.secondaryWorkGroups.map { existing ->
            if (existing.groupId == group.groupId) updatedGroup else existing
          }
        val updatedDerivative =
          currentResp.derivativeWorkGroups.map { existing ->
            if (existing.groupId == group.groupId) updatedGroup else existing
          }
        response.value =
          currentResp.copy(
            workGroups = updatedPrimary,
            secondaryWorkGroups = updatedSecondary,
            derivativeWorkGroups = updatedDerivative,
          )
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Exception) {
        // Keep existing work group visible if edition expansion fails unexpectedly
      } finally {
        loadingMoreEditions.value = loadingMoreEditions.value - group.groupId
      }
    }
  }

  companion object {
    const val DEFAULT_EDITION_WINDOW_SIZE = 6
    const val EDITION_WINDOW_INCREMENT = 8
  }

  fun selectEditionCandidate(
    candidate: SearchEditionCandidate,
    onReadyForReview: () -> Unit,
  ) {
    viewModelScope.launch {
      try {
        val chosenTitle = candidate.editionTitle?.takeIf { it.isNotBlank() } ?: candidate.workTitle
        val alternateTitle =
          (candidate.originalTitle?.takeIf { it.isNotBlank() } ?: candidate.workTitle)
            .takeIf { it != chosenTitle }
        val assessment =
          libraryRepository.assessDuplicates(
            isbn13 = candidate.isbn13,
            isbn10 = candidate.isbn10,
            externalWorkKey = candidate.externalWorkKey,
            title = chosenTitle,
            author = candidate.primaryAuthorsText,
            alternateTitle = alternateTitle,
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
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Exception) {
        draftStore.setReviewDraft(
          ReviewDraftPayload(
            mode = BookReviewMode.IMPORTED,
            candidate = candidate,
          )
        )
        onReadyForReview()
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
