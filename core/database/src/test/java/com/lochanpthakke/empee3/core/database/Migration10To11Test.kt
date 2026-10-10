package com.lochanpthakke.empee3.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v10 → v11 only adds the bookmark and waveform-cache tables: existing rows are untouched, the new tables start empty. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EmPee3Database::class.java)

    @Test
    fun `existing data survives and the new tables work`() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL("INSERT INTO playlists (id, name, created_at, updated_at, artwork_uri) VALUES (5, 'wack', 1, 2, NULL)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 1, 0)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 1, 1)")
            db.execSQL("INSERT INTO recently_played (kind, item_id, played_at) VALUES ('ALBUM', 20, 7)")
        }

        val db = helper.runMigrationsAndValidate(DB, 11, true, MIGRATION_10_11)

        assertEquals(
            listOf(listOf("5", "1", "0"), listOf("5", "1", "1")),
            dump(db, "SELECT playlist_id, song_id, position FROM playlist_song_cross_ref ORDER BY position"),
        )
        assertEquals(listOf(listOf("ALBUM", "20", "7")), dump(db, "SELECT * FROM recently_played"))
        assertEquals(emptyList<List<String?>>(), dump(db, "SELECT * FROM track_bookmarks"))
        assertEquals(emptyList<List<String?>>(), dump(db, "SELECT * FROM waveform_cache"))

        db.execSQL("INSERT INTO track_bookmarks (song_id, position_ms, label, created_at) VALUES (1, 61000, 'drop', 9)")
        db.execSQL(
            "INSERT INTO waveform_cache (song_id, source_uri, file_size, date_modified, format_version, levels, created_at) " +
                "VALUES (1, 'content://m/1', 100, 5, 1, X'00FF', 9)",
        )
        assertEquals(listOf(listOf("1", "1", "61000", "drop", "9")), dump(db, "SELECT * FROM track_bookmarks"))
        assertEquals(listOf(listOf("1", "2")), dump(db, "SELECT song_id, length(levels) FROM waveform_cache"))
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
        const val DB = "migration-10-11-test"
    }
}
