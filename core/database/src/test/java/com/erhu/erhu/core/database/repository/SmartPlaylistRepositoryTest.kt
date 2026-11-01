package com.erhu.erhu.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.model.SmartPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** Smart playlists are computed from play history, favorites and library data, deterministically. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SmartPlaylistRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = START
    private val stats = ListeningStatsRepository(db.listeningStatsDao(), { now }, { ZoneOffset.UTC })
    private val smart = SmartPlaylistRepository(db.listeningStatsDao(), db.songDao())

    @Before
    fun seed() {
        // id, title, date modified, favorite
        listOf(
            song(1, "Alpha", modified = 100, favorite = true),
            song(2, "Bravo", modified = 300, favorite = false),
            song(3, "Charlie", modified = 200, favorite = true),
            song(4, "Delta", modified = 300, favorite = false),
            song(5, "Echo", modified = 50, favorite = false),
        ).forEach(db.openHelper.writableDatabase::execSQL)
    }

    @After
    fun close() = db.close()

    private fun ids(playlist: SmartPlaylist) = runBlocking { smart.observe(playlist).first().map { it.id } }

    private fun play(
        songId: Long,
        times: Int = 1,
    ) = runBlocking {
        repeat(times) {
            stats.recordPlay(songId)
            now += 1_000L
        }
    }

    @Test
    fun `an untouched library has nothing played and every song never played`() {
        assertEquals(emptyList<Long>(), ids(SmartPlaylist.MOST_PLAYED))
        assertEquals(emptyList<Long>(), ids(SmartPlaylist.RECENTLY_PLAYED))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(SmartPlaylist.NEVER_PLAYED))
    }

    @Test
    fun `most played is ordered by total plays across months, ties by the latest play`() {
        play(3, times = 2)
        play(1, times = 3)
        play(2, times = 2) // ties with 3, played later: first
        now += 40L * DAY_MS // next month: still counted
        play(3)
        assertEquals(listOf(3L, 1L, 2L), ids(SmartPlaylist.MOST_PLAYED))
    }

    @Test
    fun `equal plays at the same moment fall back to title then id`() {
        val fixed = ListeningStatsRepository(db.listeningStatsDao(), { 5L }, { ZoneOffset.UTC })
        runBlocking {
            fixed.recordPlay(4)
            fixed.recordPlay(2)
        }
        assertEquals(listOf(2L, 4L), ids(SmartPlaylist.MOST_PLAYED))
        assertEquals(listOf(2L, 4L), ids(SmartPlaylist.RECENTLY_PLAYED))
    }

    @Test
    fun `recently played is ordered by the latest play`() {
        play(1)
        play(3)
        play(1)
        assertEquals(listOf(1L, 3L), ids(SmartPlaylist.RECENTLY_PLAYED))
    }

    @Test
    fun `listening time without a counted play is not a play`() {
        runBlocking { stats.addListeningTime(5, 30_000L) }
        assertEquals(emptyList<Long>(), ids(SmartPlaylist.MOST_PLAYED))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(SmartPlaylist.NEVER_PLAYED))
    }

    @Test
    fun `never played drops songs once they are played`() {
        play(2)
        play(4)
        assertEquals(listOf(1L, 3L, 5L), ids(SmartPlaylist.NEVER_PLAYED))
    }

    @Test
    fun `recently added is newest first with deterministic ties`() {
        assertEquals(listOf(4L, 2L, 3L, 1L, 5L), ids(SmartPlaylist.RECENTLY_ADDED))
    }

    @Test
    fun `favorites are the songs marked favorite`() {
        assertEquals(listOf(1L, 3L), ids(SmartPlaylist.FAVORITES))
    }

    @Test
    fun `songs removed from the library leave every smart playlist`() {
        play(2, times = 2)
        play(3)
        db.openHelper.writableDatabase.execSQL("DELETE FROM songs WHERE id IN (2, 3)")
        assertEquals(emptyList<Long>(), ids(SmartPlaylist.MOST_PLAYED))
        assertEquals(emptyList<Long>(), ids(SmartPlaylist.RECENTLY_PLAYED))
        assertEquals(listOf(1L, 4L, 5L), ids(SmartPlaylist.NEVER_PLAYED))
        assertEquals(listOf(1L), ids(SmartPlaylist.FAVORITES))
        assertEquals(listOf(4L, 1L, 5L), ids(SmartPlaylist.RECENTLY_ADDED))
    }

    @Test
    fun `an empty library gives empty smart playlists`() {
        db.openHelper.writableDatabase.execSQL("DELETE FROM songs")
        SmartPlaylist.entries.forEach { assertEquals(emptyList<Long>(), ids(it)) }
    }

    private companion object {
        const val START = 1_790_000_000_000L
        const val DAY_MS = 86_400_000L

        fun song(
            id: Long,
            title: String,
            modified: Long,
            favorite: Boolean,
        ) = "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
            "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
            "VALUES ($id, '$title', 'Artist', 'Album', 0, 0, 200000, 'content://s/$id', '/m/$id', $id, 2020, " +
            "$modified, NULL, 1, NULL, 1, NULL, NULL, ${if (favorite) 1 else 0})"
    }
}
