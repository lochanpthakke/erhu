package com.lochanpthakke.empee3.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AC9: artwork saved through the repository lands in the table that rescans keep. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ArtistArtworkPersistenceTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private val repository =
        LibraryRepositoryImpl(
            songDao = db.songDao(),
            albumDao = db.albumDao(),
            artistDao = db.artistDao(),
            libraryModelDao = db.libraryModelDao(),
            workManager = { error("WorkManager is not used by this test") },
        )

    @After
    fun close() = db.close()

    @Test
    fun `updating artist artwork also stores it by artist key`() =
        runBlocking {
            db.artistDao().upsertAll(listOf(ArtistEntity(id = 7, name = "Drake", songCount = 1, albumCount = 0, artistKey = "drake")))
            repository.updateArtistArtwork(7, "https://img/drake.jpg")
            assertEquals(
                mapOf("drake" to "https://img/drake.jpg"),
                db.libraryModelDao().getArtworkByKey().associate { it.artistKey to it.artworkUri },
            )
        }
}
