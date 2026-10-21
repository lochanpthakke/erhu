package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.AnimatedArtworkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnimatedArtworkDaoTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = db.animatedArtworkDao()

    @After
    fun close() = db.close()

    @Test
    fun `insert and observe by album id`() =
        runBlocking {
            val entity =
                AnimatedArtworkEntity(
                    albumId = 1L,
                    normalizedArtist = "artist",
                    normalizedAlbum = "album",
                    status = "UNCHECKED",
                    updatedAtEpochMs = 123L,
                )
            dao.upsert(entity)

            val observed = dao.observeByAlbumId(1L).first()
            assertEquals(entity, observed)
        }

    @Test
    fun `reset downloading`() =
        runBlocking {
            dao.upsert(
                AnimatedArtworkEntity(
                    albumId = 1L,
                    normalizedArtist = "artist1",
                    normalizedAlbum = "album1",
                    status = "DOWNLOADING",
                    updatedAtEpochMs = 123L,
                ),
            )
            dao.upsert(
                AnimatedArtworkEntity(
                    albumId = 2L,
                    normalizedArtist = "artist2",
                    normalizedAlbum = "album2",
                    status = "FAILED",
                    updatedAtEpochMs = 123L,
                ),
            )

            dao.resetDownloading()

            assertEquals("UNCHECKED", dao.getByAlbumId(1L)?.status)
            assertEquals("FAILED", dao.getByAlbumId(2L)?.status)
        }

    @Test
    fun `getByNormalizedKey returns match`() =
        runBlocking {
            val entity =
                AnimatedArtworkEntity(
                    albumId = 1L,
                    normalizedArtist = "foo bar",
                    normalizedAlbum = "baz qux",
                    status = "UNCHECKED",
                    updatedAtEpochMs = 123L,
                )
            dao.upsert(entity)

            val match = dao.getByNormalizedKey("foo bar", "baz qux")
            assertEquals(entity, match)

            val miss = dao.getByNormalizedKey("foo", "baz")
            assertNull(miss)
        }
}
