package com.lochanpthakke.empee3.core.database.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.library.LibraryModelBuilder
import com.lochanpthakke.empee3.core.database.library.RawTrack
import com.lochanpthakke.empee3.core.database.scanner.LibraryWriter
import com.lochanpthakke.empee3.core.database.scanner.toEntities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Playlist artwork is playlist metadata kept across scans; recently-played comes from real playback only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaylistArtworkAndHistoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private val artworkDir: File = Files.createTempDirectory("playlist-artwork").toFile()
    private val clock = FakeClock()
    private val playlists =
        PlaylistRepositoryImpl(db.playlistDao(), db.recentlyPlayedDao(), PlaylistArtworkStore(artworkDir), clock::now)

    private class FakeClock {
        var time = 1_000L

        fun now(): Long = time++
    }

    @After
    fun close() {
        db.close()
        artworkDir.deleteRecursively()
    }

    private fun fileOf(uri: String?) = File(Uri.parse(uri).path!!)

    private suspend fun newPlaylist(name: String) = (playlists.createPlaylist(name) as AppResult.Success).data

    private suspend fun playlist(id: Long) = playlists.observePlaylist(id).first()!!

    @Test
    fun `custom artwork is saved, replaced and removed`() =
        runBlocking {
            val id = newPlaylist("Gym")
            assertNull(playlist(id).artworkUri)

            playlists.setPlaylistArtwork(id, byteArrayOf(1, 2, 3))
            val first = playlist(id).artworkUri
            assertArrayEquals(byteArrayOf(1, 2, 3), fileOf(first).readBytes())

            playlists.setPlaylistArtwork(id, byteArrayOf(4, 5))
            val second = playlist(id).artworkUri
            assertTrue(first != second) // a new file, so cached images of the old one are never shown
            assertFalse(fileOf(first).exists())
            assertArrayEquals(byteArrayOf(4, 5), fileOf(second).readBytes())

            playlists.clearPlaylistArtwork(id)
            assertNull(playlist(id).artworkUri)
            assertFalse(fileOf(second).exists())
        }

    @Test
    fun `deleting a playlist deletes its artwork file`() =
        runBlocking {
            val id = newPlaylist("Old")
            playlists.setPlaylistArtwork(id, byteArrayOf(9))
            val file = fileOf(playlist(id).artworkUri)
            playlists.deletePlaylist(id)
            assertFalse(file.exists())
        }

    @Test
    fun `renaming keeps artwork and songs`() =
        runBlocking {
            val id = newPlaylist("Draft")
            playlists.setPlaylistArtwork(id, byteArrayOf(7))
            val uri = playlist(id).artworkUri
            playlists.renamePlaylist(id, "  Road Trip ")
            assertEquals("Road Trip", playlist(id).name)
            assertEquals(uri, playlist(id).artworkUri)
            assertTrue(playlists.renamePlaylist(id, "   ") is AppResult.Error)
        }

    @Test
    fun `playlist artwork survives a library rescan`() =
        runBlocking {
            val id = newPlaylist("Kept")
            playlists.setPlaylistArtwork(id, byteArrayOf(5))
            val uri = playlist(id).artworkUri
            val raw = listOf(RawTrack(1, "Rich Flex", "Drake/21 Savage", null, "Her Loss", 1, 1, "/m", false))
            val song = SongEntity(id = 1, title = "Rich Flex", artist = "Drake/21 Savage", album = "Her Loss", artistId = 0, albumId = 0,
                durationMs = 1, uri = "u1", filePath = "/m/1.mp3", trackNumber = 1, year = 2022, dateModified = 1)
            val writer = LibraryWriter(db, db.songDao(), db.albumDao(), db.artistDao(), db.libraryModelDao())
            writer.write(LibraryModelBuilder.build(raw, emptyMap()).toEntities(listOf(song), emptyMap()))
            writer.write(LibraryModelBuilder.build(raw, emptyMap()).toEntities(listOf(song), emptyMap()))
            assertEquals(uri, playlist(id).artworkUri)
            assertTrue(fileOf(uri).exists())
        }

    @Test
    fun `recently played playlists are newest first and drop deleted playlists`() =
        runBlocking {
            val a = newPlaylist("A")
            val b = newPlaylist("B")
            playlists.markPlaylistPlayed(a)
            playlists.markPlaylistPlayed(b)
            playlists.markPlaylistPlayed(a)
            assertEquals(listOf("A", "B"), playlists.observeRecentPlaylists(10).first().map { it.name })
            playlists.deletePlaylist(a)
            assertEquals(listOf("B"), playlists.observeRecentPlaylists(10).first().map { it.name })
        }

    @Test
    fun `recently played albums come from songs actually played`() =
        runBlocking {
            db.albumDao().upsertAll(listOf(AlbumEntity(20, "Her Loss", "Drake & 21 Savage", 10, 16, 2022), AlbumEntity(30, "IGOR", "Tyler", 11, 12, 2019)))
            db.songDao().upsertAll(
                listOf(
                    SongEntity(id = 1, title = "Rich Flex", artist = "Drake/21 Savage", album = "Her Loss", artistId = 10, albumId = 20,
                        durationMs = 1, uri = "u1", filePath = "/m/1.mp3", trackNumber = 1, year = 2022, dateModified = 1),
                    SongEntity(id = 2, title = "EARFQUAKE", artist = "Tyler", album = "IGOR", artistId = 11, albumId = 30,
                        durationMs = 1, uri = "u2", filePath = "/m/2.mp3", trackNumber = 1, year = 2019, dateModified = 1),
                ),
            )
            val library = RecentlyPlayedRecorder(db.recentlyPlayedDao(), clock::now)
            assertTrue(library.observeRecentAlbums(10).first().isEmpty()) // no invented history
            library.recordSongPlayed(1)
            library.recordSongPlayed(2)
            library.recordSongPlayed(999) // unknown song: ignored
            assertEquals(listOf("IGOR", "Her Loss"), library.observeRecentAlbums(10).first().map { it.title })
            library.recordSongPlayed(1)
            assertEquals(listOf("Her Loss", "IGOR"), library.observeRecentAlbums(10).first().map { it.title })
        }
}
