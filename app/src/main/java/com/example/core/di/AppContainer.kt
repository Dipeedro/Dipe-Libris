package com.example.core.di

import android.content.Context
import com.example.core.bibliographic.domain.HybridSearchOrchestrator
import com.example.core.bibliographic.network.AndroidConnectivityChecker
import com.example.core.bibliographic.network.AppleBooksAdapter
import com.example.core.bibliographic.network.BrasilApiAdapter
import com.example.core.bibliographic.network.ConnectivityChecker
import com.example.core.bibliographic.network.CrossrefAdapter
import com.example.core.bibliographic.network.GoogleBooksAdapter
import com.example.core.bibliographic.network.OpenLibraryAdapter
import com.example.core.bibliographic.network.WikidataAdapter
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.local.preferences.DataStorePreferencesRepository
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.DefaultMetadataUpdateRepository
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.MetadataUpdateRepository
import com.example.core.data.repository.ReadingJourneyRepository
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomReadingJourneyRepository
import com.example.core.data.repository.RoomStatisticsRepository
import com.example.core.data.repository.StatisticsRepository

interface AppContainer {
  val preferencesRepository: PreferencesRepository
  val coverStorageManager: CoverStorageManager
  val connectivityChecker: ConnectivityChecker
  val libraryRepository: LibraryRepository
  val readingJourneyRepository: ReadingJourneyRepository
  val diaryRepository: DiaryRepository
  val statisticsRepository: StatisticsRepository
  val hybridSearchOrchestrator: HybridSearchOrchestrator
  val metadataUpdateRepository: MetadataUpdateRepository
  val bibliographicDraftStore: BibliographicDraftStore
}

class DefaultAppContainer(context: Context) : AppContainer {
  private val appContext = context.applicationContext
  private val database: DipeLibrisDatabase by lazy { DipeLibrisDatabase.build(appContext) }

  override val bibliographicDraftStore: BibliographicDraftStore by lazy {
    BibliographicDraftStore()
  }

  override val preferencesRepository: PreferencesRepository by lazy {
    DataStorePreferencesRepository(appContext)
  }

  override val coverStorageManager: CoverStorageManager by lazy {
    InternalCoverStorageManager(appContext)
  }

  override val connectivityChecker: ConnectivityChecker by lazy {
    AndroidConnectivityChecker(appContext)
  }

  override val libraryRepository: LibraryRepository by lazy {
    RoomLibraryRepository(
      bookDao = database.bookDao(),
      shelfDao = database.shelfDao(),
      editionDao = database.editionDao(),
      copyDao = database.copyDao(),
      readingJourneyDao = database.readingJourneyDao(),
      database = database,
      coverStorageManager = coverStorageManager,
      genreDao = database.genreDao(),
      contributorDao = database.contributorDao(),
      metadataSuggestionDao = database.metadataSuggestionDao(),
    )
  }

  override val hybridSearchOrchestrator: HybridSearchOrchestrator by lazy {
    HybridSearchOrchestrator(
      openLibraryAdapter = OpenLibraryAdapter(),
      googleBooksAdapter = GoogleBooksAdapter(),
      crossrefAdapter = CrossrefAdapter(),
      wikidataAdapter = WikidataAdapter(),
      brasilApiAdapter = BrasilApiAdapter(),
      connectivityChecker = connectivityChecker,
      searchCacheDao = database.searchCacheDao(),
      editionDao = database.editionDao(),
      bookDao = database.bookDao(),
      libraryRepository = libraryRepository,
      appleBooksAdapter = AppleBooksAdapter(),
    )
  }

  override val metadataUpdateRepository: MetadataUpdateRepository by lazy {
    DefaultMetadataUpdateRepository(
      libraryRepository = libraryRepository,
      orchestrator = hybridSearchOrchestrator,
      suggestionDao = database.metadataSuggestionDao(),
      bookDao = database.bookDao(),
      editionDao = database.editionDao(),
      coverStorageManager = coverStorageManager,
      database = database,
    )
  }

  override val readingJourneyRepository: ReadingJourneyRepository by lazy {
    RoomReadingJourneyRepository(
      bookDao = database.bookDao(),
      editionDao = database.editionDao(),
      copyDao = database.copyDao(),
      journeyDao = database.readingJourneyDao(),
      database = database,
    )
  }

  override val diaryRepository: DiaryRepository by lazy {
    RoomDiaryRepository(diaryDao = database.diaryDao())
  }

  override val statisticsRepository: StatisticsRepository by lazy {
    RoomStatisticsRepository(
      bookDao = database.bookDao(),
      diaryDao = database.diaryDao(),
      readingJourneyDao = database.readingJourneyDao(),
    )
  }
}
