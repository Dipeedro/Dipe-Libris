package com.example.core.data.repository

import androidx.room.withTransaction
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.CopyDao
import com.example.core.data.local.db.DipeLibrisDatabase
import com.example.core.data.local.db.EditionDao
import com.example.core.data.local.db.ReadingJourneyDao
import com.example.core.data.local.db.ReadingProgressLogEntity
import com.example.core.data.local.db.ReadingSessionEntity
import com.example.core.model.ReadingProgressMath
import com.example.core.model.ReadingStatus

data class JourneyCompletionSnapshot(
  val sessionId: Long,
  val workId: Long,
  val copyId: Long?,
  val previousSessionStatus: ReadingStatus,
  val previousWorkStatus: ReadingStatus,
  val previousPage: Int,
  val previousPercent: Float,
  val previousTotalPagesSnapshot: Int?,
  val previousEndEpochMillis: Long?,
  val previousPausedAtEpochMillis: Long?,
  val previousAccumulatedPausedMillis: Long,
  val createdLogId: Long?,
)

interface ReadingJourneyRepository {
  suspend fun startJourney(workId: Long, copyId: Long?, notes: String? = null): Long

  suspend fun startReread(workId: Long, copyId: Long?, notes: String? = null): Long

  suspend fun updateProgressByPage(
    sessionId: Long,
    page: Int,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
    updatedTotalPages: Int? = null,
  )

  suspend fun updateProgressByPercent(
    sessionId: Long,
    percent: Float,
    note: String? = null,
    markFinishedIfComplete: Boolean = true,
  )

  suspend fun pauseJourney(sessionId: Long)

  suspend fun resumeJourney(sessionId: Long)

  suspend fun completeJourney(sessionId: Long, note: String? = null)

  suspend fun completeJourneyWithUndoSnapshot(
    sessionId: Long,
    note: String? = null,
  ): JourneyCompletionSnapshot?

  suspend fun restoreJourneyCompletion(snapshot: JourneyCompletionSnapshot)

  suspend fun abandonJourney(sessionId: Long)

  suspend fun switchActiveCopy(sessionId: Long, copyId: Long)
}

class RoomReadingJourneyRepository(
  private val bookDao: BookDao,
  private val editionDao: EditionDao,
  private val copyDao: CopyDao,
  private val journeyDao: ReadingJourneyDao,
  private val database: DipeLibrisDatabase? = null,
) : ReadingJourneyRepository {

  private suspend fun <T> runInTransaction(block: suspend () -> T): T =
    if (database != null) database.withTransaction { block() } else block()

  override suspend fun startJourney(workId: Long, copyId: Long?, notes: String?): Long =
    runInTransaction {
      val now = System.currentTimeMillis()
      val existingActive = journeyDao.getActiveSessionForWork(workId)
      if (existingActive != null) {
        if (existingActive.status == ReadingStatus.PAUSED) {
          val pauseDelta =
            existingActive.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
          journeyDao.updateSession(
            existingActive.copy(
              copyId = copyId ?: existingActive.copyId,
              status = ReadingStatus.READING,
              pausedAtEpochMillis = null,
              accumulatedPausedMillis = existingActive.accumulatedPausedMillis + pauseDelta,
            )
          )
          bookDao.updateWorkStatus(workId, ReadingStatus.READING, now)
        } else if (copyId != null && copyId != existingActive.copyId) {
          switchActiveCopyInternal(existingActive, copyId, now)
        }
        return@runInTransaction existingActive.id
      }

      createNewSessionInternal(
        workId = workId,
        copyId = copyId,
        notes = notes,
        now = now,
      )
    }

  override suspend fun startReread(workId: Long, copyId: Long?, notes: String?): Long =
    runInTransaction {
      val now = System.currentTimeMillis()
      val existingActive = journeyDao.getActiveSessionForWork(workId)
      if (existingActive != null) {
        val pauseDelta =
          existingActive.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        journeyDao.updateSession(
          existingActive.copy(
            status = ReadingStatus.ABANDONED,
            endEpochMillis = now,
            pausedAtEpochMillis = null,
            accumulatedPausedMillis = existingActive.accumulatedPausedMillis + pauseDelta,
          )
        )
      }
      createNewSessionInternal(
        workId = workId,
        copyId = copyId,
        notes = notes,
        now = now,
      )
    }

  override suspend fun updateProgressByPage(
    sessionId: Long,
    page: Int,
    note: String?,
    markFinishedIfComplete: Boolean,
    updatedTotalPages: Int?,
  ) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      val copy = session.copyId?.let { copyDao.getCopyById(it) }
      val edition =
        copy?.let { editionDao.getEditionById(it.editionId) }
          ?: editionDao.getEditionsForWork(session.workId).firstOrNull()
      val normalizedUpdatedTotal = updatedTotalPages?.takeIf { it > 0 }
      if (normalizedUpdatedTotal != null && edition != null && edition.totalPages != normalizedUpdatedTotal) {
        editionDao.updateEdition(edition.copy(totalPages = normalizedUpdatedTotal))
      }
      val totalPages =
        normalizedUpdatedTotal
          ?: ReadingProgressMath.effectiveTotalPages(edition?.totalPages, session.totalPagesSnapshot)

      val calc =
        ReadingProgressMath.calculateFromPage(
          page = page,
          totalPages = totalPages,
          fallbackPercent = if (normalizedUpdatedTotal != null) 0f else session.progressPercent,
        )

      val reachedEnd = markFinishedIfComplete && totalPages != null && calc.currentPage >= totalPages
      val pauseDelta =
        if (session.status == ReadingStatus.PAUSED && session.pausedAtEpochMillis != null) {
          (now - session.pausedAtEpochMillis).coerceAtLeast(0L)
        } else {
          0L
        }
      val newStatus =
        when {
          reachedEnd -> ReadingStatus.FINISHED
          session.status == ReadingStatus.PAUSED -> ReadingStatus.READING
          else -> session.status
        }

      journeyDao.updateSession(
        session.copy(
          status = newStatus,
          currentPage = calc.currentPage,
          totalPagesSnapshot = totalPages,
          progressPercent = if (reachedEnd) 100f else calc.progressPercent,
          endEpochMillis = if (reachedEnd) now else session.endEpochMillis,
          pausedAtEpochMillis = null,
          accumulatedPausedMillis = session.accumulatedPausedMillis + pauseDelta,
        )
      )

      journeyDao.insertProgressLog(
        ReadingProgressLogEntity(
          sessionId = session.id,
          recordedAtEpochMillis = now,
          pageNumber = calc.currentPage,
          progressPercent = if (reachedEnd) 100f else calc.progressPercent,
          deltaPages = calc.currentPage - session.currentPage,
          note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
      )

      if (copy != null) {
        copyDao.updateCopyCurrentPage(copy.id, calc.currentPage)
      }
      bookDao.updateWorkStatus(session.workId, newStatus, now)
    }
  }

  override suspend fun updateProgressByPercent(
    sessionId: Long,
    percent: Float,
    note: String?,
    markFinishedIfComplete: Boolean,
  ) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      val copy = session.copyId?.let { copyDao.getCopyById(it) }
      val edition = copy?.let { editionDao.getEditionById(it.editionId) }
      val totalPages =
        ReadingProgressMath.effectiveTotalPages(edition?.totalPages, session.totalPagesSnapshot)

      val calc =
        ReadingProgressMath.calculateFromPercent(
          percent = percent,
          totalPages = totalPages,
          fallbackPage = session.currentPage,
        )

      val reachedEnd = markFinishedIfComplete && calc.progressPercent >= 100f
      val pauseDelta =
        if (session.status == ReadingStatus.PAUSED && session.pausedAtEpochMillis != null) {
          (now - session.pausedAtEpochMillis).coerceAtLeast(0L)
        } else {
          0L
        }
      val newStatus =
        when {
          reachedEnd -> ReadingStatus.FINISHED
          session.status == ReadingStatus.PAUSED -> ReadingStatus.READING
          else -> session.status
        }

      journeyDao.updateSession(
        session.copy(
          status = newStatus,
          currentPage = calc.currentPage,
          totalPagesSnapshot = totalPages,
          progressPercent = if (reachedEnd) 100f else calc.progressPercent,
          endEpochMillis = if (reachedEnd) now else session.endEpochMillis,
          pausedAtEpochMillis = null,
          accumulatedPausedMillis = session.accumulatedPausedMillis + pauseDelta,
        )
      )

      journeyDao.insertProgressLog(
        ReadingProgressLogEntity(
          sessionId = session.id,
          recordedAtEpochMillis = now,
          pageNumber = if (totalPages != null) calc.currentPage else null,
          progressPercent = if (reachedEnd) 100f else calc.progressPercent,
          deltaPages = if (totalPages != null) calc.currentPage - session.currentPage else 0,
          note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
      )

      if (copy != null && totalPages != null) {
        copyDao.updateCopyCurrentPage(copy.id, calc.currentPage)
      }
      bookDao.updateWorkStatus(session.workId, newStatus, now)
    }
  }

  override suspend fun pauseJourney(sessionId: Long) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      if (session.status != ReadingStatus.READING) return@runInTransaction
      journeyDao.updateSession(
        session.copy(
          status = ReadingStatus.PAUSED,
          pausedAtEpochMillis = now,
        )
      )
      bookDao.updateWorkStatus(session.workId, ReadingStatus.PAUSED, now)
    }
  }

  override suspend fun resumeJourney(sessionId: Long) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      if (session.status != ReadingStatus.PAUSED) return@runInTransaction
      val pauseDelta =
        session.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
      journeyDao.updateSession(
        session.copy(
          status = ReadingStatus.READING,
          pausedAtEpochMillis = null,
          accumulatedPausedMillis = session.accumulatedPausedMillis + pauseDelta,
        )
      )
      bookDao.updateWorkStatus(session.workId, ReadingStatus.READING, now)
    }
  }

  override suspend fun completeJourney(sessionId: Long, note: String?) {
    completeJourneyWithUndoSnapshot(sessionId = sessionId, note = note)
  }

  override suspend fun completeJourneyWithUndoSnapshot(
    sessionId: Long,
    note: String?,
  ): JourneyCompletionSnapshot? =
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction null
      val work = bookDao.getWorkById(session.workId)
      val copy = session.copyId?.let { copyDao.getCopyById(it) }
      val edition = copy?.let { editionDao.getEditionById(it.editionId) }
      val totalPages =
        ReadingProgressMath.effectiveTotalPages(edition?.totalPages, session.totalPagesSnapshot)
      val pauseDelta =
        session.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
      val finalPage = totalPages ?: session.currentPage
      val deltaPages = if (totalPages != null) (finalPage - session.currentPage).coerceAtLeast(0) else 0

      journeyDao.updateSession(
        session.copy(
          status = ReadingStatus.FINISHED,
          currentPage = finalPage,
          totalPagesSnapshot = totalPages,
          progressPercent = 100f,
          endEpochMillis = now,
          pausedAtEpochMillis = null,
          accumulatedPausedMillis = session.accumulatedPausedMillis + pauseDelta,
        )
      )
      val logId =
        journeyDao.insertProgressLog(
          ReadingProgressLogEntity(
            sessionId = session.id,
            recordedAtEpochMillis = now,
            pageNumber = totalPages,
            progressPercent = 100f,
            deltaPages = deltaPages,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
          )
        )
      if (copy != null && totalPages != null) {
        copyDao.updateCopyCurrentPage(copy.id, finalPage)
      }
      bookDao.updateWorkStatus(session.workId, ReadingStatus.FINISHED, now)

      JourneyCompletionSnapshot(
        sessionId = session.id,
        workId = session.workId,
        copyId = session.copyId,
        previousSessionStatus = session.status,
        previousWorkStatus = work?.status ?: session.status,
        previousPage = session.currentPage,
        previousPercent = session.progressPercent,
        previousTotalPagesSnapshot = session.totalPagesSnapshot,
        previousEndEpochMillis = session.endEpochMillis,
        previousPausedAtEpochMillis = session.pausedAtEpochMillis,
        previousAccumulatedPausedMillis = session.accumulatedPausedMillis,
        createdLogId = logId,
      )
    }

  override suspend fun restoreJourneyCompletion(snapshot: JourneyCompletionSnapshot) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val currentSession = journeyDao.getSessionById(snapshot.sessionId) ?: return@runInTransaction
      journeyDao.updateSession(
        currentSession.copy(
          status = snapshot.previousSessionStatus,
          currentPage = snapshot.previousPage,
          totalPagesSnapshot = snapshot.previousTotalPagesSnapshot,
          progressPercent = snapshot.previousPercent,
          endEpochMillis = snapshot.previousEndEpochMillis,
          pausedAtEpochMillis = snapshot.previousPausedAtEpochMillis,
          accumulatedPausedMillis = snapshot.previousAccumulatedPausedMillis,
        )
      )
      snapshot.createdLogId?.let { logId ->
        journeyDao.deleteProgressLogById(logId)
      }
      snapshot.copyId?.let { copyId ->
        copyDao.updateCopyCurrentPage(copyId, snapshot.previousPage)
      }
      bookDao.updateWorkStatus(snapshot.workId, snapshot.previousWorkStatus, now)
    }
  }

  override suspend fun abandonJourney(sessionId: Long) {
    runInTransaction {
      val now = System.currentTimeMillis()
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      val pauseDelta =
        session.pausedAtEpochMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
      journeyDao.updateSession(
        session.copy(
          status = ReadingStatus.ABANDONED,
          endEpochMillis = now,
          pausedAtEpochMillis = null,
          accumulatedPausedMillis = session.accumulatedPausedMillis + pauseDelta,
        )
      )
      bookDao.updateWorkStatus(session.workId, ReadingStatus.ABANDONED, now)
    }
  }

  override suspend fun switchActiveCopy(sessionId: Long, copyId: Long) {
    runInTransaction {
      val session = journeyDao.getSessionById(sessionId) ?: return@runInTransaction
      switchActiveCopyInternal(session, copyId, System.currentTimeMillis())
    }
  }

  private suspend fun switchActiveCopyInternal(
    session: ReadingSessionEntity,
    newCopyId: Long,
    now: Long,
  ) {
    val newCopy = copyDao.getCopyById(newCopyId) ?: return
    val newEdition = editionDao.getEditionById(newCopy.editionId) ?: return
    if (newEdition.workId != session.workId) return

    val totalPages =
      ReadingProgressMath.effectiveTotalPages(newEdition.totalPages, session.totalPagesSnapshot)
    val calc =
      if (totalPages != null && session.progressPercent > 0f) {
        ReadingProgressMath.calculateFromPercent(
          percent = session.progressPercent,
          totalPages = totalPages,
          fallbackPage = session.currentPage,
        )
      } else {
        ReadingProgressMath.calculateFromPage(
          page = session.currentPage,
          totalPages = totalPages,
          fallbackPercent = session.progressPercent,
        )
      }
    journeyDao.updateSession(
      session.copy(
        copyId = newCopyId,
        totalPagesSnapshot = totalPages,
        currentPage = calc.currentPage,
        progressPercent = calc.progressPercent,
      )
    )
    copyDao.updateCopyCurrentPage(newCopyId, calc.currentPage)
    bookDao.updateWorkStatus(session.workId, session.status, now)
  }

  private suspend fun createNewSessionInternal(
    workId: Long,
    copyId: Long?,
    notes: String?,
    now: Long,
  ): Long {
    val resolvedCopy = copyId?.let { copyDao.getCopyById(it) } ?: copyDao.getDefaultCopyForWork(workId)
    val edition = resolvedCopy?.let { editionDao.getEditionById(it.editionId) }
    val maxReread = journeyDao.getMaxRereadNumber(workId)
    val sessionId =
      journeyDao.insertSession(
        ReadingSessionEntity(
          workId = workId,
          copyId = resolvedCopy?.id,
          status = ReadingStatus.READING,
          rereadNumber = maxReread + 1,
          currentPage = 0,
          totalPagesSnapshot = edition?.totalPages,
          progressPercent = 0f,
          startEpochMillis = now,
          notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
      )
    if (resolvedCopy != null) {
      copyDao.updateCopyCurrentPage(resolvedCopy.id, 0)
    }
    bookDao.updateWorkStatus(workId, ReadingStatus.READING, now)
    return sessionId
  }
}
