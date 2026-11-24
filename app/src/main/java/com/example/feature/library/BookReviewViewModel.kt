package com.example.feature.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.bibliographic.domain.GenreSuggester
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.di.BibliographicDraftStore
import com.example.core.di.BookReviewMode
import com.example.core.di.ReviewDraftPayload
import com.example.core.model.BibliographicMath
import com.example.core.model.BookReviewSubmission
import com.example.core.model.CandidateContributor
import com.example.core.model.ContributorRole
import com.example.core.model.CopyCondition
import com.example.core.model.CopyMediaType
import com.example.core.model.CopyOrigin
import com.example.core.model.CoverCandidate
import com.example.core.model.EditionFormat
import com.example.core.model.Genre
import com.example.core.model.ReadingStatus
import com.example.core.model.SynopsisCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ReviewSection {
  WORK_CONTRIBUTORS,
  COVER,
  EDITION,
  GENRE,
  SYNOPSIS,
  COPY,
}

@Immutable
data class EditableContributorRow(
  val localKey: Int,
  val name: String,
  val role: ContributorRole,
)

@Immutable
data class BookReviewUiState(
  val mode: BookReviewMode = BookReviewMode.MANUAL,
  val targetWorkId: Long? = null,
  val targetEditionId: Long? = null,
  val expandedSections: Set<ReviewSection> = ReviewSection.entries.toSet(),
  val collectorDetailsExpanded: Boolean = false,
  val title: String = "",
  val subtitle: String = "",
  val originalTitle: String = "",
  val contributors: List<EditableContributorRow> = listOf(EditableContributorRow(1, "", ContributorRole.AUTHOR)),
  val coverCandidates: List<CoverCandidate> = emptyList(),
  val selectedRemoteCoverUrl: String? = null,
  val selectedLocalCoverUri: Uri? = null,
  val coverSource: String? = null,
  val isCoverCustomized: Boolean = false,
  val useTypographicCover: Boolean = false,
  val publisher: String = "",
  val publicationYear: String = "",
  val totalPages: String = "",
  val isbn13: String = "",
  val isbn10: String = "",
  val language: String = "",
  val editionLabel: String = "",
  val format: EditionFormat? = null,
  val seriesName: String = "",
  val seriesVolume: String = "",
  val externalWorkKey: String? = null,
  val externalIdsJson: String? = null,
  val availableGenres: List<Genre> = emptyList(),
  val suggestedGenreId: Long? = null,
  val selectedGenreId: Long? = null,
  val secondaryCategories: List<String> = emptyList(),
  val synopsis: String = "",
  val synopsisLanguage: String? = null,
  val synopsisSource: String? = null,
  val isSynopsisCustomized: Boolean = false,
  val synopsisCandidates: List<SynopsisCandidate> = emptyList(),
  val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
  val copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
  val copyLabel: String = "",
  val copyPhysicalFormat: String = "",
  val copyLocation: String = "",
  val copyCondition: CopyCondition? = null,
  val copyOrigin: CopyOrigin? = null,
  val copyAcquisitionEpochMillis: Long? = null,
  val copyPriceText: String = "",
  val copyCustomCode: String = "",
  val copyPersonalNote: String = "",
  val validationErrorRes: Int? = null,
  val isSaving: Boolean = false,
)

class BookReviewViewModel(
  modeArg: String,
  targetWorkIdArg: Long?,
  prefillArg: String?,
  private val libraryRepository: LibraryRepository,
  private val coverStorageManager: CoverStorageManager,
  private val preferencesRepository: PreferencesRepository,
  draftStore: BibliographicDraftStore,
) : ViewModel() {

  private var nextContributorKey = 10
  private val draftState: MutableStateFlow<BookReviewUiState>

  init {
    val payload: ReviewDraftPayload =
      draftStore.consumeReviewDraft()
        ?: ReviewDraftPayload(
          mode =
            when (modeArg.lowercase()) {
              "imported" -> BookReviewMode.IMPORTED
              "add_edition" -> BookReviewMode.ADD_EDITION_TO_WORK
              "add_copy" -> BookReviewMode.ADD_COPY_TO_EDITION
              else -> BookReviewMode.MANUAL
            },
          targetWorkId = targetWorkIdArg?.takeIf { it > 0L },
          prefillTitleOrIsbn = prefillArg,
        )

    draftState = MutableStateFlow(buildInitialReviewState(payload))
    nextContributorKey = (draftState.value.contributors.maxOfOrNull { it.localKey } ?: 1) + 5

    viewModelScope.launch {
      val genres = libraryRepository.observeGenres().first()
      val categories = payload.candidate?.categories.orEmpty()
      val matched = GenreSuggester.matchGenre(categories, genres, draftState.value.title)
      if (matched != null) {
        draftState.value =
          draftState.value.copy(
            suggestedGenreId = matched.id,
            selectedGenreId = draftState.value.selectedGenreId ?: matched.id,
          )
      }
      if (payload.candidate == null && payload.mode == BookReviewMode.MANUAL) {
        val langTag = preferencesRepository.preferences.first().language.tag
        if (draftState.value.language.isBlank()) {
          draftState.value =
            draftState.value.copy(
              language = if (langTag.startsWith("pt")) "pt" else "en",
            )
        }
      }
    }
  }

  val uiState: StateFlow<BookReviewUiState> =
    combine(draftState, libraryRepository.observeGenres()) { state, genres ->
        state.copy(availableGenres = genres)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = draftState.value,
      )

  fun toggleSection(section: ReviewSection) {
    val current = draftState.value.expandedSections
    draftState.value =
      draftState.value.copy(
        expandedSections = if (section in current) current - section else current + section,
      )
  }

  fun toggleCollectorDetails() {
    draftState.value =
      draftState.value.copy(collectorDetailsExpanded = !draftState.value.collectorDetailsExpanded)
  }

  fun setTitle(value: String) {
    draftState.value = draftState.value.copy(title = value, validationErrorRes = null)
  }

  fun setSubtitle(value: String) {
    draftState.value = draftState.value.copy(subtitle = value)
  }

  fun setOriginalTitle(value: String) {
    draftState.value = draftState.value.copy(originalTitle = value)
  }

  fun addContributorRow() {
    val newKey = nextContributorKey++
    draftState.value =
      draftState.value.copy(
        contributors =
          draftState.value.contributors +
            EditableContributorRow(localKey = newKey, name = "", role = ContributorRole.CO_AUTHOR),
      )
  }

  fun updateContributorName(localKey: Int, name: String) {
    draftState.value =
      draftState.value.copy(
        contributors =
          draftState.value.contributors.map { row ->
            if (row.localKey == localKey) row.copy(name = name) else row
          },
        validationErrorRes = null,
      )
  }

  fun updateContributorRole(localKey: Int, role: ContributorRole) {
    draftState.value =
      draftState.value.copy(
        contributors =
          draftState.value.contributors.map { row ->
            if (row.localKey == localKey) row.copy(role = role) else row
          },
      )
  }

  fun removeContributorRow(localKey: Int) {
    if (draftState.value.contributors.size <= 1) return
    draftState.value =
      draftState.value.copy(
        contributors = draftState.value.contributors.filterNot { it.localKey == localKey },
      )
  }

  fun selectRemoteCover(candidate: CoverCandidate) {
    draftState.value =
      draftState.value.copy(
        selectedRemoteCoverUrl = candidate.imageUrl,
        selectedLocalCoverUri = null,
        coverSource = candidate.source.name,
        isCoverCustomized = true,
        useTypographicCover = false,
      )
  }

  fun selectLocalCover(uri: Uri) {
    draftState.value =
      draftState.value.copy(
        selectedLocalCoverUri = uri,
        selectedRemoteCoverUrl = null,
        coverSource = "USER",
        isCoverCustomized = true,
        useTypographicCover = false,
      )
  }

  fun chooseTypographicCover() {
    draftState.value =
      draftState.value.copy(
        selectedLocalCoverUri = null,
        selectedRemoteCoverUrl = null,
        coverSource = null,
        isCoverCustomized = true,
        useTypographicCover = true,
      )
  }

  fun setPublisher(value: String) {
    draftState.value = draftState.value.copy(publisher = value)
  }

  fun setPublicationYear(value: String) {
    draftState.value =
      draftState.value.copy(
        publicationYear = value.filter { it.isDigit() }.take(4),
        validationErrorRes = null,
      )
  }

  fun setTotalPages(value: String) {
    draftState.value =
      draftState.value.copy(
        totalPages = value.filter { it.isDigit() }.take(6),
        validationErrorRes = null,
      )
  }

  fun setIsbn13(value: String) {
    draftState.value = draftState.value.copy(isbn13 = value, validationErrorRes = null)
  }

  fun setIsbn10(value: String) {
    draftState.value = draftState.value.copy(isbn10 = value, validationErrorRes = null)
  }

  fun setLanguage(value: String) {
    draftState.value = draftState.value.copy(language = value)
  }

  fun setEditionLabel(value: String) {
    draftState.value = draftState.value.copy(editionLabel = value)
  }

  fun toggleFormat(format: EditionFormat) {
    draftState.value =
      draftState.value.copy(format = if (draftState.value.format == format) null else format)
  }

  fun setSeriesName(value: String) {
    draftState.value = draftState.value.copy(seriesName = value)
  }

  fun setSeriesVolume(value: String) {
    draftState.value = draftState.value.copy(seriesVolume = value)
  }

  fun selectPrimaryGenre(genreId: Long) {
    val current = draftState.value.selectedGenreId
    draftState.value = draftState.value.copy(selectedGenreId = if (current == genreId) null else genreId)
  }

  fun createCustomGenre(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      val created = libraryRepository.createCustomGenre(trimmed)
      draftState.value = draftState.value.copy(selectedGenreId = created.id)
    }
  }

  fun setSynopsis(value: String) {
    draftState.value =
      draftState.value.copy(
        synopsis = value,
        isSynopsisCustomized = true,
        synopsisSource = "USER",
      )
  }

  fun selectAlternativeSynopsis(candidate: SynopsisCandidate) {
    draftState.value =
      draftState.value.copy(
        synopsis = candidate.text,
        synopsisLanguage = candidate.language,
        synopsisSource = candidate.source.name,
        isSynopsisCustomized = false,
      )
  }

  fun setReadingStatus(status: ReadingStatus) {
    draftState.value = draftState.value.copy(readingStatus = status)
  }

  fun setCopyMediaType(type: CopyMediaType) {
    draftState.value = draftState.value.copy(copyMediaType = type)
  }

  fun setCopyLabel(value: String) {
    draftState.value = draftState.value.copy(copyLabel = value)
  }

  fun setCopyPhysicalFormat(value: String) {
    draftState.value = draftState.value.copy(copyPhysicalFormat = value)
  }

  fun setCopyLocation(value: String) {
    draftState.value = draftState.value.copy(copyLocation = value)
  }

  fun toggleCopyCondition(condition: CopyCondition) {
    draftState.value =
      draftState.value.copy(
        copyCondition = if (draftState.value.copyCondition == condition) null else condition,
      )
  }

  fun toggleCopyOrigin(origin: CopyOrigin) {
    draftState.value =
      draftState.value.copy(
        copyOrigin = if (draftState.value.copyOrigin == origin) null else origin,
      )
  }

  fun setCopyAcquiredToday() {
    draftState.value = draftState.value.copy(copyAcquisitionEpochMillis = System.currentTimeMillis())
  }

  fun clearCopyAcquiredDate() {
    draftState.value = draftState.value.copy(copyAcquisitionEpochMillis = null)
  }

  fun setCopyPriceText(value: String) {
    draftState.value = draftState.value.copy(copyPriceText = value)
  }

  fun setCopyCustomCode(value: String) {
    draftState.value = draftState.value.copy(copyCustomCode = value)
  }

  fun setCopyPersonalNote(value: String) {
    draftState.value = draftState.value.copy(copyPersonalNote = value)
  }

  fun save(onSavedWork: (Long) -> Unit) {
    val state = draftState.value
    if (state.isSaving) return

    val validContributors =
      state.contributors
        .mapNotNull { row ->
          val trimmed = row.name.trim()
          if (trimmed.isEmpty()) null else CandidateContributor(trimmed, row.role)
        }

    if (state.mode != BookReviewMode.ADD_COPY_TO_EDITION) {
      if (state.title.isBlank() || validContributors.isEmpty()) {
        draftState.value =
          state.copy(
            expandedSections = state.expandedSections + ReviewSection.WORK_CONTRIBUTORS,
            validationErrorRes = R.string.book_validation_required,
          )
        return
      }
    }

    val parsedPages =
      if (state.totalPages.isBlank()) null
      else state.totalPages.toIntOrNull()?.takeIf { it in 1..50_000 }
    if (state.totalPages.isNotBlank() && parsedPages == null) {
      draftState.value =
        state.copy(
          expandedSections = state.expandedSections + ReviewSection.EDITION,
          validationErrorRes = R.string.book_validation_pages,
        )
      return
    }

    val parsedYear =
      if (state.publicationYear.isBlank()) null
      else state.publicationYear.toIntOrNull()?.takeIf { it in 1..2100 }
    if (state.publicationYear.isNotBlank() && parsedYear == null) {
      draftState.value =
        state.copy(
          expandedSections = state.expandedSections + ReviewSection.EDITION,
          validationErrorRes = R.string.book_validation_year,
        )
      return
    }

    val raw13 = state.isbn13.trim()
    val parsed13 = if (raw13.isEmpty()) null else BibliographicMath.parseIsbn(raw13)
    if (raw13.isNotEmpty() && parsed13?.isbn13 == null) {
      draftState.value =
        state.copy(
          expandedSections = state.expandedSections + ReviewSection.EDITION,
          validationErrorRes = R.string.review_validation_isbn13,
        )
      return
    }

    val raw10 = state.isbn10.trim()
    val parsed10 = if (raw10.isEmpty()) null else BibliographicMath.parseIsbn(raw10)
    if (raw10.isNotEmpty() && parsed10?.isbn10 == null) {
      draftState.value =
        state.copy(
          expandedSections = state.expandedSections + ReviewSection.EDITION,
          validationErrorRes = R.string.review_validation_isbn10,
        )
      return
    }

    val parsedPriceMinor =
      state.copyPriceText
        .trim()
        .replace(",", ".")
        .toDoubleOrNull()
        ?.let { Math.round(it * 100.0) }
        ?.takeIf { it >= 0L }

    viewModelScope.launch {
      draftState.value = state.copy(isSaving = true, validationErrorRes = null)
      try {
        val importedLocalCoverPath =
          if (!state.useTypographicCover && state.selectedLocalCoverUri != null) {
            coverStorageManager.importCoverFromUri(state.selectedLocalCoverUri)
          } else {
            null
          }

        val effective13 = parsed13?.isbn13 ?: parsed10?.isbn13
        val effective10 = parsed10?.isbn10 ?: parsed13?.isbn10

        val submission =
          BookReviewSubmission(
            existingWorkId = state.targetWorkId,
            title = state.title.trim(),
            subtitle = state.subtitle.trim().takeIf { it.isNotEmpty() },
            originalTitle = state.originalTitle.trim().takeIf { it.isNotEmpty() },
            contributors = validContributors,
            primaryGenreId = state.selectedGenreId,
            activeSynopsis = state.synopsis.trim().takeIf { it.isNotEmpty() },
            synopsisLanguage = state.synopsisLanguage,
            synopsisSource = state.synopsisSource,
            isSynopsisCustomized = state.isSynopsisCustomized,
            externalWorkKey = state.externalWorkKey,
            status = state.readingStatus,
            publisherOrNote = state.publisher.trim().takeIf { it.isNotEmpty() },
            publicationYear = parsedYear,
            totalPages = parsedPages,
            isbn10 = effective10,
            isbn13 = effective13,
            language = state.language.trim().takeIf { it.isNotEmpty() },
            format = state.format,
            editionLabel = state.editionLabel.trim().takeIf { it.isNotEmpty() },
            seriesName = state.seriesName.trim().takeIf { it.isNotEmpty() },
            seriesVolume = state.seriesVolume.trim().takeIf { it.isNotEmpty() },
            categories = state.secondaryCategories,
            externalIdsJson = state.externalIdsJson,
            selectedRemoteCoverUrl =
              if (state.useTypographicCover) null else state.selectedRemoteCoverUrl,
            fallbackRemoteCoverUrls =
              if (state.useTypographicCover || state.isCoverCustomized) emptyList()
              else state.coverCandidates.map { it.imageUrl }.filter { it != state.selectedRemoteCoverUrl },
            selectedLocalCoverUri = importedLocalCoverPath,
            coverSource = state.coverSource,
            isCoverCustomized = state.isCoverCustomized,
            copyMediaType = state.copyMediaType,
            copyLabel = state.copyLabel.trim().takeIf { it.isNotEmpty() },
            copyPhysicalFormat = state.copyPhysicalFormat.trim().takeIf { it.isNotEmpty() },
            copyLocationOrPlatform = state.copyLocation.trim().takeIf { it.isNotEmpty() },
            copyCondition = state.copyCondition,
            copyOrigin = state.copyOrigin,
            copyAcquisitionEpochMillis = state.copyAcquisitionEpochMillis,
            copyPriceMinorUnits = parsedPriceMinor,
            copyCurrencyCode = if (parsedPriceMinor != null) "BRL" else null,
            copyCustomCode = state.copyCustomCode.trim().takeIf { it.isNotEmpty() },
            copyPersonalNote = state.copyPersonalNote.trim().takeIf { it.isNotEmpty() },
          )

        val resultWorkId =
          when (state.mode) {
            BookReviewMode.ADD_COPY_TO_EDITION -> {
              val edId = state.targetEditionId ?: error("targetEditionId required")
              libraryRepository.addCopyFromReview(edId, submission)
              state.targetWorkId ?: error("targetWorkId required")
            }
            BookReviewMode.ADD_EDITION_TO_WORK -> {
              val wId = state.targetWorkId ?: error("targetWorkId required")
              libraryRepository.addEditionFromReview(wId, submission)
              wId
            }
            BookReviewMode.IMPORTED,
            BookReviewMode.MANUAL -> libraryRepository.saveBookFromReview(submission)
          }
        onSavedWork(resultWorkId)
      } finally {
        draftState.value = draftState.value.copy(isSaving = false)
      }
    }
  }
}

internal fun buildInitialReviewState(payload: ReviewDraftPayload): BookReviewUiState {
  val candidate = payload.candidate
  if (candidate != null) {
    val contribRows =
      if (candidate.contributors.isNotEmpty()) {
        candidate.contributors.mapIndexed { idx, c ->
          EditableContributorRow(localKey = idx + 1, name = c.name, role = c.role)
        }
      } else {
        listOf(EditableContributorRow(1, "", ContributorRole.AUTHOR))
      }
    val bestCover = candidate.bestCover
    val bestSynopsis = candidate.bestSynopsis
    val extIdsJson =
      candidate.externalIds.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) }
    val resolvedTitle =
      candidate.editionTitle?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.workTitle
    val resolvedTitleNorm = BibliographicMath.normalizeBaseTitle(resolvedTitle)
    val resolvedOriginalTitle =
      candidate.originalTitle
        ?.trim()
        ?.takeIf { it.isNotEmpty() && BibliographicMath.normalizeBaseTitle(it) != resolvedTitleNorm }
        ?: candidate.workTitle
          .trim()
          .takeIf { it.isNotEmpty() && BibliographicMath.normalizeBaseTitle(it) != resolvedTitleNorm }
          .orEmpty()

    return BookReviewUiState(
      mode = payload.mode,
      targetWorkId = payload.targetWorkId,
      targetEditionId = payload.targetEditionId,
      title = resolvedTitle,
      subtitle = candidate.subtitle.orEmpty(),
      originalTitle = resolvedOriginalTitle,
      contributors = contribRows,
      coverCandidates = candidate.coverCandidates,
      selectedRemoteCoverUrl = bestCover?.imageUrl,
      coverSource = bestCover?.source?.name,
      useTypographicCover = bestCover == null,
      publisher = candidate.publisher.orEmpty(),
      publicationYear = candidate.publicationYear?.toString().orEmpty(),
      totalPages = candidate.totalPages?.toString().orEmpty(),
      isbn13 = candidate.isbn13.orEmpty(),
      isbn10 = candidate.isbn10.orEmpty(),
      language = candidate.language.orEmpty(),
      editionLabel = candidate.editionLabel.orEmpty(),
      format = candidate.format,
      seriesName = candidate.seriesName.orEmpty(),
      seriesVolume = candidate.seriesVolume.orEmpty(),
      externalWorkKey = candidate.externalWorkKey,
      externalIdsJson = extIdsJson,
      secondaryCategories = candidate.categories,
      synopsis = bestSynopsis?.text.orEmpty(),
      synopsisLanguage = bestSynopsis?.language,
      synopsisSource = bestSynopsis?.source?.name,
      synopsisCandidates = candidate.synopsisCandidates,
    )
  }

  val prefill = payload.prefillTitleOrIsbn?.trim().orEmpty()
  val parsedPrefillIsbn = if (prefill.isNotEmpty()) BibliographicMath.parseIsbn(prefill) else null
  return BookReviewUiState(
    mode = payload.mode,
    targetWorkId = payload.targetWorkId,
    targetEditionId = payload.targetEditionId,
    title = if (parsedPrefillIsbn == null) prefill else "",
    isbn13 = parsedPrefillIsbn?.isbn13.orEmpty(),
    isbn10 = parsedPrefillIsbn?.isbn10.orEmpty(),
    useTypographicCover = true,
  )
}
