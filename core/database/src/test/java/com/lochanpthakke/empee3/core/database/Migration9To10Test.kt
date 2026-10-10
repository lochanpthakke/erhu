package com.lochanpthakke.empee3.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v9 → v10 re-keys playlist entries by position: every entry survives in its order (positions
 * renumbered 0..n-1 per playlist), other tables are untouched, and afterwards a playlist can hold
 * the same song twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration9To10Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EmPee3Database::class.java)

    @Test
    fun `entries keep their order and duplicates become possible`() {
        helper.createDatabase(DB, 9).use { db ->
            db.execSQL("INSERT INTO playlists (id, name, created_at, updated_at, artwork_uri) VALUES (5, 'wack', 1, 2, NULL)")
            db.execSQL("INSERT INTO playlists (id, name, created_at, updated_at, artwork_uri) VALUES (6, 'other', 1, 2, NULL)")
            // Gaps after removals, and an (unexpected) tie on position 4: order by position, then song.
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 30, 0)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 10, 4)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 20, 4)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 40, 9)")
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (6, 10, 3)")
            db.execSQL("INSERT INTO recently_played (kind, item_id, played_at) VALUES ('PLAYLIST', 5, 7)")
        }

        val db = helper.runMigrationsAndValidate(DB, 10, true, MIGRATION_9_10)

        assertEquals(
            listOf(listOf("5", "30", "0"), listOf("5", "10", "1"), listOf("5", "20", "2"), listOf("5", "40", "3"), listOf("6", "10", "0")),
            dump(db, "SELECT playlist_id, song_id, position FROM playlist_song_cross_ref ORDER BY playlist_id, position"),
        )
        assertEquals(listOf(listOf("5", "wack"), listOf("6", "other")), dump(db, "SELECT id, name FROM playlists ORDER BY id"))
        assertEquals(listOf(listOf("PLAYLIST", "5", "7")), dump(db, "SELECT * FROM recently_played"))

        // The same song again, at a new position: allowed now.
        db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 30, 4)")
        assertEquals(listOf(listOf("2")), dump(db, "SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlist_id = 5 AND song_id = 30"))
        // Two entries can never share a position.
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 99, 4)")
        }
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
        const val DB = "migration-9-10-test"
    }
}
