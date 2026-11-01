package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v6 → v7 adds playlist artwork and the recently-played log; playlists and their songs survive. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun `playlists, their song order and favorites survive`() {
        helper.createDatabase(DB, 6).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
                    "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
                    "VALUES (1, 'Rich Flex', 'Drake/21 Savage', 'Her Loss', 10, 20, 1000, 'u', '/f', 1, 2022, 5, NULL, 1, NULL, 1, NULL, NULL, 1)",
            )
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Mine', 1, 1)")
            execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (1, 1, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_6_7)
        db.query("SELECT name, artwork_uri FROM playlists WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Mine", it.getString(0))
            assertNull(it.getString(1))
        }
        db.query("SELECT song_id, position FROM playlist_song_cross_ref WHERE playlist_id = 1").use {
            it.moveToFirst()
            assertEquals(1L, it.getLong(0))
            assertEquals(0, it.getInt(1))
        }
        db.query("SELECT is_favorite FROM songs WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM recently_played").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-6-7-test"
    }
}
