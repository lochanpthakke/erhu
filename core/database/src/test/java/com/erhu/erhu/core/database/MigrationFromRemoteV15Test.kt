package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.core.model.RemoteIds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A development build used v15 for the remote library tables, without the playlist display order.
 * A phone left on that database must upgrade to the current schema without losing anything.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationFromRemoteV15Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun `the remote-tables v15 database reaches the current schema with its data intact`() {
        helper.createDatabase(DB, 14).use { db ->
            // What the development build's 14 -> 15 did.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `remote_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`provider` TEXT NOT NULL, `name` TEXT NOT NULL, `base_url` TEXT, `account` TEXT, " +
                    "`allow_insecure` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, `last_location` TEXT)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `remote_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`source_id` INTEGER NOT NULL, `provider` TEXT NOT NULL, `remote_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration_ms` INTEGER NOT NULL, `track_number` INTEGER NOT NULL, " +
                    "`disc_number` INTEGER NOT NULL, `mime_type` TEXT, `artwork_key` TEXT, `is_favorite` INTEGER NOT NULL, " +
                    "`is_unavailable` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`source_id`) REFERENCES `remote_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_remote_tracks_source_id_remote_id` ON `remote_tracks` (`source_id`, `remote_id`)",
            )
            db.execSQL("INSERT INTO remote_sources (provider, name, allow_insecure, added_at) VALUES ('subsonic', 'Home', 0, 1)")
            db.execSQL(
                "INSERT INTO remote_tracks (id, source_id, provider, remote_id, title, artist, album, duration_ms, track_number, " +
                    "disc_number, is_favorite, is_unavailable, updated_at) VALUES (${RemoteIds.FIRST + 4}, 1, 'subsonic', 'a', 't', '', '', 0, 0, 1, 0, 0, 1)",
            )
            db.execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Mix', 1, 1)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (1, 10, 0), (1, 11, 3)")
            db.version = 15
        }

        helper.runMigrationsAndValidate(DB, 18, true, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18).use { db ->
            db.query("SELECT position, sort_order FROM playlist_song_cross_ref ORDER BY position").use { cursor ->
                val rows = generateSequence { if (cursor.moveToNext()) cursor.getInt(0) to cursor.getInt(1) else null }.toList()
                assertEquals(listOf(0 to 0, 3 to 3), rows)
            }
            db.query("SELECT COUNT(*) FROM remote_tracks").use { assertEquals(1, it.run { moveToFirst(); getInt(0) }) }
            // The next remote track continues after the existing one instead of reusing an id.
            db.query("SELECT seq FROM sqlite_sequence WHERE name = 'remote_tracks'").use {
                it.moveToFirst()
                assertEquals(RemoteIds.FIRST + 4, it.getLong(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-remote-v15"
    }
}
