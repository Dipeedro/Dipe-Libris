package com.example.feature.library

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.model.AppLanguage
import com.example.core.model.CatalogBook
import com.example.core.model.CopyMediaType
import com.example.core.model.EditionFormat
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
  val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_ADDED,
  val viewMode: LibraryViewMode = LibraryViewMode.GRID,
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
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val statusFilter = MutableStateFlow<ReadingStatus?>(null)
  private val sortOrder = MutableStateFlow(LibrarySortOrder.RECENTLY_ADDED)
  private val activeViewModeOverride = MutableStateFlow<ViewModeOverride?>(null)
  private val latestDefaultViewMode = MutableStateFlow(LibraryViewMode.GRID)

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

  val uiState: StateFlow<LibraryUiState> =
    combine(
        libraryRepository.observeAllBooks(),
        searchQuery,
        statusFilter,
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
      ) { allBooks, query, filter, sort, (language, viewMode) ->
        val locale = language.toLocale()
        val normalizedQuery = query.trim().lowercase(locale)
        val filtered =
          if (filter == null && normalizedQuery.isEmpty()) {
            allBooks
          } else {
            allBooks.filter { book ->
              val matchesStatus = filter == null || book.status == filter
              val matchesQuery =
                normalizedQuery.isEmpty() ||
                  book.title.lowercase(locale).contains(normalizedQuery) ||
                  book.author.lowercase(locale).contains(normalizedQuery) ||
                  (book.primaryEdition?.isbn?.lowercase(locale)?.contains(normalizedQuery) == true)
              matchesStatus && matchesQuery
            }
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

        LibraryUiState(
          books = sorted,
          totalCatalogCount = allBooks.size,
          searchQuery = query,
          statusFilter = filter,
          sortOrder = sort,
          viewMode = viewMode,
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

  fun setSortOrder(order: LibrarySortOrder) {
    sortOrder.value = order
  }

  fun setViewMode(mode: LibraryViewMode) {
    activeViewModeOverride.value =
      ViewModeOverride(mode = mode, baseDefault = latestDefaultViewMode.value)
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
