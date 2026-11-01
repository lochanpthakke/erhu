package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v12 -> v13 adds songs.bpm. Every song and all its values survive (genres, favorites); the new
 * column starts empty and every song is marked for re-enrichment, so the next scan reads its BPM tag.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration12To13Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun `songs keep their values and get an empty bpm to be read`() {
        helper.createDatabase(DB, 12).use { db ->
            db.execSQL(
                "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
                    "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) VALUES " +
                    "(1, 'EARFQUAKE', 'Tyler, The Creator', 'IGOR', 2, 3, 190000, 'content://m/1', '/m/e.mp3', 2, 2019, 5, NULL, 1, " +
                    "'lyrics', 1, NULL, 'hip hop', 1)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 13, true, MIGRATION_12_13)

        assertEquals(
            listOf(listOf("1", "EARFQUAKE", "hip hop", "lyrics", "1", "0", null)),
            dump(db, "SELECT id, title, genre, lyrics, is_favorite, is_enriched, bpm FROM songs"),
        )
        db.execSQL("UPDATE songs SET bpm = 80, is_enriched = 1 WHERE id = 1")
        assertEquals(listOf(listOf("80")), dump(db, "SELECT bpm FROM songs"))
    }

    private fun dump(
        db: SupportSQLiteDatabase,
        sql: String,
    ): List<List<String?>> =
        db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }

    private companion object {
        const val DB = "migration-12-13-test"
    }
}
