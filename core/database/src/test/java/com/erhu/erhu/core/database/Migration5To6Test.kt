package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun `favorites, playlists and artist artwork survive`() {
        helper.createDatabase(DB, 5).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
                    "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
                    "VALUES (1, 'Rich Flex', 'Drake/21 Savage', 'Her Loss', 10, 20, 1000, 'u', '/f', 1, 2022, 5, NULL, 1, NULL, 1, NULL, NULL, 1)",
            )
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Mine', 1, 1)")
            execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (1, 1, 0)")
            execSQL("INSERT INTO artists (id, name, song_count, album_count, artwork_uri) VALUES (10, 'Drake', 1, 1, 'https://img/drake.jpg')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6)
        db.query("SELECT is_favorite FROM songs WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM playlist_song_cross_ref").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT artwork_uri FROM artist_artwork WHERE artist_key = 'drake'").use {
            it.moveToFirst()
            assertEquals("https://img/drake.jpg", it.getString(0))
        }
        db.query("SELECT artist_key FROM artists WHERE id = 10").use {
            it.moveToFirst()
            assertEquals("drake", it.getString(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
