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
 * - Destructive fallback migrations are intentionally disabled so user libraries, editions,
 *   reading journeys, and diary notes are never silently dropped across app updates.
 * - Every future schema version increment MUST define an explicit [Migration] registered in
 *   [MIGRATIONS] and be covered by migration tests.
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
    ReadingProgressLogEntity::class,
  ],
  version = 3,
  exportSchema = true,
)
@TypeConverters(DipeConverters::class)
abstract class DipeLibrisDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun editionDao(): EditionDao

  abstract fun copyDao(): CopyDao

  abstract fun readingJourneyDao(): ReadingJourneyDao

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

    val MIGRATION_2_3: Migration =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          // 1. Expand editions table losslessly
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `language` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `format` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `translator` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `isPrimary` INTEGER NOT NULL DEFAULT 0")
          db.execSQL(
            "ALTER TABLE `editions` ADD COLUMN `createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL(
            """
            UPDATE `editions`
            SET `isPrimary` = 1
            WHERE `id` IN (SELECT MIN(`id`) FROM `editions` GROUP BY `workId`)
            """.trimIndent()
          )

          // 2. Expand book_copies table losslessly
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `label` TEXT")
          db.execSQL(
            "ALTER TABLE `book_copies` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'PHYSICAL'"
          )
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `locationOrPlatform` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `acquisitionEpochMillis` INTEGER")
          db.execSQL(
            "ALTER TABLE `book_copies` ADD COLUMN `createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0"
          )

          // 3. Upgrade reading_sessions preserving existing journeys and seeding active/finished v2 works
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_sessions_new` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `workId` INTEGER NOT NULL,
              `copyId` INTEGER,
              `status` TEXT NOT NULL,
              `rereadNumber` INTEGER NOT NULL,
              `currentPage` INTEGER NOT NULL,
              `totalPagesSnapshot` INTEGER,
              `progressPercent` REAL NOT NULL,
              `startEpochMillis` INTEGER NOT NULL,
              `endEpochMillis` INTEGER,
              `pausedAtEpochMillis` INTEGER,
              `accumulatedPausedMillis` INTEGER NOT NULL,
              `notes` TEXT,
              FOREIGN KEY(`workId`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`copyId`) REFERENCES `book_copies`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
          )

          db.execSQL(
            """
            INSERT INTO `reading_sessions_new` (
              `id`, `workId`, `copyId`, `status`, `rereadNumber`,
              `currentPage`, `totalPagesSnapshot`, `progressPercent`,
              `startEpochMillis`, `endEpochMillis`, `pausedAtEpochMillis`, `accumulatedPausedMillis`, `notes`
            )
            SELECT
              rs.`id`,
              rs.`workId`,
              (
                SELECT c.`id`
                FROM `book_copies` c
                INNER JOIN `editions` e ON c.`editionId` = e.`id`
                WHERE e.`workId` = rs.`workId`
                ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                LIMIT 1
              ),
              CASE
                WHEN rs.`endEpochMillis` IS NOT NULL THEN 'FINISHED'
                WHEN w.`status` IN ('READING', 'PAUSED', 'FINISHED', 'ABANDONED') THEN w.`status`
                ELSE 'READING'
              END,
              rs.`rereadNumber`,
              COALESCE(
                (
                  SELECT c.`currentPage`
                  FROM `book_copies` c
                  INNER JOIN `editions` e ON c.`editionId` = e.`id`
                  WHERE e.`workId` = rs.`workId`
                  ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                  LIMIT 1
                ),
                0
              ),
              (
                SELECT e.`totalPages`
                FROM `editions` e
                WHERE e.`workId` = rs.`workId`
                ORDER BY e.`isPrimary` DESC, e.`id` ASC
                LIMIT 1
              ),
              CASE WHEN rs.`endEpochMillis` IS NOT NULL THEN 100.0 ELSE 0.0 END,
              rs.`startEpochMillis`,
              rs.`endEpochMillis`,
              CASE WHEN rs.`endEpochMillis` IS NULL AND w.`status` = 'PAUSED' THEN w.`updatedAtEpochMillis` ELSE NULL END,
              0,
              NULL
            FROM `reading_sessions` rs
            INNER JOIN `works` w ON rs.`workId` = w.`id`
            """.trimIndent()
          )

          // Seed initial reading session for legacy v2 works marked READING/PAUSED/FINISHED without a session row
          db.execSQL(
            """
            INSERT INTO `reading_sessions_new` (
              `workId`, `copyId`, `status`, `rereadNumber`,
              `currentPage`, `totalPagesSnapshot`, `progressPercent`,
              `startEpochMillis`, `endEpochMillis`, `pausedAtEpochMillis`, `accumulatedPausedMillis`, `notes`
            )
            SELECT
              w.`id`,
              (
                SELECT c.`id`
                FROM `book_copies` c
                INNER JOIN `editions` e ON c.`editionId` = e.`id`
                WHERE e.`workId` = w.`id`
                ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                LIMIT 1
              ),
              w.`status`,
              1,
              CASE
                WHEN w.`status` = 'FINISHED' THEN COALESCE(
                  (
                    SELECT e.`totalPages`
                    FROM `editions` e
                    WHERE e.`workId` = w.`id`
                    ORDER BY e.`isPrimary` DESC, e.`id` ASC
                    LIMIT 1
                  ),
                  COALESCE(
                    (
                      SELECT c.`currentPage`
                      FROM `book_copies` c
                      INNER JOIN `editions` e ON c.`editionId` = e.`id`
                      WHERE e.`workId` = w.`id`
                      ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                      LIMIT 1
                    ),
                    0
                  )
                )
                ELSE COALESCE(
                  (
                    SELECT c.`currentPage`
                    FROM `book_copies` c
                    INNER JOIN `editions` e ON c.`editionId` = e.`id`
                    WHERE e.`workId` = w.`id`
                    ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                    LIMIT 1
                  ),
                  0
                )
              END,
              (
                SELECT e.`totalPages`
                FROM `editions` e
                WHERE e.`workId` = w.`id`
                ORDER BY e.`isPrimary` DESC, e.`id` ASC
                LIMIT 1
              ),
              CASE
                WHEN w.`status` = 'FINISHED' THEN 100.0
                WHEN (
                  SELECT e.`totalPages`
                  FROM `editions` e
                  WHERE e.`workId` = w.`id`
                  ORDER BY e.`isPrimary` DESC, e.`id` ASC
                  LIMIT 1
                ) > 0 THEN MIN(
                  100.0,
                  MAX(
                    0.0,
                    (
                      CAST(
                        COALESCE(
                          (
                            SELECT c.`currentPage`
                            FROM `book_copies` c
                            INNER JOIN `editions` e ON c.`editionId` = e.`id`
                            WHERE e.`workId` = w.`id`
                            ORDER BY e.`isPrimary` DESC, e.`id` ASC, c.`id` ASC
                            LIMIT 1
                          ),
                          0
                        ) AS REAL
                      ) * 100.0
                    ) / (
                      SELECT e.`totalPages`
                      FROM `editions` e
                      WHERE e.`workId` = w.`id`
                      ORDER BY e.`isPrimary` DESC, e.`id` ASC
                      LIMIT 1
                    )
                  )
                )
                ELSE 0.0
              END,
              w.`createdAtEpochMillis`,
              CASE WHEN w.`status` = 'FINISHED' THEN w.`updatedAtEpochMillis` ELSE NULL END,
              CASE WHEN w.`status` = 'PAUSED' THEN w.`updatedAtEpochMillis` ELSE NULL END,
              0,
              NULL
            FROM `works` w
            WHERE w.`status` IN ('READING', 'PAUSED', 'FINISHED')
              AND NOT EXISTS (
                SELECT 1 FROM `reading_sessions_new` rs WHERE rs.`workId` = w.`id`
              )
            """.trimIndent()
          )

          db.execSQL("DROP TABLE `reading_sessions`")
          db.execSQL("ALTER TABLE `reading_sessions_new` RENAME TO `reading_sessions`")
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_workId` ON `reading_sessions` (`workId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_copyId` ON `reading_sessions` (`copyId`)"
          )

          // 4. Create reading_progress_logs table
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_progress_logs` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `sessionId` INTEGER NOT NULL,
              `recordedAtEpochMillis` INTEGER NOT NULL,
              `pageNumber` INTEGER,
              `progressPercent` REAL NOT NULL,
              `deltaPages` INTEGER NOT NULL,
              `note` TEXT,
              FOREIGN KEY(`sessionId`) REFERENCES `reading_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_progress_logs_sessionId` ON `reading_progress_logs` (`sessionId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_progress_logs_recordedAtEpochMillis` ON `reading_progress_logs` (`recordedAtEpochMillis`)"
          )
        }
      }

    /**
     * Ordered list of explicit schema migrations for database versions.
     */
    val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
