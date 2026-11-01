package com.erhu.erhu.core.database.scanner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.ArtistArtworkEntity
import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.library.IdentityHash
import com.erhu.erhu.core.database.library.LibraryModelBuilder
import com.erhu.erhu.core.database.library.RawTrack
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
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
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
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("drake", "https://img/drake.jpg", 1))
            db.songDao().setFavorite(id = 1, isFavorite = true)
            scan()
            assertEquals("https://img/drake.jpg", db.artistDao().getArtistById(drake)?.artworkUri)
            assertTrue(db.songDao().getSongById(1)!!.isFavorite)
            assertEquals(1, db.albumDao().getAllAlbums().first().size)
        }

    @Test
    fun `a custom image set after the scan read the artwork is not undone by that scan`() =
        runBlocking {
            scan()
            val drake = IdentityHash.of("drake")
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("drake", "https://img/auto.jpg", 1))
            // The scan reads artwork, then the user picks an image before the scan writes.
            val staleArtwork = db.libraryModelDao().getArtworkByKey().associate { it.artistKey to it.artworkUri }
            db.libraryModelDao().upsertArtwork(
                ArtistArtworkEntity("drake", "file:///data/artist_artwork/a.jpg", 2, source = ArtistArtworkEntity.SOURCE_CUSTOM),
            )
            writer.write(LibraryModelBuilder.build(raw, emptyMap()).toEntities(songs, staleArtwork))
            assertEquals("file:///data/artist_artwork/a.jpg", db.artistDao().getArtistById(drake)?.artworkUri)
            val row = db.libraryModelDao().getArtwork("drake")!!
            assertEquals(ArtistArtworkEntity.SOURCE_CUSTOM, row.source)
            assertEquals("file:///data/artist_artwork/a.jpg", row.artworkUri)
        }
}
