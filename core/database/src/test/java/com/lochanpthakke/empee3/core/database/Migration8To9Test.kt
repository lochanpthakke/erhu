package com.lochanpthakke.empee3.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v8 → v9 only adds the Sound Capsule tables. Every existing row of every table survives
 * unchanged (favorites, playlists, artwork, credits, aliases, history); the new tables start
 * empty and accept a play.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration8To9Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EmPee3Database::class.java)

    private val tables =
        listOf(
            "songs",
            "albums",
            "artists",
            "playlists",
            "playlist_song_cross_ref",
            "song_artist_cross_ref",
            "album_artist_cross_ref",
            "artist_artwork",
            "artist_alias",
            "unverified_credit_segment",
            "metadata_processing",
            "recently_played",
        )

    @Test
    fun `all existing data survives and the listening tables start empty`() {
        val before =
            helper.createDatabase(DB, 8).use { db ->
                seed(db)
                tables.associateWith { dump(db, "SELECT * FROM $it") }
            }

        val db = helper.runMigrationsAndValidate(DB, 9, true, MIGRATION_8_9)

        tables.forEach { assertEquals("table $it", before[it], dump(db, "SELECT * FROM $it")) }
        assertTrue(before.getValue("songs").isNotEmpty())
        assertTrue(before.getValue("playlist_song_cross_ref").isNotEmpty())
        assertEquals(emptyList<List<String?>>(), dump(db, "SELECT * FROM listening_stats"))
        assertEquals(emptyList<List<String?>>(), dump(db, "SELECT * FROM listening_days"))

        db.execSQL(
            "INSERT INTO listening_stats (month_key, song_id, play_count, listened_ms, last_played_at) VALUES ('2026-10', 1, 1, 0, 9)",
        )
        db.execSQL("INSERT INTO listening_days (day, play_count) VALUES ('2026-10-06', 1)")
        assertEquals(listOf(listOf("2026-10", "1", "1", "0", "9")), dump(db, "SELECT * FROM listening_stats"))
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
                "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
                "VALUES (1, 'Rich Flex', 'Drake/21 Savage', 'Her Loss', 10, 20, 1000, 'u', '/f', 1, 2022, 5, NULL, 1, NULL, 1, NULL, NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO albums (id, title, artist, artist_id, song_count, year, artwork_uri, is_compilation, album_artist_inconsistent, grouping_key) " +
                "VALUES (20, 'Her Loss', 'Drake & 21 Savage', 10, 16, 2022, 'content://art/20', 0, 0, 'dir:/m')",
        )
        db.execSQL(
            "INSERT INTO artists (id, name, song_count, album_count, artwork_uri, artist_key, is_unresolved) " +
                "VALUES (10, 'Drake', 33, 2, 'https://img/drake.jpg', 'drake', 0)",
        )
        db.execSQL("INSERT INTO playlists (id, name, created_at, updated_at, artwork_uri) VALUES (5, 'wack', 1, 2, 'file:///p/wack.jpg')")
        db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 1, 0)")
        db.execSQL(
            "INSERT INTO song_artist_cross_ref (song_id, artist_id, role, position, credit_text, is_unresolved) " +
                "VALUES (1, 10, 'PRIMARY', 0, 'Drake', 0)",
        )
        db.execSQL("INSERT INTO album_artist_cross_ref (album_id, artist_id, position) VALUES (20, 10, 0)")
        db.execSQL(
            "INSERT INTO artist_artwork (artist_key, artwork_uri, updated_at, source, provider_artist_id, matcher_version) " +
                "VALUES ('drake', 'https://img/drake.jpg', 1791059962417, 'MANUAL', 246791, 2)",
        )
        db.execSQL("INSERT INTO artist_alias (alias_key, canonical_key, source, created_at) VALUES ('a', 'b', 'MANUAL', 1)")
        db.execSQL("INSERT INTO unverified_credit_segment (song_id, artist_id, segment_key, segment_index) VALUES (1, 99, 'kanye west', 0)")
        db.execSQL("INSERT INTO recently_played (kind, item_id, played_at) VALUES ('ALBUM', 20, 7)")
    }

    /** Every row of [sql] as strings (null kept), in a stable order. */
    private fun dump(
        db: SupportSQLiteDatabase,
        sql: String,
    ): List<List<String?>> =
        db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }.sortedBy { it.joinToString("|") }

    private companion object {
        const val DB = "migration-8-9-test"
    }
}
