package com.example.core.data.repository

import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.DiaryDao
import com.example.core.data.local.db.ReadingJourneyDao
import com.example.core.model.ArchiveSnapshot
import com.example.core.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

interface StatisticsRepository {
  fun observeArchiveSnapshot(): Flow<ArchiveSnapshot>
}

class RoomStatisticsRepository(
  private val bookDao: BookDao,
  private val diaryDao: DiaryDao,
  private val readingJourneyDao: ReadingJourneyDao? = null,
) : StatisticsRepository {

  override fun observeArchiveSnapshot(): Flow<ArchiveSnapshot> {
    val statusCounts =
      combine(
        bookDao.observeWorksCountByStatus(ReadingStatus.READING),
        bookDao.observeWorksCountByStatus(ReadingStatus.FINISHED),
        bookDao.observeWorksCountByStatus(ReadingStatus.UNREAD),
        bookDao.observeWorksCountByStatus(ReadingStatus.PAUSED),
        bookDao.observeWorksCountByStatus(ReadingStatus.ABANDONED),
      ) { reading, finished, unread, paused, abandoned ->
        StatusCounts(
          reading = reading,
          finished = finished,
          unread = unread,
          paused = paused,
          abandoned = abandoned,
        )
      }

    val journeyCounts =
      if (readingJourneyDao != null) {
        combine(
          bookDao.observeTotalEditionsCount(),
          bookDao.observeTotalCopiesCount(),
          readingJourneyDao.observeCompletedJourneysCount(),
          readingJourneyDao.observeRereadsCount(),
          readingJourneyDao.observeTotalPagesRead(),
        ) { editions, copies, completedJourneys, rereads, pagesRead ->
          JourneyCounts(
            editions = editions,
            copies = copies,
            completedJourneys = completedJourneys,
            rereads = rereads,
            pagesRead = pagesRead,
          )
        }
      } else {
        flowOf(JourneyCounts())
      }

    return combine(
        bookDao.observeTotalWorksCount(),
        statusCounts,
        diaryDao.observeEntriesCount(),
        journeyCounts,
      ) { total, counts, diary, journeys ->
        ArchiveSnapshot(
          totalWorks = total,
          readingCount = counts.reading,
          finishedCount = counts.finished,
          unreadCount = counts.unread,
          pausedCount = counts.paused,
          abandonedCount = counts.abandoned,
          diaryEntriesCount = diary,
          editionsCount = journeys.editions,
          copiesCount = journeys.copies,
          completedJourneysCount = journeys.completedJourneys,
          rereadsCount = journeys.rereads,
          totalPagesRead = journeys.pagesRead,
        )
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  private data class StatusCounts(
    val reading: Int,
    val finished: Int,
    val unread: Int,
    val paused: Int,
    val abandoned: Int,
  )

  private data class JourneyCounts(
    val editions: Int = 0,
    val copies: Int = 0,
    val completedJourneys: Int = 0,
    val rereads: Int = 0,
    val pagesRead: Int = 0,
  )
}
