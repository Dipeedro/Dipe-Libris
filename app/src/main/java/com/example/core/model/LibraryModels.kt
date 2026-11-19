package com.example.core.model

import androidx.compose.runtime.Immutable

/**
 * Core domain models structured around Work -> Edition -> Copy -> ReadingSession.
 */
@Immutable
data class Genre(
  val id: Long = 0,
  val slug: String,
  val namePt: String,
  val nameEn: String,
  val isCustom: Boolean = false,
) {
  fun localizedName(language: AppLanguage): String =
    when {
      isCustom -> namePt.ifBlank { nameEn }
      language == AppLanguage.PORTUGUESE_BR -> namePt
      else -> nameEn
    }
}

@Immutable
data class WorkContributor(
  val contributorId: Long = 0,
  val name: String,
  val role: ContributorRole = ContributorRole.AUTHOR,
  val editionId: Long? = null,
  val displayOrder: Int = 0,
)

@Immutable
data class Work(
  val id: Long = 0,
  val title: String,
  val author: String,
  val subtitle: String? = null,
  val originalTitle: String? = null,
  val primaryGenreId: Long? = null,
  val primaryGenre: Genre? = null,
  val activeSynopsis: String? = null,
  val synopsisLanguage: String? = null,
  val synopsisSource: String? = null,
  val isSynopsisCustomized: Boolean = false,
  val externalWorkKey: String? = null,
  val userEditedMask: Long = 0L,
  val status: ReadingStatus = ReadingStatus.UNREAD,
  val isFavorite: Boolean = false,
  val typographyPresetOverride: TypographyPreset? = null,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
  val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

@Immutable
data class Edition(
  val id: Long = 0,
  val workId: Long,
  val publisherOrNote: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn: String? = null,
  val isbn10: String? = null,
  val isbn13: String? = null,
  val coverUri: String? = null,
  val coverSource: String? = null,
  val isCoverCustomized: Boolean = false,
  val language: String? = null,
  val format: EditionFormat? = null,
  val translator: String? = null,
  val editionLabel: String? = null,
  val seriesName: String? = null,
  val seriesVolume: String? = null,
  val synopsis: String? = null,
  val synopsisSource: String? = null,
  val categories: List<String> = emptyList(),
  val externalIdsJson: String? = null,
  val isPrimary: Boolean = false,
  val createdAtEpochMillis: Long = 0L,
)

@Immutable
data class BookCopy(
  val id: Long = 0,
  val editionId: Long,
  val currentPage: Int = 0,
  val physicalFormat: String? = null,
  val personalNote: String? = null,
  val label: String? = null,
  val mediaType: CopyMediaType = CopyMediaType.PHYSICAL,
  val locationOrPlatform: String? = null,
  val acquisitionEpochMillis: Long? = null,
  val condition: CopyCondition? = null,
  val acquisitionOrigin: CopyOrigin? = null,
  val pricePaidMinorUnits: Long? = null,
  val currencyCode: String? = null,
  val customCopyCode: String? = null,
  val customAttributesJson: String? = null,
  val createdAtEpochMillis: Long = 0L,
)

@Immutable
data class ReadingSession(
  val id: Long = 0,
  val workId: Long,
  val copyId: Long? = null,
  val rereadNumber: Int = 1,
  val status: ReadingStatus = ReadingStatus.READING,
  val currentPage: Int = 0,
  val totalPagesSnapshot: Int? = null,
  val progressPercent: Float = 0f,
  val startEpochMillis: Long,
  val endEpochMillis: Long? = null,
  val pausedAtEpochMillis: Long? = null,
  val accumulatedPausedMillis: Long = 0L,
  val notes: String? = null,
) {
  val isActive: Boolean
    get() = status == ReadingStatus.READING || status == ReadingStatus.PAUSED
}

@Immutable
data class ReadingProgressLog(
  val id: Long = 0,
  val sessionId: Long,
  val recordedAtEpochMillis: Long,
  val pageNumber: Int? = null,
  val progressPercent: Float,
  val deltaPages: Int = 0,
  val note: String? = null,
)

@Immutable
data class EditionWithBookCopies(
  val edition: Edition,
  val copies: List<BookCopy>,
  val contributors: List<WorkContributor> = emptyList(),
)

@Immutable
data class ReadingJourneySummary(
  val session: ReadingSession,
  val copy: BookCopy?,
  val edition: Edition?,
  val logs: List<ReadingProgressLog>,
)

@Immutable
data class MetadataSuggestion(
  val id: Long = 0,
  val workId: Long,
  val editionId: Long? = null,
  val scope: MetadataSuggestionScope = MetadataSuggestionScope.WORK,
  val fieldName: String,
  val currentValue: String? = null,
  val suggestedValue: String,
  val sourceName: String,
  val reason: String,
  val confidenceScore: Int = 0,
  val status: MetadataSuggestionStatus = MetadataSuggestionStatus.PENDING,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Immutable
data class BookDetailAggregate(
  val work: Work,
  val editions: List<EditionWithBookCopies>,
  val activeJourney: ReadingJourneySummary?,
  val journeys: List<ReadingJourneySummary>,
  val contributors: List<WorkContributor> = emptyList(),
  val pendingSuggestions: List<MetadataSuggestion> = emptyList(),
) {
  val totalCopiesCount: Int
    get() = editions.sumOf { it.copies.size }
}

@Immutable
data class CatalogBook(
  val work: Work,
  val primaryEdition: Edition?,
  val primaryCopy: BookCopy?,
  val editionCount: Int = 1,
  val copyCount: Int = 1,
  val activeSessionId: Long? = null,
  val activeProgressPercent: Float? = null,
  val activeCurrentPage: Int? = null,
  val activeTotalPages: Int? = null,
  val activeRereadNumber: Int? = null,
  val activeCopyMediaType: CopyMediaType? = null,
  val activeCopyLabel: String? = null,
  val primaryGenre: Genre? = null,
  val pendingSuggestionsCount: Int = 0,
) {
  val id: Long get() = work.id
  val title: String get() = work.title
  val author: String get() = work.author
  val status: ReadingStatus get() = work.status
  val isFavorite: Boolean get() = work.isFavorite
  val coverUri: String? get() = primaryEdition?.coverUri
  val totalPages: Int? get() = activeTotalPages ?: primaryEdition?.totalPages
  val publicationYear: Int? get() = primaryEdition?.publicationYear
  val editionNote: String? get() = primaryEdition?.publisherOrNote
  val typographyPresetOverride: TypographyPreset? get() = work.typographyPresetOverride
}

@Immutable
data class DiaryEntry(
  val id: Long = 0,
  val workId: Long,
  val linkedBookTitle: String? = null,
  val linkedBookAuthor: String? = null,
  val content: String,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Immutable
data class Shelf(
  val id: Long = 0,
  val name: String,
  val description: String? = null,
  val type: ShelfType = ShelfType.CUSTOM,
  val isFeatured: Boolean = false,
)

@Immutable
data class ArchiveSnapshot(
  val totalWorks: Int = 0,
  val readingCount: Int = 0,
  val finishedCount: Int = 0,
  val unreadCount: Int = 0,
  val pausedCount: Int = 0,
  val abandonedCount: Int = 0,
  val diaryEntriesCount: Int = 0,
  val editionsCount: Int = 0,
  val copiesCount: Int = 0,
  val completedJourneysCount: Int = 0,
  val rereadsCount: Int = 0,
  val totalPagesRead: Int = 0,
)
