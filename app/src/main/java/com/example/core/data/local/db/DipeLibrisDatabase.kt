package com.example.core.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Room database for Dipe Libris.
 *
 * Migration policy:
 * - [exportSchema] is enabled and schemas are exported to `app/schemas` as the source of truth.
 * - Destructive fallback migrations are intentionally disabled so user libraries and diary notes
 *   are never silently dropped across app updates.
 * - Every future schema version increment (v1 -> v2 -> ...) MUST define an explicit [Migration]
 *   registered in [MIGRATIONS] (or a verified AutoMigration) and be covered by migration tests.
 */
@Database(
  entities = [
    WorkEntity::class,
    EditionEntity::class,
    BookCopyEntity::class,
    ShelfEntity::class,
    BookShelfCrossRef::class,
    DiaryEntryEntity::class,
    ReadingSessionEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(DipeConverters::class)
abstract class DipeLibrisDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun diaryDao(): DiaryDao

  abstract fun shelfDao(): ShelfDao

  companion object {
    const val DATABASE_NAME = "dipe_libris.db"

    /**
     * Ordered list of explicit schema migrations for future database versions.
     * Example for Phase 2: `Migration(1, 2) { db -> ... }`.
     */
    val MIGRATIONS: Array<Migration> = emptyArray()

    fun build(context: Context): DipeLibrisDatabase =
      Room.databaseBuilder(
          context.applicationContext,
          DipeLibrisDatabase::class.java,
          DATABASE_NAME,
        )
        .addMigrations(*MIGRATIONS)
        .build()
  }
}
