package com.erhu.erhu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve the library; old rows are backfilled from MediaStore on the Songs page. */
@Suppress("MagicNumber")
val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // A database can reach v15 by another path: development builds briefly used v15 for the
            // remote library tables, without the playlist display order. Add it if it is missing.
            if (!db.hasColumn("playlist_song_cross_ref", "sort_order")) {
                db.execSQL("ALTER TABLE `playlist_song_cross_ref` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `playlist_song_cross_ref` SET `sort_order` = `position`")
            }
            if (!db.hasColumn("songs", "date_added_ms")) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `date_added_ms` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

internal fun SupportSQLiteDatabase.hasColumn(
    table: String,
    column: String,
): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.any { it == column }
    }

/**
 * v16 -> v17: custom song artwork overrides and listening events for recap statistics.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_song_artwork` (
                `song_id` INTEGER NOT NULL,
                `file_path` TEXT NOT NULL,
                `mime_type` TEXT NOT NULL,
                `is_animated` INTEGER NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`song_id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listening_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `song_id` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `duration_ms` INTEGER NOT NULL,
                `is_counted_play` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_timestamp` ON `listening_events` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_song_id` ON `listening_events` (`song_id`)")
        db.execSQL(
            """
            INSERT INTO `listening_events` (`song_id`, `timestamp`, `duration_ms`, `is_counted_play`)
            SELECT `song_id`, `last_played_at`, `listened_ms`, 1
            FROM `listening_stats`
            WHERE `last_played_at` > 0
            """.trimIndent(),
        )
    }
}

/**
 * v14 â†’ v15: separate playlist display order from each entry's permanent position identity.
 * Copying the old position preserves the existing order, including gaps and duplicate songs,
 * while leaving transition keys and playback origins attached to their original entries.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `playlist_song_cross_ref` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `playlist_song_cross_ref` SET `sort_order` = `position`")
        }
    }

/**
 * v5 â†’ v6: credit/album/artist metadata columns, albumâ€“artist links, artwork and
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

/** v6 â†’ v7: user-chosen playlist artwork and the recently-played log (both kept across scans). */
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
 * v7 â†’ v8: artist artwork records where it came from. Existing rows become automatic artwork of
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
 * v8 â†’ v9: monthly listening statistics (Sound Capsule). Two new tables only; no existing
 * table, row or column is touched.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_8_9.forEach(db::execSQL)
        }
    }

/**
 * v10 â†’ v11: timestamp bookmarks and the waveform cache. Two new tables only; no existing table,
 * row or column is touched.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_10_11.forEach(db::execSQL)
        }
    }

private val STATEMENTS_10_11 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `track_bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `song_id` INTEGER NOT NULL, " +
            "`position_ms` INTEGER NOT NULL, `label` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_track_bookmarks_song_id` ON `track_bookmarks` (`song_id`)",
        "CREATE TABLE IF NOT EXISTS `waveform_cache` (`song_id` INTEGER NOT NULL, `source_uri` TEXT NOT NULL, " +
            "`file_size` INTEGER NOT NULL, " +
            "`date_modified` INTEGER NOT NULL, `format_version` INTEGER NOT NULL, `levels` BLOB NOT NULL, `created_at` INTEGER NOT NULL, " +
            "PRIMARY KEY(`song_id`))",
    )

/**
 * v11 â†’ v12: learned watermarks/junk tokens table for autonomous metadata cleaning.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_11_12.forEach(db::execSQL)
        }
    }

private val STATEMENTS_11_12 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `learned_watermarks` (`token` TEXT NOT NULL, `frequency_count` INTEGER NOT NULL DEFAULT 1, " +
            "`date_discovered` INTEGER NOT NULL, `is_active` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`token`))",
    )

/**
 * v12 â†’ v13: songs get a `bpm` column (the file's BPM tag), so the library and VYBE can use tempo.
 * Every song is marked for re-enrichment so the next library scan reads its BPM tag (the other
 * enriched values are read again from the same tags); nothing else changes.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `bpm` INTEGER")
            db.execSQL("UPDATE `songs` SET `is_enriched` = 0")
        }
    }

/**
 * v9 â†’ v10: playlist entries are keyed by (playlist, position) instead of (playlist, song), so a
 * playlist can hold the same song more than once (a saved queue). Every existing entry is kept in
 * its order; positions are renumbered 0..n-1 per playlist (a correlated count: SQLite on older
 * Android has no window functions). Nothing else changes.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_9_10.forEach(db::execSQL)
        }
    }

private val STATEMENTS_9_10 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref_new` (`playlist_id` INTEGER NOT NULL, `song_id` INTEGER NOT NULL, " +
            "`position` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `position`))",
        "INSERT INTO `playlist_song_cross_ref_new` (`playlist_id`, `song_id`, `position`) " +
            "SELECT o.`playlist_id`, o.`song_id`, (SELECT COUNT(*) FROM `playlist_song_cross_ref` p " +
            "WHERE p.`playlist_id` = o.`playlist_id` AND (p.`position` < o.`position` OR " +
            "(p.`position` = o.`position` AND p.`song_id` < o.`song_id`))) FROM `playlist_song_cross_ref` o",
        "DROP TABLE `playlist_song_cross_ref`",
        "ALTER TABLE `playlist_song_cross_ref_new` RENAME TO `playlist_song_cross_ref`",
        "CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlist_id` ON `playlist_song_cross_ref` (`playlist_id`)",
        "CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_song_id` ON `playlist_song_cross_ref` (`song_id`)",
    )

private val STATEMENTS_8_9 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `listening_stats` (`month_key` TEXT NOT NULL, `song_id` INTEGER NOT NULL, " +
            "`play_count` INTEGER NOT NULL, `listened_ms` INTEGER NOT NULL, `last_played_at` INTEGER NOT NULL, " +
            "PRIMARY KEY(`month_key`, `song_id`))",
        "CREATE INDEX IF NOT EXISTS `index_listening_stats_song_id` ON `listening_stats` (`song_id`)",
        "CREATE TABLE IF NOT EXISTS `listening_days` (`day` TEXT NOT NULL, `play_count` INTEGER NOT NULL, PRIMARY KEY(`day`))",
    )

/**
 * v13 -> v14 adds the animated_artwork table.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `animated_artwork` (
                    `album_id` INTEGER NOT NULL, 
                    `normalized_artist` TEXT NOT NULL, 
                    `normalized_album` TEXT NOT NULL, 
                    `catalog_album_id` TEXT, 
                    `storefront` TEXT, 
                    `square_file_name` TEXT, 
                    `tall_file_name` TEXT, 
                    `has_square` INTEGER, 
                    `has_tall` INTEGER, 
                    `status` TEXT NOT NULL, 
                    `updated_at_epoch_ms` INTEGER NOT NULL, 
                    PRIMARY KEY(`album_id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_animated_artwork_normalized_artist_normalized_album` ON `animated_artwork` (`normalized_artist`, `normalized_album`)",
            )
        }
    }

/**
 * v17 → v18: remote libraries. `remote_sources` are the connected accounts/servers and
 * `remote_tracks` the remote tracks erhu refers to by id. Nothing existing is touched.
 */
@Suppress("MagicNumber") // schema version numbers
val MIGRATION_17_18 =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS_17_18.forEach(db::execSQL)
            RemoteTrackIdSeed.seed(db)
        }
    }

private val STATEMENTS_17_18 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `remote_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`provider` TEXT NOT NULL, `name` TEXT NOT NULL, `base_url` TEXT, `account` TEXT, " +
            "`allow_insecure` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, `last_location` TEXT)",
        "CREATE TABLE IF NOT EXISTS `remote_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`source_id` INTEGER NOT NULL, `provider` TEXT NOT NULL, `remote_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration_ms` INTEGER NOT NULL, `track_number` INTEGER NOT NULL, " +
            "`disc_number` INTEGER NOT NULL, `mime_type` TEXT, `artwork_key` TEXT, `is_favorite` INTEGER NOT NULL, " +
            "`is_unavailable` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
            "FOREIGN KEY(`source_id`) REFERENCES `remote_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_remote_tracks_source_id_remote_id` ON `remote_tracks` (`source_id`, `remote_id`)",
    )

/**
 * Starts `remote_tracks` ids at RemoteIds.FIRST, far above any MediaStore id, wherever the table is
 * created empty: by the migration, on a fresh install, and after a destructive rebuild.
 */
object RemoteTrackIdSeed : androidx.room.RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) = seed(db)

    fun seed(db: SupportSQLiteDatabase) {
        // Raise the sequence to the remote range, never lower it: the tables may already hold remote tracks.
        val floor = com.erhu.erhu.core.model.RemoteIds.FIRST - 1
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'remote_tracks' AND seq < $floor")
        db.execSQL(
            "INSERT INTO sqlite_sequence (name, seq) SELECT 'remote_tracks', " +
                "MAX($floor, (SELECT COALESCE(MAX(id), 0) FROM remote_tracks)) " +
                "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'remote_tracks')",
        )
    }
}
