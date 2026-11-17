package com.example.core.di

import android.content.Context
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.preferences.DataStorePreferencesRepository
import com.example.core.data.local.preferences.PreferencesRepository
import com.example.core.data.repository.DiaryRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.RoomDiaryRepository
import com.example.core.data.repository.RoomLibraryRepository
import com.example.core.data.repository.RoomStatisticsRepository
import com.example.core.data.repository.StatisticsRepository

interface AppContainer {
  val preferencesRepository: PreferencesRepository
  val libraryRepository: LibraryRepository
  val diaryRepository: DiaryRepository
  val statisticsRepository: StatisticsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
  private val database: DipeLibrisDatabase by lazy { DipeLibrisDatabase.build(context) }

  override val preferencesRepository: PreferencesRepository by lazy {
    DataStorePreferencesRepository(context)
  }

  override val libraryRepository: LibraryRepository by lazy {
    RoomLibraryRepository(
      bookDao = database.bookDao(),
      shelfDao = database.shelfDao(),
    )
  }

  override val diaryRepository: DiaryRepository by lazy {
    RoomDiaryRepository(diaryDao = database.diaryDao())
  }

  override val statisticsRepository: StatisticsRepository by lazy {
    RoomStatisticsRepository(
      bookDao = database.bookDao(),
      diaryDao = database.diaryDao(),
    )
  }
}
