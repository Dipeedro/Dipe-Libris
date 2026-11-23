package com.example.core.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.example.core.model.ContributorRole
import com.example.core.model.CopyCondition
import com.example.core.model.CopyMediaType
import com.example.core.model.CopyOrigin
import com.example.core.model.EditionFormat
import com.example.core.model.MetadataSuggestionScope
import com.example.core.model.MetadataSuggestionStatus
import com.example.core.model.ReadingStatus
import com.example.core.model.ShelfType
import com.example.core.model.TypographyPreset

@Entity(
  tableName = "genres",
  indices = [Index(value = ["slug"], unique = true)],
)
data class GenreEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val slug: String,
  val namePt: String,
  val nameEn: String,
  @ColumnInfo(defaultValue = "0") val isCustom: Boolean = false,
  @ColumnInfo(defaultValue = "0") val createdAtEpochMillis: Long = 0L,
)

@Entity(
  tableName = "works",
  foreignKeys = [
    ForeignKey(
      entity = GenreEntity::class,
      parentColumns = ["id"],
      childColumns = ["primaryGenreId"],
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [
    Index(value = ["primaryGenreId"]),
    Index(value = ["status"]),
    Index(value = ["isFavorite"]),
    Index(value = ["externalWorkKey"]),
    Index(value = ["createdAtEpochMillis"]),
    Index(value = ["updatedAtEpochMillis"]),
  ],
)
data class WorkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val author: String,
  val subtitle: String? = null,
  val originalTitle: String? = null,
  val primaryGenreId: Long? = null,
  val activeSynopsis: String? = null,
  val synopsisLanguage: String? = null,
  val synopsisSource: String? = null,
  @ColumnInfo(defaultValue = "0") val isSynopsisCustomized: Boolean = false,
  val externalWorkKey: String? = null,
  @ColumnInfo(defaultValue = "0") val userEditedMask: Long = 0L,
  val status: ReadingStatus = ReadingStatus.UNREAD,
  val isFavorite: Boolean = false,
  val typographyPresetOverride: TypographyPreset? = null,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
  val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "editions",
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["workId"]),
    Index(value = ["isbn13"]),
    Index(value = ["isbn10"]),
    Index(value = ["workId", "isPrimary"]),
  ],
)
data class EditionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val publisherOrNote: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn: String? = null,
  val isbn10: String? = null,
  val isbn13: String? = null,
  val coverUri: String? = null,
  val coverSource: String? = null,
  @ColumnInfo(defaultValue = "0") val isCoverCustomized: Boolean = false,
  val language: String? = null,
  val format: EditionFormat? = null,
  val translator: String? = null,
  val editionLabel: String? = null,
  val seriesName: String? = null,
  val seriesVolume: String? = null,
  val synopsis: String? = null,
  val synopsisSource: String? = null,
  val categoriesJson: String? = null,
  val externalIdsJson: String? = null,
  @ColumnInfo(defaultValue = "0") val isPrimary: Boolean = false,
  @ColumnInfo(defaultValue = "0") val createdAtEpochMillis: Long = 0L,
)

@Entity(
  tableName = "book_copies",
  foreignKeys = [
    ForeignKey(
      entity = EditionEntity::class,
      parentColumns = ["id"],
      childColumns = ["editionId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index(value = ["editionId"])],
)
data class BookCopyEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val editionId: Long,
  val currentPage: Int = 0,
  val physicalFormat: String? = null,
  val personalNote: String? = null,
  val label: String? = null,
  @ColumnInfo(defaultValue = "'PHYSICAL'") val mediaType: CopyMediaType = CopyMediaType.PHYSICAL,
  val locationOrPlatform: String? = null,
  val acquisitionEpochMillis: Long? = null,
  val condition: CopyCondition? = null,
  val acquisitionOrigin: CopyOrigin? = null,
  val pricePaidMinorUnits: Long? = null,
  val currencyCode: String? = null,
  val customCopyCode: String? = null,
  val customAttributesJson: String? = null,
  @ColumnInfo(defaultValue = "0") val createdAtEpochMillis: Long = 0L,
)

@Entity(
  tableName = "contributors",
  indices = [Index(value = ["normalizedName"], unique = true)],
)
data class ContributorEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val normalizedName: String,
  val externalIdsJson: String? = null,
)

@Entity(
  tableName = "contribution_links",
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = EditionEntity::class,
      parentColumns = ["id"],
      childColumns = ["editionId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ContributorEntity::class,
      parentColumns = ["id"],
      childColumns = ["contributorId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["workId"]),
    Index(value = ["editionId"]),
    Index(value = ["contributorId"]),
  ],
)
data class ContributionLinkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val editionId: Long? = null,
  val contributorId: Long,
  val role: ContributorRole = ContributorRole.AUTHOR,
  val displayOrder: Int = 0,
)

@Entity(
  tableName = "search_cache_entries",
  indices = [Index(value = ["expiresAtEpochMillis"])],
)
data class SearchCacheEntity(
  @PrimaryKey val cacheKey: String,
  val queryNormalized: String,
  val queryType: String,
  val payloadJson: String,
  val sourcesUsed: String,
  val fetchedAtEpochMillis: Long,
  val expiresAtEpochMillis: Long,
  val lastAccessedEpochMillis: Long,
)

@Entity(
  tableName = "metadata_update_suggestions",
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = EditionEntity::class,
      parentColumns = ["id"],
      childColumns = ["editionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["workId"]),
    Index(value = ["editionId"]),
    Index(value = ["status"]),
    Index(value = ["workId", "status"]),
  ],
)
data class MetadataSuggestionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Fts4
@Entity(tableName = "library_fts")
data class LibraryFtsEntity(
  @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
  val title: String,
  val subtitle: String,
  val authorsText: String,
  val translatorsText: String,
  val publisherText: String,
  val isbnsText: String,
  val genreText: String,
  val notesText: String,
)

@Entity(tableName = "shelves")
data class ShelfEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val description: String? = null,
  val type: ShelfType = ShelfType.CUSTOM,
  val isFeatured: Boolean = false,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "book_shelf_cross_ref",
  primaryKeys = ["workId", "shelfId"],
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ShelfEntity::class,
      parentColumns = ["id"],
      childColumns = ["shelfId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["shelfId"])],
)
data class BookShelfCrossRef(
  val workId: Long,
  val shelfId: Long,
)

@Entity(
  tableName = "diary_entries",
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index(value = ["workId"])],
)
data class DiaryEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val content: String,
  val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "reading_sessions",
  foreignKeys = [
    ForeignKey(
      entity = WorkEntity::class,
      parentColumns = ["id"],
      childColumns = ["workId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = BookCopyEntity::class,
      parentColumns = ["id"],
      childColumns = ["copyId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["workId"]),
    Index(value = ["copyId"]),
    Index(value = ["workId", "status", "rereadNumber"]),
  ],
)
data class ReadingSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val copyId: Long? = null,
  val status: ReadingStatus = ReadingStatus.READING,
  val rereadNumber: Int = 1,
  val currentPage: Int = 0,
  val totalPagesSnapshot: Int? = null,
  val progressPercent: Float = 0f,
  val startEpochMillis: Long,
  val endEpochMillis: Long? = null,
  val pausedAtEpochMillis: Long? = null,
  val accumulatedPausedMillis: Long = 0L,
  val notes: String? = null,
)

@Entity(
  tableName = "reading_progress_logs",
  foreignKeys = [
    ForeignKey(
      entity = ReadingSessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["sessionId"]),
    Index(value = ["recordedAtEpochMillis"]),
  ],
)
data class ReadingProgressLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sessionId: Long,
  val recordedAtEpochMillis: Long,
  val pageNumber: Int? = null,
  val progressPercent: Float,
  val deltaPages: Int = 0,
  val note: String? = null,
)

data class EditionWithCopies(
  @Embedded val edition: EditionEntity,
  @Relation(parentColumn = "id", entityColumn = "editionId")
  val copies: List<BookCopyEntity>,
)

data class WorkWithEditions(
  @Embedded val work: WorkEntity,
  @Relation(entity = EditionEntity::class, parentColumn = "id", entityColumn = "workId")
  val editions: List<EditionWithCopies>,
)

data class ContributorLinkWithNameRow(
  val linkId: Long,
  val workId: Long,
  val editionId: Long?,
  val contributorId: Long,
  val name: String,
  val role: ContributorRole,
  val displayOrder: Int,
)

data class LibraryBookSummaryRow(
  @Embedded val work: WorkEntity,
  @Embedded(prefix = "pe_") val primaryEdition: EditionEntity?,
  @Embedded(prefix = "pc_") val primaryCopy: BookCopyEntity?,
  @Embedded(prefix = "g_") val primaryGenre: GenreEntity?,
  val editionCount: Int,
  val copyCount: Int,
  val activeSessionId: Long? = null,
  val activeProgressPercent: Float?,
  val activeCurrentPage: Int?,
  val activeTotalPages: Int?,
  val activeRereadNumber: Int?,
  val activeCopyMediaType: CopyMediaType?,
  val activeCopyLabel: String?,
  val pendingSuggestionsCount: Int = 0,
)

data class DiaryEntryWithBookRow(
  @Embedded val entry: DiaryEntryEntity,
  val linkedBookTitle: String?,
  val linkedBookAuthor: String?,
)

data class ReadingProgressLogWithWorkRow(
  @Embedded val log: ReadingProgressLogEntity,
  val workId: Long,
)

class DipeConverters {
  @TypeConverter fun fromReadingStatus(value: ReadingStatus): String = value.name

  @TypeConverter fun toReadingStatus(value: String): ReadingStatus = ReadingStatus.fromName(value)

  @TypeConverter fun fromTypographyPreset(value: TypographyPreset?): String? = value?.name

  @TypeConverter
  fun toTypographyPreset(value: String?): TypographyPreset? =
    value?.let { TypographyPreset.fromName(it) }

  @TypeConverter fun fromCopyMediaType(value: CopyMediaType): String = value.name

  @TypeConverter
  fun toCopyMediaType(value: String): CopyMediaType = CopyMediaType.fromName(value)

  @TypeConverter fun fromEditionFormat(value: EditionFormat?): String? = value?.name

  @TypeConverter
  fun toEditionFormat(value: String?): EditionFormat? = EditionFormat.fromName(value)

  @TypeConverter fun fromShelfType(value: ShelfType): String = value.name

  @TypeConverter
  fun toShelfType(value: String): ShelfType =
    ShelfType.entries.firstOrNull { it.name == value } ?: ShelfType.CUSTOM

  @TypeConverter fun fromContributorRole(value: ContributorRole): String = value.name

  @TypeConverter
  fun toContributorRole(value: String): ContributorRole = ContributorRole.fromName(value)

  @TypeConverter fun fromCopyCondition(value: CopyCondition?): String? = value?.name

  @TypeConverter
  fun toCopyCondition(value: String?): CopyCondition? = CopyCondition.fromName(value)

  @TypeConverter fun fromCopyOrigin(value: CopyOrigin?): String? = value?.name

  @TypeConverter
  fun toCopyOrigin(value: String?): CopyOrigin? = CopyOrigin.fromName(value)

  @TypeConverter fun fromMetadataSuggestionStatus(value: MetadataSuggestionStatus): String = value.name

  @TypeConverter
  fun toMetadataSuggestionStatus(value: String): MetadataSuggestionStatus =
    MetadataSuggestionStatus.fromName(value)

  @TypeConverter fun fromMetadataSuggestionScope(value: MetadataSuggestionScope): String = value.name

  @TypeConverter
  fun toMetadataSuggestionScope(value: String): MetadataSuggestionScope =
    MetadataSuggestionScope.fromName(value)
}
