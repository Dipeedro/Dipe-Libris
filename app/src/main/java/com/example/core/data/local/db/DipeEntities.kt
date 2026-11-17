package com.example.core.data.local.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.example.core.model.ReadingStatus
import com.example.core.model.ShelfType
import com.example.core.model.TypographyPreset

@Entity(tableName = "works")
data class WorkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val author: String,
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
  indices = [Index(value = ["workId"])],
)
data class EditionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val publisherOrNote: String? = null,
  val publicationYear: Int? = null,
  val totalPages: Int? = null,
  val isbn: String? = null,
  val coverUri: String? = null,
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
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [Index(value = ["workId"])],
)
data class DiaryEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long? = null,
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
    )
  ],
  indices = [Index(value = ["workId"])],
)
data class ReadingSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val workId: Long,
  val startEpochMillis: Long,
  val endEpochMillis: Long? = null,
  val rereadNumber: Int = 1,
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

data class DiaryEntryWithBookRow(
  @Embedded val entry: DiaryEntryEntity,
  val linkedBookTitle: String?,
  val linkedBookAuthor: String?,
)

class DipeConverters {
  @TypeConverter fun fromReadingStatus(value: ReadingStatus): String = value.name

  @TypeConverter fun toReadingStatus(value: String): ReadingStatus = ReadingStatus.fromName(value)

  @TypeConverter fun fromTypographyPreset(value: TypographyPreset?): String? = value?.name

  @TypeConverter
  fun toTypographyPreset(value: String?): TypographyPreset? =
    value?.let { TypographyPreset.fromName(it) }

  @TypeConverter fun fromShelfType(value: ShelfType): String = value.name

  @TypeConverter
  fun toShelfType(value: String): ShelfType =
    ShelfType.entries.firstOrNull { it.name == value } ?: ShelfType.CUSTOM
}
