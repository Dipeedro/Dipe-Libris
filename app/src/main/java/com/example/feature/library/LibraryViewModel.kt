package com.example.feature.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.LibraryUpdateCheckSummary
import com.example.core.data.repository.MetadataUpdateRepository
import com.example.core.model.AppLanguage
import com.example.core.model.BibliographicMath
import com.example.core.model.CatalogBook
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
import com.example.core.model.Genre
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus
import java.text.Collator
import java.util.EnumMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibraryUiState(
  val books: List<CatalogBook> = emptyList(),
  val totalCatalogCount: Int = 0,
  val searchQuery: String = "",
  val statusFilter: ReadingStatus? = null,
  val genreFilter: Long? = null,
  val availableGenres: List<Genre> = emptyList(),
  val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
  val viewMode: LibraryViewMode = LibraryViewMode.GRID,
  val isCheckingUpdates: Boolean = false,
  val totalPendingSuggestionsCount: Int = 0,
)

private data class ViewModeOverride(
  val mode: LibraryViewMode,
  val baseDefault: LibraryViewMode,
)

private val collatorCache =
  object : ThreadLocal<EnumMap<AppLanguage, Collator>>() {
    override fun initialValue(): EnumMap<AppLanguage, Collator> =
      EnumMap(AppLanguage::class.java)
  }

private fun collatorFor(language: AppLanguage): Collator {
  val map = collatorCache.get()!!
  return map.getOrPut(language) {
    Collator.getInstance(language.toLocale()).apply { strength = Collator.SECONDARY }
  }
}

class LibraryViewModel(
  private val libraryRepository: LibraryRepository,
  private val preferencesRepository: PreferencesRepository,
  private val coverStorageManager: CoverStorageManager? = null,
  private val metadataUpdateRepository: MetadataUpdateRepository? = null,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val statusFilter = MutableStateFlow<ReadingStatus?>(null)
  private val genreFilter = MutableStateFlow<Long?>(null)
  private val sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_ADDED)
  private val activeViewModeOverride = MutableStateFlow<ViewModeOverride?>(null)
  private val latestDefaultViewMode = MutableStateFlow(LibraryViewMode.GRID)
  private val isCheckingUpdates = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      preferencesRepository.preferences
        .map { it.defaultLibraryViewMode }
        .distinctUntilChanged()
        .drop(1)
        .collect { newDefault ->
          latestDefaultViewMode.value = newDefault
          activeViewModeOverride.value = null
        }
    }
  }

  private data class FilterSortPrefsTuple(
    val query: String,
    val status: ReadingStatus?,
    val genreId: Long?,
    val sort: LibrarySortOrder,
    val langAndMode: Pair<AppLanguage, LibraryViewMode>,
  )

  private val filterSortPrefsFlow =
    combine(
      searchQuery,
      statusFilter,
      genreFilter,
      sortOrder,
      combine(preferencesRepository.preferences, activeViewModeOverride) { prefs, override ->
          latestDefaultViewMode.value = prefs.defaultLibraryViewMode
          val effectiveViewMode =
            if (override != null && override.baseDefault == prefs.defaultLibraryViewMode) {
              override.mode
            } else {
              prefs.defaultLibraryViewMode
            }
          prefs.language to effectiveViewMode
        }
        .distinctUntilChanged(),
    ) { q, st, gen, sort, lm -> FilterSortPrefsTuple(q, st, gen, sort, lm) }

  val uiState: StateFlow<LibraryUiState> =
    combine(
        libraryRepository.observeAllBooks(),
        libraryRepository.observeGenres(),
        filterSortPrefsFlow,
        isCheckingUpdates,
      ) { allBooks, genres, fsp, checkingUpdates ->
        val language = fsp.langAndMode.first
        val viewMode = fsp.langAndMode.second
        val query = fsp.query
        val filter = fsp.status
        val selectedGenreId = fsp.genreId
        val sort = fsp.sort

        val normalizedQuery = BibliographicMath.normalizeText(query)
        val cleanedIsbnQuery = BibliographicMath.cleanIsbn(query)

        val filtered =
          allBooks.filter { book ->
            val matchesStatus = filter == null || book.status == filter
            val matchesGenre = selectedGenreId == null || book.work.primaryGenreId == selectedGenreId
            val matchesQuery =
              if (normalizedQuery.isEmpty() && cleanedIsbnQuery.isEmpty()) {
                true
              } else {
                val haystack =
                  BibliographicMath.normalizeText(
                    listOfNotNull(
                      book.title,
                      book.work.subtitle,
                      book.author,
                      book.primaryEdition?.publisherOrNote,
                      book.primaryEdition?.translator,
                      book.primaryEdition?.seriesName,
                      book.primaryGenre?.namePt,
                      book.primaryGenre?.nameEn,
                    ).joinToString(" ")
                  )
                val isbnText =
                  listOfNotNull(
                    book.primaryEdition?.isbn13,
                    book.primaryEdition?.isbn10,
                    book.primaryEdition?.isbn,
                  ).joinToString(" ")
                (normalizedQuery.isNotEmpty() && haystack.contains(normalizedQuery)) ||
                  (cleanedIsbnQuery.length >= 4 && isbnText.contains(cleanedIsbnQuery))
              }
            matchesStatus && matchesGenre && matchesQuery
          }

        val sorted =
          when (sort) {
            LibrarySortOrder.RECENTLY_ADDED ->
              filtered.sortedByDescending { it.work.createdAtEpochMillis }
            LibrarySortOrder.TITLE_ASC -> {
              val collator = collatorFor(language)
              filtered.sortedWith { a, b ->
                val primary = collator.compare(a.title.trim(), b.title.trim())
                if (primary != 0) primary else a.title.compareTo(b.title, ignoreCase = true)
              }
            }
            LibrarySortOrder.AUTHOR_ASC -> {
              val collator = collatorFor(language)
              filtered.sortedWith { a, b ->
                val byAuthor = collator.compare(a.author.trim(), b.author.trim())
                if (byAuthor != 0) byAuthor else collator.compare(a.title.trim(), b.title.trim())
              }
            }
          }

        val usedGenreIds = allBooks.mapNotNull { it.work.primaryGenreId }.toSet()
        val displayGenres = genres.filter { it.id in usedGenreIds || it.id == selectedGenreId }

        LibraryUiState(
          books = sorted,
          totalCatalogCount = allBooks.size,
          searchQuery = query,
          statusFilter = filter,
          genreFilter = selectedGenreId,
          availableGenres = displayGenres,
          sortOrder = sort,
          viewMode = viewMode,
          isCheckingUpdates = checkingUpdates,
          totalPendingSuggestionsCount = allBooks.sumOf { it.pendingSuggestionsCount },
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
      )

  fun setSearchQuery(value: String) {
    searchQuery.value = value
  }

  fun setStatusFilter(status: ReadingStatus?) {
    statusFilter.value = status
  }

  fun setGenreFilter(genreId: Long?) {
    genreFilter.value = if (genreFilter.value == genreId) null else genreId
  }

  fun setSortOrder(order: LibrarySortOrder) {
    sortOrder.value = order
  }

  fun setViewMode(mode: LibraryViewMode) {
    activeViewModeOverride.value =
      ViewModeOverride(mode = mode, baseDefault = latestDefaultViewMode.value)
  }

  fun checkLibraryMetadataUpdates(onCompleted: (LibraryUpdateCheckSummary) -> Unit = {}) {
    val updater = metadataUpdateRepository ?: return
    if (isCheckingUpdates.value) return
    viewModelScope.launch {
      isCheckingUpdates.value = true
      try {
        val preferredLang = preferencesRepository.preferences.first().language.tag
        val summary = updater.checkUpdatesForLibrary(preferredLang)
        onCompleted(summary)
      } finally {
        isCheckingUpdates.value = false
      }
    }
  }

  fun addBook(
    title: String,
    author: String,
    totalPages: Int?,
    publicationYear: Int?,
    editionNote: String?,
    status: ReadingStatus,
    isbn: String? = null,
    pickedCoverUri: Uri? = null,
    language: String? = null,
    format: EditionFormat? = null,
    translator: String? = null,
    copyMediaType: CopyMediaType = CopyMediaType.PHYSICAL,
    copyLabel: String? = null,
    copyLocation: String? = null,
    onCompleted: (Long) -> Unit = {},
  ) {
    viewModelScope.launch {
      val importedCoverPath =
        pickedCoverUri?.let { uri -> coverStorageManager?.importCoverFromUri(uri) }
      val id =
        libraryRepository.addBook(
          title = title,
          author = author,
          totalPages = totalPages,
          publicationYear = publicationYear,
          editionNote = editionNote,
          status = status,
          isbn = isbn,
          coverUri = importedCoverPath,
          language = language,
          format = format,
          translator = translator,
          copyMediaType = copyMediaType,
          copyLabel = copyLabel,
          copyLocation = copyLocation,
        )
      onCompleted(id)
    }
  }

  fun toggleFavorite(book: CatalogBook) {
    viewModelScope.launch { libraryRepository.toggleFavorite(book.id, book.isFavorite) }
  }

  fun updateStatus(workId: Long, status: ReadingStatus) {
    viewModelScope.launch { libraryRepository.updateReadingStatus(workId, status) }
  }

  fun deleteBook(workId: Long, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      libraryRepository.deleteBook(workId)
      onDeleted()
    }
  }
}
