package com.example.core.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
  version = 2,
  exportSchema = true,
)
@TypeConverters(DipeConverters::class)
abstract class DipeLibrisDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun diaryDao(): DiaryDao

  abstract fun shelfDao(): ShelfDao

  companion object {
    const val DATABASE_NAME = "dipe_libris.db"

    val MIGRATION_1_2: Migration =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `diary_entries_new` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `workId` INTEGER NOT NULL,
              `content` TEXT NOT NULL,
              `createdAtEpochMillis` INTEGER NOT NULL,
              FOREIGN KEY(`workId`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            INSERT INTO `diary_entries_new` (`id`, `workId`, `content`, `createdAtEpochMillis`)
            SELECT d.`id`, d.`workId`, d.`content`, d.`createdAtEpochMillis`
            FROM `diary_entries` d
            INNER JOIN `works` w ON d.`workId` = w.`id`
            WHERE d.`workId` IS NOT NULL
            """.trimIndent()
          )
          db.execSQL("DROP TABLE `diary_entries`")
          db.execSQL("ALTER TABLE `diary_entries_new` RENAME TO `diary_entries`")
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_diary_entries_workId` ON `diary_entries` (`workId`)"
          )
        }
      }

    /**
     * Ordered list of explicit schema migrations for database versions.
     */
    val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
