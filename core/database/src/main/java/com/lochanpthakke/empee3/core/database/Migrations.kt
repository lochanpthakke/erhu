package com.lochanpthakke.empee3.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: credit/album/artist metadata columns, album–artist links, artwork and
 * alias tables kept across scans, unverified credit segments. Derived rows are
 * left in place; the next scan rebuilds them. Artwork is carried over by name key.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_5_6.forEach(db::execSQL)
        }
    }

private val STATEMENTS_5_6 =
    listOf(
        "ALTER TABLE `song_artist_cross_ref` ADD COLUMN `credit_text` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `song_artist_cross_ref` ADD COLUMN `is_unresolved` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `albums` ADD COLUMN `is_compilation` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `albums` ADD COLUMN `album_artist_inconsistent` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `albums` ADD COLUMN `grouping_key` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `artists` ADD COLUMN `artist_key` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `artists` ADD COLUMN `is_unresolved` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `album_artist_cross_ref` (`album_id` INTEGER NOT NULL, `artist_id` INTEGER NOT NULL, " +
            "`position` INTEGER NOT NULL, PRIMARY KEY(`album_id`, `artist_id`))",
        "CREATE INDEX IF NOT EXISTS `index_album_artist_cross_ref_artist_id` ON `album_artist_cross_ref` (`artist_id`)",
        "CREATE TABLE IF NOT EXISTS `artist_artwork` (`artist_key` TEXT NOT NULL, `artwork_uri` TEXT NOT NULL, " +
            "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`artist_key`))",
        "CREATE TABLE IF NOT EXISTS `artist_alias` (`alias_key` TEXT NOT NULL, `canonical_key` TEXT NOT NULL, " +
            "`source` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`alias_key`))",
        "CREATE TABLE IF NOT EXISTS `unverified_credit_segment` (`song_id` INTEGER NOT NULL, `artist_id` INTEGER NOT NULL, " +
            "`segment_key` TEXT NOT NULL, `segment_index` INTEGER NOT NULL, PRIMARY KEY(`song_id`, `artist_id`, `segment_index`))",
        "CREATE INDEX IF NOT EXISTS `index_unverified_credit_segment_segment_key` ON `unverified_credit_segment` (`segment_key`)",
        "INSERT OR REPLACE INTO `artist_artwork` (`artist_key`, `artwork_uri`, `updated_at`) " +
            "SELECT lower(trim(`name`)), `artwork_uri`, 0 FROM `artists` WHERE `artwork_uri` IS NOT NULL",
        "UPDATE `artists` SET `artist_key` = lower(trim(`name`))",
    )

/** v6 → v7: user-chosen playlist artwork and the recently-played log (both kept across scans). */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_6_7.forEach(db::execSQL)
        }
    }

private val STATEMENTS_6_7 =
    listOf(
        "ALTER TABLE `playlists` ADD COLUMN `artwork_uri` TEXT",
        "CREATE TABLE IF NOT EXISTS `recently_played` (`kind` TEXT NOT NULL, `item_id` INTEGER NOT NULL, " +
            "`played_at` INTEGER NOT NULL, PRIMARY KEY(`kind`, `item_id`))",
        "CREATE INDEX IF NOT EXISTS `index_recently_played_played_at` ON `recently_played` (`played_at`)",
    )

/**
 * v7 → v8: artist artwork records where it came from. Existing rows become automatic artwork of
 * matcher 0, so the new matcher re-checks them once; nothing is deleted or rewritten.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_7_8.forEach(db::execSQL)
        }
    }

private val STATEMENTS_7_8 =
    listOf(
        "ALTER TABLE `artist_artwork` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'AUTO'",
        "ALTER TABLE `artist_artwork` ADD COLUMN `provider_artist_id` INTEGER",
        "ALTER TABLE `artist_artwork` ADD COLUMN `matcher_version` INTEGER NOT NULL DEFAULT 0",
    )

/**
 * v8 → v9: monthly listening statistics (Sound Capsule). Two new tables only; no existing
 * table, row or column is touched.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_8_9.forEach(db::execSQL)
        }
    }

private val STATEMENTS_8_9 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `listening_stats` (`month_key` TEXT NOT NULL, `song_id` INTEGER NOT NULL, " +
            "`play_count` INTEGER NOT NULL, `listened_ms` INTEGER NOT NULL, `last_played_at` INTEGER NOT NULL, " +
            "PRIMARY KEY(`month_key`, `song_id`))",
        "CREATE INDEX IF NOT EXISTS `index_listening_stats_song_id` ON `listening_stats` (`song_id`)",
        "CREATE TABLE IF NOT EXISTS `listening_days` (`day` TEXT NOT NULL, `play_count` INTEGER NOT NULL, PRIMARY KEY(`day`))",
    )
