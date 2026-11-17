package com.example.core.data.repository

import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.DiaryDao
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface StatisticsRepository {
  fun observeArchiveSnapshot(): Flow<ArchiveSnapshot>
}

class RoomStatisticsRepository(
  private val bookDao: BookDao,
  private val diaryDao: DiaryDao,
) : StatisticsRepository {

  override fun observeArchiveSnapshot(): Flow<ArchiveSnapshot> =
    combine(
      bookDao.observeTotalWorksCount(),
      bookDao.observeWorksCountByStatus(ReadingStatus.READING),
      bookDao.observeWorksCountByStatus(ReadingStatus.FINISHED),
      diaryDao.observeEntriesCount(),
    ) { total, reading, finished, diary ->
      ArchiveSnapshot(
        totalWorks = total,
        readingCount = reading,
        finishedCount = finished,
        diaryEntriesCount = diary,
      )
    }
}
