package com.lochanpthakke.empee3.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.getOrNull
import com.lochanpthakke.empee3.core.database.EmPee3Database
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.database.library.ArtistArtworkCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.URI

/** Artwork is stored by artist key (kept across rescans); custom images always win over automatic ones. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ArtistArtworkPersistenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmPee3Database::class.java)
            .allowMainThreadQueries()
            .build()
    private var clock = 1_000L
    private val storeDir by lazy { folder.newFolder("artist_artwork") }
    private val repository by lazy {
        ArtistArtworkRepositoryImpl(db, db.artistDao(), db.libraryModelDao(), ArtistArtworkStore(storeDir), TimeSource { clock })
    }

    @After
    fun close() = db.close()

    private suspend fun addArtist(
        id: Long,
        name: String,
    ) = db.artistDao().upsertAll(listOf(ArtistEntity(id = id, name = name, songCount = 1, albumCount = 0, artistKey = name.lowercase())))

    private suspend fun needingLookup(now: Long) = repository.artistsNeedingAutomaticLookup(now).getOrNull()!!

    private suspend fun record(
        key: String,
        artwork: AutomaticArtwork,
    ): Boolean = repository.recordAutomaticArtwork(key, artwork).getOrNull()!!

    private suspend fun row(key: String) = db.libraryModelDao().getArtwork(key)

    private suspend fun shown(id: Long) = db.artistDao().getArtistById(id)?.artworkUri

    private suspend fun customFile(key: String) = File(URI(row(key)!!.artworkUri))

    // Q, R, S: provider id, matcher version and source are persisted

    @Test
    fun `a found photo stores url, Deezer id, matcher version and source by artist key`() =
        runBlocking {
            addArtist(7, "Drake")
            assertTrue(record("drake", AutomaticArtwork.Found("https://img/drake.jpg", 246791)))
            val stored = row("drake")!!
            assertEquals("https://img/drake.jpg", stored.artworkUri)
            assertEquals(246791L, stored.providerArtistId)
            assertEquals(ArtistArtworkCache.MATCHER_VERSION, stored.matcherVersion)
            assertEquals(ArtistArtworkEntity.SOURCE_AUTO, stored.source)
            assertEquals("https://img/drake.jpg", shown(7))
        }

    // L: re-check of old automatic artwork

    @Test
    fun `old automatic artwork is re-checked once and replaced by the new decision`() =
        runBlocking {
            addArtist(1, "Offset")
            addArtist(2, "Too \$hort")
            addArtist(3, "Drake")
            // As migrated from v7: matcher 0.
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("offset", "https://img/wrong-offset.jpg", 0))
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("too \$hort", "NONE:2", 0))
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("drake", "https://img/drake.jpg", 0))
            db.artistDao().syncAllArtwork()
            assertEquals(setOf("Offset", "Too \$hort", "Drake"), needingLookup(clock).map { it.name }.toSet())

            record("offset", AutomaticArtwork.Found("https://img/main-offset.jpg", 68334))
            record("too \$hort", AutomaticArtwork.NotFound)
            // Drake's lookup failed: nothing is recorded, so the old image stays and is retried later.

            assertEquals("https://img/main-offset.jpg", shown(1))
            assertEquals(ArtistArtworkCache.NOT_FOUND, row("too \$hort")!!.artworkUri)
            assertNull(row("too \$hort")!!.providerArtistId)
            assertEquals(ArtistArtworkCache.MATCHER_VERSION, row("too \$hort")!!.matcherVersion)
            assertEquals("https://img/drake.jpg", shown(3))
            assertEquals(0, row("drake")!!.matcherVersion)
            assertEquals(listOf("Drake"), needingLookup(clock).map { it.name })
        }

    @Test
    fun `not found replaces a wrong automatic image with the placeholder state`() =
        runBlocking {
            addArtist(1, "Samsung")
            db.libraryModelDao().upsertArtwork(ArtistArtworkEntity("samsung", "https://img/unrelated.jpg", 0))
            record("samsung", AutomaticArtwork.NotFound)
            assertEquals(ArtistArtworkCache.NOT_FOUND, shown(1))
            assertFalse(ArtistArtworkCache.hasArtwork(shown(1)))
        }

    // M: NONE:3 retry

    @Test
    fun `a not-found decision is retried after thirty days, not before`() =
        runBlocking {
            addArtist(1, "Too \$hort")
            record("too \$hort", AutomaticArtwork.NotFound)
            assertTrue(needingLookup(clock + ArtistArtworkCache.NOT_FOUND_RETRY_MS - 1).isEmpty())
            assertEquals(1, needingLookup(clock + ArtistArtworkCache.NOT_FOUND_RETRY_MS).size)
        }

    // N: custom artwork is never overwritten

    @Test
    fun `automatic decisions never overwrite a custom image`() =
        runBlocking {
            addArtist(230, "Kanye West")
            assertTrue(repository.setCustomArtwork(230, byteArrayOf(1, 2, 3)) is AppResult.Success)
            val custom = row("kanye west")!!
            assertEquals(ArtistArtworkEntity.SOURCE_CUSTOM, custom.source)
            assertNull(custom.providerArtistId)
            assertTrue(custom.artworkUri.startsWith("file:"))

            assertFalse(record("kanye west", AutomaticArtwork.Found("https://img/black.jpg", 230)))
            assertFalse(record("kanye west", AutomaticArtwork.NotFound))
            assertEquals(custom, row("kanye west"))
            assertEquals(custom.artworkUri, shown(230))
            // Never selected for lookups, however old.
            assertTrue(needingLookup(Long.MAX_VALUE / 2).isEmpty())
            assertEquals(setOf("kanye west"), repository.observeCustomArtworkKeys().first())
        }

    @Test
    fun `saving twice in the same millisecond keeps the image`() =
        runBlocking {
            addArtist(1, "Drake")
            repository.setCustomArtwork(1, byteArrayOf(1))
            repository.setCustomArtwork(1, byteArrayOf(2))
            assertEquals(listOf(2.toByte()), customFile("drake").readBytes().toList())
        }

    @Test
    fun `replacing a custom image deletes only the previous custom file`() =
        runBlocking {
            addArtist(1, "Drake")
            repository.setCustomArtwork(1, byteArrayOf(1))
            val first = customFile("drake")
            clock += 1
            repository.setCustomArtwork(1, byteArrayOf(2))
            val second = customFile("drake")
            assertFalse(first.exists())
            assertEquals(listOf(2.toByte()), second.readBytes().toList())
        }

    @Test
    fun `removing a custom image deletes its file and returns the artist to automatic lookup`() =
        runBlocking {
            addArtist(1, "Drake")
            db.libraryModelDao().upsertArtwork(
                ArtistArtworkEntity("drake", "https://img/drake.jpg", 1, providerArtistId = 246791, matcherVersion = 2),
            )
            repository.setCustomArtwork(1, byteArrayOf(1))
            val file = customFile("drake")
            assertTrue(repository.clearCustomArtwork(1) is AppResult.Success)
            assertFalse(file.exists())
            assertNull(row("drake"))
            assertNull(shown(1))
            assertEquals(listOf("Drake"), needingLookup(clock).map { it.name })
        }

    @Test
    fun `clearing when there is no custom image leaves automatic artwork alone`() =
        runBlocking {
            addArtist(1, "Drake")
            record("drake", AutomaticArtwork.Found("https://img/drake.jpg", 246791))
            repository.clearCustomArtwork(1)
            assertEquals("https://img/drake.jpg", row("drake")!!.artworkUri)
            assertEquals("https://img/drake.jpg", shown(1))
        }

    @Test
    fun `the store never deletes files outside its folder`() {
        val outside = folder.newFile("keep.jpg")
        ArtistArtworkStore(storeDir).delete(outside.toURI().toString())
        ArtistArtworkStore(storeDir).delete("https://cdn-images.dzcdn.net/images/artist/x/500x500.jpg")
        assertTrue(outside.exists())
    }
}
