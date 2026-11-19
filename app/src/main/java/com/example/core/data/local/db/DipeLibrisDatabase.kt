package com.example.core.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.model.BibliographicMath

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
    GenreEntity::class,
    WorkEntity::class,
    EditionEntity::class,
    BookCopyEntity::class,
    ContributorEntity::class,
    ContributionLinkEntity::class,
    SearchCacheEntity::class,
    MetadataSuggestionEntity::class,
    LibraryFtsEntity::class,
    ShelfEntity::class,
    BookShelfCrossRef::class,
    DiaryEntryEntity::class,
    ReadingSessionEntity::class,
    ReadingProgressLogEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
@TypeConverters(DipeConverters::class)
abstract class DipeLibrisDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun editionDao(): EditionDao

  abstract fun copyDao(): CopyDao

  abstract fun genreDao(): GenreDao

  abstract fun contributorDao(): ContributorDao

  abstract fun searchCacheDao(): SearchCacheDao

  abstract fun metadataSuggestionDao(): MetadataSuggestionDao

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

          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `label` TEXT")
          db.execSQL(
            "ALTER TABLE `book_copies` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'PHYSICAL'"
          )
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `locationOrPlatform` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `acquisitionEpochMillis` INTEGER")
          db.execSQL(
            "ALTER TABLE `book_copies` ADD COLUMN `createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0"
          )

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

    val MIGRATION_3_4: Migration =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          // 1. Create and seed genres table
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `genres` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `slug` TEXT NOT NULL,
              `namePt` TEXT NOT NULL,
              `nameEn` TEXT NOT NULL,
              `isCustom` INTEGER NOT NULL DEFAULT 0,
              `createdAtEpochMillis` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
          )
          db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_genres_slug` ON `genres` (`slug`)")
          seedDefaultGenres(db)

          // 2. Recreate works with bibliographic fields and FK to genres(id)
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `works_new` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `title` TEXT NOT NULL,
              `author` TEXT NOT NULL,
              `subtitle` TEXT,
              `originalTitle` TEXT,
              `primaryGenreId` INTEGER,
              `activeSynopsis` TEXT,
              `synopsisLanguage` TEXT,
              `synopsisSource` TEXT,
              `isSynopsisCustomized` INTEGER NOT NULL DEFAULT 0,
              `externalWorkKey` TEXT,
              `userEditedMask` INTEGER NOT NULL DEFAULT 0,
              `status` TEXT NOT NULL,
              `isFavorite` INTEGER NOT NULL,
              `typographyPresetOverride` TEXT,
              `createdAtEpochMillis` INTEGER NOT NULL,
              `updatedAtEpochMillis` INTEGER NOT NULL,
              FOREIGN KEY(`primaryGenreId`) REFERENCES `genres`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            INSERT INTO `works_new` (
              `id`, `title`, `author`, `status`, `isFavorite`,
              `typographyPresetOverride`, `createdAtEpochMillis`, `updatedAtEpochMillis`
            )
            SELECT
              `id`, `title`, `author`, `status`, `isFavorite`,
              `typographyPresetOverride`, `createdAtEpochMillis`, `updatedAtEpochMillis`
            FROM `works`
            """.trimIndent()
          )
          db.execSQL("DROP TABLE `works`")
          db.execSQL("ALTER TABLE `works_new` RENAME TO `works`")
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_works_primaryGenreId` ON `works` (`primaryGenreId`)"
          )

          // 3. Expand editions losslessly
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `isbn10` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `isbn13` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `coverSource` TEXT")
          db.execSQL(
            "ALTER TABLE `editions` ADD COLUMN `isCoverCustomized` INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `editionLabel` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `seriesName` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `seriesVolume` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `synopsis` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `synopsisSource` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `categoriesJson` TEXT")
          db.execSQL("ALTER TABLE `editions` ADD COLUMN `externalIdsJson` TEXT")
          db.execSQL("CREATE INDEX IF NOT EXISTS `index_editions_isbn13` ON `editions` (`isbn13`)")
          db.execSQL("CREATE INDEX IF NOT EXISTS `index_editions_isbn10` ON `editions` (`isbn10`)")

          // Normalize existing edition ISBNs into isbn10/isbn13
          db.query("SELECT id, isbn FROM editions WHERE isbn IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
              val id = cursor.getLong(0)
              val rawIsbn = cursor.getString(1)
              val parsed = BibliographicMath.parseIsbn(rawIsbn)
              if (parsed != null) {
                val values =
                  ContentValues().apply {
                    put("isbn10", parsed.isbn10)
                    put("isbn13", parsed.isbn13)
                  }
                db.update("editions", SQLiteDatabase.CONFLICT_NONE, values, "id = ?", arrayOf(id.toString()))
              }
            }
          }

          // 4. Expand book_copies losslessly
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `condition` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `acquisitionOrigin` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `pricePaidMinorUnits` INTEGER")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `currencyCode` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `customCopyCode` TEXT")
          db.execSQL("ALTER TABLE `book_copies` ADD COLUMN `customAttributesJson` TEXT")

          // 5. Contributors & contribution links
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contributors` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `normalizedName` TEXT NOT NULL,
              `externalIdsJson` TEXT
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_contributors_normalizedName` ON `contributors` (`normalizedName`)"
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contribution_links` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `workId` INTEGER NOT NULL,
              `editionId` INTEGER,
              `contributorId` INTEGER NOT NULL,
              `role` TEXT NOT NULL,
              `displayOrder` INTEGER NOT NULL,
              FOREIGN KEY(`workId`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`editionId`) REFERENCES `editions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`contributorId`) REFERENCES `contributors`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contribution_links_workId` ON `contribution_links` (`workId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contribution_links_editionId` ON `contribution_links` (`editionId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contribution_links_contributorId` ON `contribution_links` (`contributorId`)"
          )

          backfillContributorsFromLegacyColumns(db)

          // 6. Search cache table
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_cache_entries` (
              `cacheKey` TEXT NOT NULL PRIMARY KEY,
              `queryNormalized` TEXT NOT NULL,
              `queryType` TEXT NOT NULL,
              `payloadJson` TEXT NOT NULL,
              `sourcesUsed` TEXT NOT NULL,
              `fetchedAtEpochMillis` INTEGER NOT NULL,
              `expiresAtEpochMillis` INTEGER NOT NULL,
              `lastAccessedEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_search_cache_entries_expiresAtEpochMillis` ON `search_cache_entries` (`expiresAtEpochMillis`)"
          )

          // 7. Metadata update suggestions table
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `metadata_update_suggestions` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `workId` INTEGER NOT NULL,
              `editionId` INTEGER,
              `scope` TEXT NOT NULL,
              `fieldName` TEXT NOT NULL,
              `currentValue` TEXT,
              `suggestedValue` TEXT NOT NULL,
              `sourceName` TEXT NOT NULL,
              `reason` TEXT NOT NULL,
              `confidenceScore` INTEGER NOT NULL,
              `status` TEXT NOT NULL,
              `createdAtEpochMillis` INTEGER NOT NULL,
              FOREIGN KEY(`workId`) REFERENCES `works`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`editionId`) REFERENCES `editions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_metadata_update_suggestions_workId` ON `metadata_update_suggestions` (`workId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_metadata_update_suggestions_editionId` ON `metadata_update_suggestions` (`editionId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_metadata_update_suggestions_status` ON `metadata_update_suggestions` (`status`)"
          )

          // 8. FTS4 virtual search table
          db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `library_fts` USING FTS4(
              `title` TEXT NOT NULL,
              `subtitle` TEXT NOT NULL,
              `authorsText` TEXT NOT NULL,
              `translatorsText` TEXT NOT NULL,
              `publisherText` TEXT NOT NULL,
              `isbnsText` TEXT NOT NULL,
              `genreText` TEXT NOT NULL,
              `notesText` TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            INSERT INTO `library_fts` (`rowid`, `title`, `subtitle`, `authorsText`, `translatorsText`, `publisherText`, `isbnsText`, `genreText`, `notesText`)
            SELECT
              w.`id`,
              w.`title`,
              '',
              w.`author`,
              COALESCE((SELECT GROUP_CONCAT(COALESCE(e.`translator`, ''), ' ') FROM `editions` e WHERE e.`workId` = w.`id`), ''),
              COALESCE((SELECT GROUP_CONCAT(COALESCE(e.`publisherOrNote`, ''), ' ') FROM `editions` e WHERE e.`workId` = w.`id`), ''),
              COALESCE((SELECT GROUP_CONCAT(COALESCE(e.`isbn`, '') || ' ' || COALESCE(e.`isbn13`, '') || ' ' || COALESCE(e.`isbn10`, ''), ' ') FROM `editions` e WHERE e.`workId` = w.`id`), ''),
              '',
              ''
            FROM `works` w
            """.trimIndent()
          )
        }

        private fun backfillContributorsFromLegacyColumns(db: SupportSQLiteDatabase) {
          fun getOrCreateContributorId(name: String): Long? {
            val trimmed = name.trim()
            val normalized = BibliographicMath.normalizeText(trimmed)
            if (trimmed.isEmpty() || normalized.isEmpty()) return null
            db.query("SELECT id FROM contributors WHERE normalizedName = ? LIMIT 1", arrayOf(normalized)).use { c ->
              if (c.moveToFirst()) return c.getLong(0)
            }
            val cv =
              ContentValues().apply {
                put("name", trimmed)
                put("normalizedName", normalized)
              }
            return db.insert("contributors", SQLiteDatabase.CONFLICT_IGNORE, cv).takeIf { it > 0 }
              ?: db.query("SELECT id FROM contributors WHERE normalizedName = ? LIMIT 1", arrayOf(normalized)).use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
              }
          }

          db.query("SELECT id, author FROM works").use { cursor ->
            while (cursor.moveToNext()) {
              val workId = cursor.getLong(0)
              val rawAuthor = cursor.getString(1).orEmpty()
              val parts = rawAuthor.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
              parts.forEachIndexed { index, part ->
                val contributorId = getOrCreateContributorId(part) ?: return@forEachIndexed
                val linkValues =
                  ContentValues().apply {
                    put("workId", workId)
                    putNull("editionId")
                    put("contributorId", contributorId)
                    put("role", if (index == 0) "AUTHOR" else "CO_AUTHOR")
                    put("displayOrder", index)
                  }
                db.insert("contribution_links", SQLiteDatabase.CONFLICT_IGNORE, linkValues)
              }
            }
          }

          db.query("SELECT id, workId, translator FROM editions WHERE translator IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
              val editionId = cursor.getLong(0)
              val workId = cursor.getLong(1)
              val rawTranslator = cursor.getString(2).orEmpty()
              val parts = rawTranslator.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
              parts.forEachIndexed { index, part ->
                val contributorId = getOrCreateContributorId(part) ?: return@forEachIndexed
                val linkValues =
                  ContentValues().apply {
                    put("workId", workId)
                    put("editionId", editionId)
                    put("contributorId", contributorId)
                    put("role", "TRANSLATOR")
                    put("displayOrder", 100 + index)
                  }
                db.insert("contribution_links", SQLiteDatabase.CONFLICT_IGNORE, linkValues)
              }
            }
          }
        }
      }

    fun seedDefaultGenres(db: SupportSQLiteDatabase) {
      val now = System.currentTimeMillis()
      DefaultGenres.seeds.forEach { seed ->
        val values =
          ContentValues().apply {
            put("slug", seed.slug)
            put("namePt", seed.namePt)
            put("nameEn", seed.nameEn)
            put("isCustom", 0)
            put("createdAtEpochMillis", now)
          }
        db.insert("genres", SQLiteDatabase.CONFLICT_IGNORE, values)
      }
    }

    /**
     * Ordered list of explicit schema migrations for database versions.
     */
    val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    val SEED_CALLBACK: Callback =
      object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
          super.onCreate(db)
          seedDefaultGenres(db)
        }
      }

    fun build(context: Context): DipeLibrisDatabase =
      Room.databaseBuilder(
          context.applicationContext,
          DipeLibrisDatabase::class.java,
          DATABASE_NAME,
        )
        .addMigrations(*MIGRATIONS)
        .addCallback(SEED_CALLBACK)
        .build()
  }
}
