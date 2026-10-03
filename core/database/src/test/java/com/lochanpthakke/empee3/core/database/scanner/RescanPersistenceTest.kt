package com.lochanpthakke.empee3.core.database.scanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.library.IdentityHash
import com.lochanpthakke.empee3.core.database.library.LibraryModelBuilder
import com.lochanpthakke.empee3.core.database.library.RawTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AC7 / AC9: favorites and artist artwork survive a rescan. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RescanPersistenceTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private val writer = LibraryWriter(db, db.songDao(), db.albumDao(), db.artistDao(), db.libraryModelDao())

    private val raw =
        listOf(
            RawTrack(1, "Rich Flex", "Drake/21 Savage", null, "Her Loss", 1, 1, "/m", false),
            RawTrack(2, "BackOutsideBoyz", "Drake", null, "Her Loss", 4, 1, "/m", false),
            RawTrack(3, "3AM on Glenwood", "21 Savage", null, "Her Loss", 15, 1, "/m", false),
        )
    private val songs =
        raw.map {
            SongEntity(
                id = it.songId,
                title = it.title,
                artist = it.rawArtist,
                album = it.album,
                artistId = 0,
                albumId = 0,
                durationMs = 1,
                uri = "u${it.songId}",
                filePath = "/m/${it.songId}.mp3",
                trackNumber = it.trackNumber,
                year = 2022,
                dateModified = 1,
                artworkUri = null,
            )
        }

    private suspend fun scan() {
        val artwork = db.libraryModelDao().getArtworkByKey().associate { it.artistKey to it.artworkUri }
        writer.write(LibraryModelBuilder.build(raw, emptyMap()).toEntities(songs, artwork))
    }

    @After
    fun close() = db.close()

    @Test
    fun `artwork and favorites survive a rescan`() =
        runBlocking {
            scan()
            val drake = IdentityHash.of("drake")
            db.artistDao().updateArtistArtwork(drake, "https://img/drake.jpg")
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("drake", "https://img/drake.jpg", 1))
            db.songDao().setFavorite(id = 1, isFavorite = true)
            scan()
            assertEquals("https://img/drake.jpg", db.artistDao().getArtistById(drake)?.artworkUri)
            assertTrue(db.songDao().getSongById(1)!!.isFavorite)
            assertEquals(1, db.albumDao().getAllAlbums().first().size)
        }
}
