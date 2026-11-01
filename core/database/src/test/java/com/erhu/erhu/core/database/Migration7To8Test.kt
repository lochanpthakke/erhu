package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v7 → v8 only adds artwork provenance columns. Every existing row of every table survives
 * unchanged; artwork rows become automatic artwork of matcher 0 (eligible for the one-time re-check).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    private val tables =
        listOf(
            "songs",
            "albums",
            "artists",
            "playlists",
            "playlist_song_cross_ref",
            "song_artist_cross_ref",
            "album_artist_cross_ref",
            "artist_alias",
            "unverified_credit_segment",
            "recently_played",
        )

    @Test
    fun `all existing data survives and artwork rows become automatic matcher-0 rows`() {
        val before =
            helper.createDatabase(DB, 7).use { db ->
                seed(db)
                tables.associateWith { dump(db, "SELECT * FROM $it") } +
                    ("artist_artwork" to dump(db, "SELECT artist_key, artwork_uri, updated_at FROM artist_artwork"))
            }

        val db = helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_7_8)

        tables.forEach { assertEquals("table $it", before[it], dump(db, "SELECT * FROM $it")) }
        assertEquals(before["artist_artwork"], dump(db, "SELECT artist_key, artwork_uri, updated_at FROM artist_artwork"))
        assertEquals(
            listOf(
                listOf("drake", "AUTO", null, "0"),
                listOf("kanye west", "AUTO", null, "0"),
                listOf("too \$hort", "AUTO", null, "0"),
            ),
            dump(db, "SELECT artist_key, source, provider_artist_id, matcher_version FROM artist_artwork ORDER BY artist_key"),
        )
        assertTrue(before.getValue("playlist_song_cross_ref").isNotEmpty())
    }

    private fun seed(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
                "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
                "VALUES (1, 'Rich Flex', 'Drake/21 Savage', 'Her Loss', 10, 20, 1000, 'u', '/f', 1, 2022, 5, NULL, 1, NULL, 1, NULL, NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO albums (id, title, artist, artist_id, song_count, year, artwork_uri, is_compilation, album_artist_inconsistent, grouping_key) " +
                "VALUES (20, 'Her Loss', 'Drake & 21 Savage', 10, 16, 2022, NULL, 0, 0, 'dir:/m')",
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
        db.execSQL("INSERT INTO artist_alias (alias_key, canonical_key, source, created_at) VALUES ('a', 'b', 'MANUAL', 1)")
        db.execSQL("INSERT INTO unverified_credit_segment (song_id, artist_id, segment_key, segment_index) VALUES (1, 99, 'kanye west', 0)")
        db.execSQL("INSERT INTO recently_played (kind, item_id, played_at) VALUES ('ALBUM', 20, 7)")
        db.execSQL("INSERT INTO artist_artwork (artist_key, artwork_uri, updated_at) VALUES ('drake', 'https://img/drake.jpg', 1791059962417)")
        db.execSQL("INSERT INTO artist_artwork (artist_key, artwork_uri, updated_at) VALUES ('kanye west', 'https://img/kanye.jpg', 0)")
        db.execSQL("INSERT INTO artist_artwork (artist_key, artwork_uri, updated_at) VALUES ('too \$hort', 'NONE:2', 1791059964569)")
    }

    /** Every row of [sql] as strings (null kept), in a stable order. */
    private fun dump(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): List<List<String?>> =
        db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }.sortedBy { it.joinToString("|") }

    private companion object {
        const val DB = "migration-7-8-test"
    }
}
