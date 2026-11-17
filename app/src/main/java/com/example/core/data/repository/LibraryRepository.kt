package com.example.core.data.repository

import com.example.core.data.local.db.BookCopyEntity
import com.example.core.data.local.db.BookDao
import com.example.core.data.local.db.EditionEntity
import com.example.core.data.local.db.ShelfDao
import com.example.core.data.local.db.WorkEntity
import com.example.core.data.local.db.WorkWithEditions
import com.example.core.model.BookCopy
import com.example.core.model.CatalogBook
import com.example.core.model.Edition
import com.example.core.model.ReadingStatus
import com.example.core.model.Shelf
import com.example.core.model.Work
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LibraryRepository {
  fun observeAllBooks(): Flow<List<CatalogBook>>

  fun observeCurrentlyReading(): Flow<List<CatalogBook>>

  fun observeFavoriteBooks(): Flow<List<CatalogBook>>

  fun observeBook(workId: Long): Flow<CatalogBook?>

  fun observeShelves(): Flow<List<Shelf>>

  suspend fun addBook(
    title: String,
    author: String,
    totalPages: Int?,
    publicationYear: Int?,
    editionNote: String?,
    status: ReadingStatus,
  ): Long

  suspend fun updateReadingStatus(workId: Long, status: ReadingStatus)

  suspend fun toggleFavorite(workId: Long, currentFavorite: Boolean)

  suspend fun deleteBook(workId: Long)
}

class RoomLibraryRepository(
  private val bookDao: BookDao,
  private val shelfDao: ShelfDao,
) : LibraryRepository {

  override fun observeAllBooks(): Flow<List<CatalogBook>> =
    bookDao.observeAllCatalogBooks().map { list -> list.map { it.toDomain() } }

  override fun observeCurrentlyReading(): Flow<List<CatalogBook>> =
    bookDao.observeBooksByStatus(ReadingStatus.READING).map { list -> list.map { it.toDomain() } }

  override fun observeFavoriteBooks(): Flow<List<CatalogBook>> =
    bookDao.observeFavoriteBooks().map { list -> list.map { it.toDomain() } }

  override fun observeBook(workId: Long): Flow<CatalogBook?> =
    bookDao.observeCatalogBook(workId).map { it?.toDomain() }

  override fun observeShelves(): Flow<List<Shelf>> =
    shelfDao.observeAllShelves().map { list ->
      list.map { entity ->
        Shelf(
          id = entity.id,
          name = entity.name,
          description = entity.description,
          type = entity.type,
          isFeatured = entity.isFeatured,
        )
      }
    }

  override suspend fun addBook(
    title: String,
    author: String,
    totalPages: Int?,
    publicationYear: Int?,
    editionNote: String?,
    status: ReadingStatus,
  ): Long {
    val now = System.currentTimeMillis()
    return bookDao.insertBookAggregate(
      work =
        WorkEntity(
          title = title.trim(),
          author = author.trim(),
          status = status,
          createdAtEpochMillis = now,
          updatedAtEpochMillis = now,
        ),
      publisherOrNote = editionNote?.trim()?.takeIf { it.isNotEmpty() },
      publicationYear = publicationYear,
      totalPages = totalPages,
    )
  }

  override suspend fun updateReadingStatus(workId: Long, status: ReadingStatus) {
    bookDao.updateWorkStatus(workId, status, System.currentTimeMillis())
  }

  override suspend fun toggleFavorite(workId: Long, currentFavorite: Boolean) {
    bookDao.updateWorkFavorite(workId, !currentFavorite, System.currentTimeMillis())
  }

  override suspend fun deleteBook(workId: Long) {
    bookDao.deleteWorkById(workId)
  }

  private fun WorkWithEditions.toDomain(): CatalogBook {
    val firstEditionWithCopies = editions.firstOrNull()
    val editionDomain =
      firstEditionWithCopies?.edition?.let {
        Edition(
          id = it.id,
          workId = it.workId,
          publisherOrNote = it.publisherOrNote,
          publicationYear = it.publicationYear,
          totalPages = it.totalPages,
          isbn = it.isbn,
          coverUri = it.coverUri,
        )
      }
    val copyDomain =
      firstEditionWithCopies?.copies?.firstOrNull()?.let {
        BookCopy(
          id = it.id,
          editionId = it.editionId,
          currentPage = it.currentPage,
          physicalFormat = it.physicalFormat,
          personalNote = it.personalNote,
        )
      }
    return CatalogBook(
      work =
        Work(
          id = work.id,
          title = work.title,
          author = work.author,
          status = work.status,
          isFavorite = work.isFavorite,
          typographyPresetOverride = work.typographyPresetOverride,
          createdAtEpochMillis = work.createdAtEpochMillis,
          updatedAtEpochMillis = work.updatedAtEpochMillis,
        ),
      primaryEdition = editionDomain,
      primaryCopy = copyDomain,
    )
  }
}
