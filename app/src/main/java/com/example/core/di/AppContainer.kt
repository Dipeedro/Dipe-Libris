package com.example.core.di

import android.content.Context
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.media.CoverStorageManager
import com.example.core.data.local.media.InternalCoverStorageManager
import com.example.core.data.local.preferences.DataStorePreferencesRepository
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.ReadingJourneyRepository
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomReadingJourneyRepository
import com.example.core.data.repository.RoomStatisticsRepository
import com.example.core.data.repository.StatisticsRepository

interface AppContainer {
  val preferencesRepository: PreferencesRepository
  val coverStorageManager: CoverStorageManager
  val libraryRepository: LibraryRepository
  val readingJourneyRepository: ReadingJourneyRepository
  val diaryRepository: DiaryRepository
  val statisticsRepository: StatisticsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
  private val database: DipeLibrisDatabase by lazy { DipeLibrisDatabase.build(context) }

  override val preferencesRepository: PreferencesRepository by lazy {
    DataStorePreferencesRepository(context)
  }

  override val coverStorageManager: CoverStorageManager by lazy {
    InternalCoverStorageManager(context.applicationContext)
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
