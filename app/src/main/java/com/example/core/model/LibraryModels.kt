package com.example.core.model

/**
 * Core domain models structured around Work -> Edition -> Copy to allow progressive expansion
 * in future phases without restructuring the database or UI contracts.
 */
data class Work(
  val id: Long = 0,
  val title: String,
  val author: String,
  val status: ReadingStatus = ReadingStatus.UNREAD,
  val isFavorite: Boolean = false,
  val typographyPresetOverride: TypographyPreset? = null,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
  val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

data class Edition(
  val id: Long = 0,
  val workId: Long,
  val publisherOrNote: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn: String? = null,
  val coverUri: String? = null,
)

data class BookCopy(
  val id: Long = 0,
  val editionId: Long,
  val currentPage: Int = 0,
  val physicalFormat: String? = null,
  val personalNote: String? = null,
)

data class CatalogBook(
  val work: Work,
  val primaryEdition: Edition?,
  val primaryCopy: BookCopy?,
) {
  val id: Long get() = work.id
  val title: String get() = work.title
  val author: String get() = work.author
  val status: ReadingStatus get() = work.status
  val isFavorite: Boolean get() = work.isFavorite
  val totalPages: Int? get() = primaryEdition?.totalPages
  val publicationYear: Int? get() = primaryEdition?.publicationYear
  val editionNote: String? get() = primaryEdition?.publisherOrNote
  val typographyPresetOverride: TypographyPreset? get() = work.typographyPresetOverride
}

data class DiaryEntry(
  val id: Long = 0,
  val workId: Long? = null,
  val linkedBookTitle: String? = null,
  val linkedBookAuthor: String? = null,
  val content: String,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class Shelf(
  val id: Long = 0,
  val name: String,
  val description: String? = null,
  val type: ShelfType = ShelfType.CUSTOM,
  val isFeatured: Boolean = false,
)

data class ArchiveSnapshot(
  val totalWorks: Int = 0,
  val readingCount: Int = 0,
  val finishedCount: Int = 0,
  val diaryEntriesCount: Int = 0,
)
