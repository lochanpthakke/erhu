package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.core.model.RemoteIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Remote folders in the Library: the upgrade adds them without touching the remote tracks already known. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration18To19Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun `known remote tracks survive and start outside the Library`() {
        helper.createDatabase(DB, 18).use { db ->
            db.execSQL("INSERT INTO remote_sources (provider, name, allow_insecure, added_at) VALUES ('drive', 'me', 0, 1)")
            db.execSQL(
                "INSERT INTO remote_tracks (id, source_id, provider, remote_id, title, artist, album, duration_ms, track_number, " +
                    "disc_number, is_favorite, is_unavailable, updated_at) VALUES (${RemoteIds.FIRST + 2}, 1, 'drive', 'f', 't', '', '', 0, 0, 1, 1, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 19, true, MIGRATION_18_19).use { db ->
            db.query("SELECT title, is_favorite, library_folder_id FROM remote_tracks").use {
                assertTrue(it.moveToFirst())
                assertEquals("t", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertTrue(it.isNull(2))
            }
            db.execSQL("INSERT INTO remote_library_folders (source_id, folder_id, name, added_at, synced_at) VALUES (1, 'songs', 'songs', 1, 0)")
            // Removing the account removes its Library folders too.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM remote_sources")
            db.query("SELECT COUNT(*) FROM remote_library_folders").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun `v20 adds genre, year and lyrics, with every known track still to be looked at`() {
        helper.createDatabase(DB_20, 19).use { db ->
            db.execSQL("INSERT INTO remote_sources (provider, name, allow_insecure, added_at) VALUES ('drive', 'me', 0, 1)")
            db.execSQL(
                "INSERT INTO remote_tracks (id, source_id, provider, remote_id, title, artist, album, duration_ms, track_number, " +
                    "disc_number, is_favorite, is_unavailable, updated_at) VALUES (${RemoteIds.FIRST + 2}, 1, 'drive', 'f', 't', '', '', 0, 0, 1, 0, 0, 1)",
            )
        }
        helper.runMigrationsAndValidate(DB_20, 20, true, MIGRATION_19_20).use { db ->
            db.query("SELECT genre, year, lyrics, details_version FROM remote_tracks").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0))
                assertEquals(0, it.getInt(1))
                assertTrue(it.isNull(2))
                assertEquals(0, it.getInt(3))
            }
        }
    }

    private companion object {
        const val DB_20 = "migration-19-20"
        const val DB = "migration-18-19"
    }
}
