package com.erhu.erhu.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.ArtistEntity
import com.erhu.erhu.core.database.entity.SongArtistCrossRefEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RadioArtistQueriesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
        .allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test fun onlyStationArtistsAreReturnedAndTheSeedIsFirst() = runBlocking {
        db.artistDao().upsertAll((1L..6L).map { ArtistEntity(it, "Artist $it", 1, 1, "file:///artist-$it.jpg", "artist $it", it == 6L) })
        db.songArtistCrossRefDao().upsertAll(listOf(
            SongArtistCrossRefEntity(10, 1, "PRIMARY"),
            SongArtistCrossRefEntity(20, 2, "PRIMARY"),
            SongArtistCrossRefEntity(30, 2, "PRIMARY"),
            SongArtistCrossRefEntity(30, 3, "FEATURED", position = 1),
            SongArtistCrossRefEntity(99, 4, "PRIMARY"), // Outside the station.
            SongArtistCrossRefEntity(30, 5, "FEATURED", isUnresolved = true),
            SongArtistCrossRefEntity(30, 6, "FEATURED"), // Unresolved artist entity.
        ))
        val rows = db.artistDao().observeArtistsForSongs(listOf(10, 20, 30), 10).first()
        assertEquals(listOf(1L, 2L, 3L), rows.map { it.artist.id })
        assertEquals(listOf(10L, 20L, 30L), rows.map { it.representativeSongId })
        assertEquals(emptyList<ArtistForSongs>(), db.artistDao().observeArtistsForSongs(emptyList(), 10).first())
    }

    @Test fun rolesDoNotDuplicateArtistsAndNewPortraitsAreObserved() = runBlocking {
        db.artistDao().upsertAll(listOf(ArtistEntity(1, "Artist", 2, 1, "file:///old.jpg")))
        db.songArtistCrossRefDao().upsertAll(listOf(
            SongArtistCrossRefEntity(10, 1, "PRIMARY"), SongArtistCrossRefEntity(10, 1, "FEATURED"),
        ))
        assertEquals(1, db.artistDao().observeArtistsForSongs(listOf(10), 10).first().size)
        db.artistDao().upsertAll(listOf(ArtistEntity(1, "Artist", 2, 1, "file:///new.jpg")))
        assertEquals("file:///new.jpg", db.artistDao().observeArtistsForSongs(listOf(10), 10).first().single().artist.artworkUri)
    }
}
