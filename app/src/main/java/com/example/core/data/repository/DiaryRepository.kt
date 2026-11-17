package com.example.core.data.repository

import com.example.core.data.local.db.DiaryDao
import com.example.core.data.local.db.DiaryEntryEntity
import com.example.core.data.local.db.DiaryEntryWithBookRow
import com.example.core.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DiaryRepository {
  fun observeAllEntries(): Flow<List<DiaryEntry>>

  fun observeEntriesForWork(workId: Long): Flow<List<DiaryEntry>>

  suspend fun addEntry(content: String, workId: Long?): Long

  suspend fun deleteEntry(entryId: Long)
}

class RoomDiaryRepository(private val diaryDao: DiaryDao) : DiaryRepository {

  override fun observeAllEntries(): Flow<List<DiaryEntry>> =
    diaryDao.observeAllEntriesWithBook().map { rows -> rows.map { it.toDomain() } }

  override fun observeEntriesForWork(workId: Long): Flow<List<DiaryEntry>> =
    diaryDao.observeEntriesForWork(workId).map { rows -> rows.map { it.toDomain() } }

  override suspend fun addEntry(content: String, workId: Long?): Long =
    diaryDao.insertEntry(
      DiaryEntryEntity(
        workId = workId,
        content = content.trim(),
        createdAtEpochMillis = System.currentTimeMillis(),
      )
    )

  override suspend fun deleteEntry(entryId: Long) {
    diaryDao.deleteEntryById(entryId)
  }

  private fun DiaryEntryWithBookRow.toDomain(): DiaryEntry =
    DiaryEntry(
      id = entry.id,
      workId = entry.workId,
      linkedBookTitle = linkedBookTitle,
      linkedBookAuthor = linkedBookAuthor,
      content = entry.content,
      createdAtEpochMillis = entry.createdAtEpochMillis,
    )
}
