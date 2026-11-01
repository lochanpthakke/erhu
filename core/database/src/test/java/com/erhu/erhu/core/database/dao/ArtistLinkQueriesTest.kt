package com.erhu.erhu.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.AlbumArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.AlbumEntity
import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.entity.UnverifiedCreditSegmentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Albums are listed under every linked artist; unverified credits are found by artist key. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ArtistLinkQueriesTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @After
    fun close() = db.close()

    private fun song(id: Long) =
        SongEntity(
            id = id,
            title = "t$id",
            artist = "Kanye West/JAY-Z",
            album = "MBDTF",
            artistId = 0,
            albumId = 1,
            durationMs = 1,
            uri = "u$id",
            filePath = "/m/$id.mp3",
            trackNumber = 1,
            year = 2010,
            dateModified = 1,
        )

    @Test
    fun `a collaborative album is listed under each linked artist`() =
        runBlocking {
            db.albumDao().upsertAll(listOf(AlbumEntity(1, "Her Loss", "Drake & 21 Savage", 10, 16, 2022)))
            db.libraryModelDao().insertAlbumArtists(listOf(AlbumArtistCrossRefEntity(1, 10, 0), AlbumArtistCrossRefEntity(1, 20, 1)))
            assertEquals(listOf(1L), db.albumDao().getAlbumsByArtistId(20).first().map { it.id })
            assertEquals(listOf(1L), db.albumDao().getAlbumsByArtistId(10).first().map { it.id })
        }

    @Test
    fun `unverified songs are found by the artist key inside unresolved credits`() =
        runBlocking {
            db.songDao().upsertAll(listOf(song(5), song(6)))
            db.libraryModelDao().insertUnverifiedSegments(listOf(UnverifiedCreditSegmentEntity(5, 99, "kanye west", 0)))
            assertEquals(listOf(5L), db.songDao().getUnverifiedSongsByArtistKey("kanye west").first().map { it.id })
        }
}
