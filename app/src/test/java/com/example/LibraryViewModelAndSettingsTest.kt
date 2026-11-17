package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.local.preferences.UserPreferences
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.model.AppLanguage
import com.example.core.model.AppThemeMode
import com.example.core.model.LibrarySortOrder
import com.example.core.model.LibraryViewMode
import com.example.core.model.ReadingStatus
import com.example.core.model.TypographyPreset
import com.example.feature.library.BookDetailUiState
import com.example.feature.library.BookDetailViewModel
import com.example.feature.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakePreferencesRepository(
  initial: UserPreferences = UserPreferences(),
) : PreferencesRepository {
  private val state = MutableStateFlow(initial)
  override val preferences: Flow<UserPreferences> = state

  override suspend fun setLanguage(language: AppLanguage) {
    state.update { it.copy(language = language) }
  }

  override suspend fun setThemeMode(themeMode: AppThemeMode) {
    state.update { it.copy(themeMode = themeMode) }
  }

  override suspend fun setTypographyPreset(preset: TypographyPreset) {
    state.update { it.copy(typographyPreset = preset) }
  }

  override suspend fun setDefaultLibraryViewMode(viewMode: LibraryViewMode) {
    state.update { it.copy(defaultLibraryViewMode = viewMode) }
  }

  override suspend fun setReduceMotion(reduceMotion: Boolean) {
    state.update { it.copy(reduceMotion = reduceMotion) }
  }

  override suspend fun setReaderIdentity(name: String, bio: String) {
    state.update { it.copy(readerName = name.trim(), readerBio = bio.trim()) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryViewModelAndSettingsTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var db: DipeLibrisDatabase
  private lateinit var libraryRepo: RoomLibraryRepository
  private lateinit var diaryRepo: RoomDiaryRepository
  private lateinit var fakePrefs: FakePreferencesRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, DipeLibrisDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    libraryRepo = RoomLibraryRepository(db.bookDao(), db.shelfDao())
    diaryRepo = RoomDiaryRepository(db.diaryDao())
    fakePrefs = FakePreferencesRepository()
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun settingsDefaultViewModeUpdatesLibraryEvenAfterManualOverride() = runTest {
    val viewModel = LibraryViewModel(libraryRepo, diaryRepo, fakePrefs)
    val collector = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

    // Wait for initial Room query emission to populate combine()
    val initial = viewModel.uiState.first { it.viewMode == LibraryViewMode.GRID }
    assertEquals(LibraryViewMode.GRID, initial.viewMode)

    // User temporarily switches view inside Library
    viewModel.setViewMode(LibraryViewMode.LIST)
    val overridden = viewModel.uiState.first { it.viewMode == LibraryViewMode.LIST }
    assertEquals(LibraryViewMode.LIST, overridden.viewMode)

    // User goes to Settings and changes default view mode to SHELF
    fakePrefs.setDefaultLibraryViewMode(LibraryViewMode.SHELF)
    val syncedShelf = viewModel.uiState.first { it.viewMode == LibraryViewMode.SHELF }
    assertEquals(LibraryViewMode.SHELF, syncedShelf.viewMode)

    // User changes default view mode back to GRID in Settings
    fakePrefs.setDefaultLibraryViewMode(LibraryViewMode.GRID)
    val syncedGrid = viewModel.uiState.first { it.viewMode == LibraryViewMode.GRID }
    assertEquals(LibraryViewMode.GRID, syncedGrid.viewMode)

    collector.cancel()
  }

  @Test
  fun sortingByTitleAndAuthorRespectsAccentsAndLocale() = runTest {
    fakePrefs.setLanguage(AppLanguage.PORTUGUESE_BR)
    libraryRepo.addBook("Zéfiro", "Machado de Assis", 200, 1950, null, ReadingStatus.UNREAD)
    libraryRepo.addBook("Água Viva", "Clarice Lispector", 96, 1973, null, ReadingStatus.UNREAD)
    libraryRepo.addBook("Borges e os Outros", "Alfonso Reyes", 150, 1960, null, ReadingStatus.UNREAD)
    libraryRepo.addBook("Amado", "Érico Veríssimo", 88, 1977, null, ReadingStatus.UNREAD)

    val viewModel = LibraryViewModel(libraryRepo, diaryRepo, fakePrefs)
    val collector = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

    viewModel.setSortOrder(LibrarySortOrder.TITLE_ASC)
    val sortedByTitle =
      viewModel.uiState.first {
        it.books.size == 4 && it.sortOrder == LibrarySortOrder.TITLE_ASC
      }
    assertEquals(
      listOf("Água Viva", "Amado", "Borges e os Outros", "Zéfiro"),
      sortedByTitle.books.map { it.title },
    )

    viewModel.setSortOrder(LibrarySortOrder.AUTHOR_ASC)
    val sortedByAuthor =
      viewModel.uiState.first {
        it.books.size == 4 && it.sortOrder == LibrarySortOrder.AUTHOR_ASC
      }
    assertEquals(
      listOf("Alfonso Reyes", "Clarice Lispector", "Érico Veríssimo", "Machado de Assis"),
      sortedByAuthor.books.map { it.author },
    )

    collector.cancel()
  }

  @Test
  fun bookDetailViewModelDistinguishesLoadedAndNotFound() = runTest {
    val workId =
      libraryRepo.addBook(
        title = "Vidas Secas",
        author = "Graciliano Ramos",
        totalPages = 176,
        publicationYear = 1938,
        editionNote = null,
        status = ReadingStatus.READING,
      )

    val detailVm = BookDetailViewModel(workId, libraryRepo, diaryRepo)
    val collector = backgroundScope.launch(testDispatcher) { detailVm.uiState.collect {} }

    val loaded = detailVm.uiState.first { it is BookDetailUiState.Loaded } as BookDetailUiState.Loaded
    assertEquals("Vidas Secas", loaded.book.title)

    val deletedSignal = kotlinx.coroutines.CompletableDeferred<Boolean>()
    detailVm.deleteBook { deletedSignal.complete(true) }
    assertTrue(deletedSignal.await())

    val afterDelete = detailVm.uiState.first { it is BookDetailUiState.NotFound }
    assertEquals(BookDetailUiState.NotFound, afterDelete)

    collector.cancel()

    val missingVm = BookDetailViewModel(999_999L, libraryRepo, diaryRepo)
    val missingCollector = backgroundScope.launch(testDispatcher) { missingVm.uiState.collect {} }
    val notFound = missingVm.uiState.first { it is BookDetailUiState.NotFound }
    assertEquals(BookDetailUiState.NotFound, notFound)
    missingCollector.cancel()
  }
}
