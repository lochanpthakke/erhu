package com.erhu.erhu.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.RemoteTrackIdSeed
import com.erhu.erhu.core.database.entity.RemoteDetails
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
import com.erhu.erhu.core.database.entity.RemoteSourceEntity
import com.erhu.erhu.core.database.entity.RemoteTrackEntity
import com.erhu.erhu.core.model.RemoteIds
import com.erhu.erhu.core.model.RemoteProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteLibraryDaoTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .addCallback(RemoteTrackIdSeed)
            .allowMainThreadQueries()
            .build()
    private val dao = db.remoteLibraryDao()

    @After
    fun close() = db.close()

    @Test
    fun `re-listing an existing Drive track preserves tags, metadata, favorite and updatedAt with changed false`() =
        runBlocking {
            val sourceId = insertSource(RemoteProvider.GOOGLE_DRIVE, "My Drive")
            val folderId = insertFolder(sourceId, "folder-1")
            val initialUpdatedAt = 1_000L

            val initial =
                dao.rememberWithChanges(
                    listOf(
                        driveListing(sourceId = sourceId, remoteId = "file-1", title = "01 - song", updatedAt = initialUpdatedAt),
                    ),
                )
            assertTrue(initial.changed)
            val id = initial.tracks.single().id
            assertTrue(RemoteIds.isRemote(id))

            dao.setTags(
                id = id,
                title = "Real Title",
                artist = "Real Artist",
                album = "Real Album",
                durationMs = 215_000L,
                genre = "Indie",
                year = 2024,
            )
            dao.setFavorite(id, true)
            dao.setDetails(id, "Indie", 2024, "Verse one", RemoteDetails.VERSION)
            dao.replaceLibraryFolderTracks(folderId, mapOf(id to "sub-1"))

            val relisted =
                dao.rememberWithChanges(
                    listOf(
                        driveListing(
                            sourceId = sourceId,
                            remoteId = "file-1",
                            title = "01 - song",
                            updatedAt = 99_999L,
                            isFavorite = false,
                        ),
                    ),
                )

            assertFalse(relisted.changed)
            val stored = dao.findTrack(sourceId, "file-1")!!
            assertEquals("Real Title", stored.title)
            assertEquals("Real Artist", stored.artist)
            assertEquals("Real Album", stored.album)
            assertEquals(215_000L, stored.durationMs)
            assertEquals("Indie", stored.genre)
            assertEquals(2024, stored.year)
            assertEquals("Verse one", stored.lyrics)
            assertEquals(RemoteDetails.VERSION, stored.detailsVersion)
            assertTrue(stored.isFavorite)
            assertEquals(initialUpdatedAt, stored.updatedAt)
            assertEquals(folderId, stored.libraryFolderId)
            assertEquals("sub-1", stored.parentFolder)
        }

    @Test
    fun `re-listing a Subsonic track reports changed only when metadata changes and preserves updatedAt`() =
        runBlocking {
            val sourceId = insertSource(RemoteProvider.SUBSONIC, "Navidrome")
            val initialUpdatedAt = 500L

            val first =
                dao.rememberWithChanges(
                    listOf(
                        subsonicTrack(
                            sourceId = sourceId,
                            remoteId = "tr-1",
                            title = "Midnight",
                            durationMs = 180_000L,
                            genre = "Electronic",
                            year = 2020,
                            isFavorite = false,
                            updatedAt = initialUpdatedAt,
                        ),
                    ),
                )
            assertTrue(first.changed)

            val unchanged =
                dao.rememberWithChanges(
                    listOf(
                        subsonicTrack(
                            sourceId = sourceId,
                            remoteId = "tr-1",
                            title = "Midnight",
                            durationMs = 180_000L,
                            genre = "Electronic",
                            year = 2020,
                            isFavorite = false,
                            updatedAt = 9_999L,
                        ),
                    ),
                )
            assertFalse(unchanged.changed)
            assertEquals(initialUpdatedAt, unchanged.tracks.single().updatedAt)

            val updated =
                dao.rememberWithChanges(
                    listOf(
                        subsonicTrack(
                            sourceId = sourceId,
                            remoteId = "tr-1",
                            title = "Midnight (Remaster)",
                            durationMs = 180_000L,
                            genre = "Electronic",
                            year = 2020,
                            isFavorite = true,
                            updatedAt = 10_000L,
                        ),
                    ),
                )
            assertTrue(updated.changed)
            val stored = dao.findTrack(sourceId, "tr-1")!!
            assertEquals("Midnight (Remaster)", stored.title)
            assertTrue(stored.isFavorite)
            assertEquals(initialUpdatedAt, stored.updatedAt)
        }

    @Test
    fun `setTags stores genre and year without clobbering existing values with null or zero`() =
        runBlocking {
            val sourceId = insertSource(RemoteProvider.GOOGLE_DRIVE, "My Drive")
            val id =
                dao
                    .rememberWithChanges(listOf(driveListing(sourceId, "file-2", "raw", 100L)))
                    .tracks
                    .single()
                    .id

            dao.setTags(id, "Title 1", "Artist 1", "Album 1", 1_000L, genre = "Jazz", year = 2019)
            val first = dao.findTrack(sourceId, "file-2")!!
            assertEquals("Jazz", first.genre)
            assertEquals(2019, first.year)

            dao.setTags(id, "Title 2", "Artist 2", "Album 2", 2_000L, genre = null, year = 0)
            val second = dao.findTrack(sourceId, "file-2")!!
            assertEquals("Title 2", second.title)
            assertEquals("Artist 2", second.artist)
            assertEquals("Album 2", second.album)
            assertEquals(2_000L, second.durationMs)
            assertEquals("Jazz", second.genre)
            assertEquals(2019, second.year)
        }

    @Test
    fun `replaceLibraryFolderTracks returns false when unchanged and true on add, move or remove`() =
        runBlocking {
            val sourceId = insertSource(RemoteProvider.GOOGLE_DRIVE, "My Drive")
            val folderId = insertFolder(sourceId, "folder-2")
            val tracks =
                dao
                    .rememberWithChanges(
                        listOf(
                            driveListing(sourceId, "a", "a", 100L),
                            driveListing(sourceId, "b", "b", 100L),
                        ),
                    ).tracks
            val idA = tracks[0].id
            val idB = tracks[1].id

            assertTrue(dao.replaceLibraryFolderTracks(folderId, mapOf(idA to "f1", idB to "f1")))
            assertFalse(dao.replaceLibraryFolderTracks(folderId, mapOf(idA to "f1", idB to "f1")))
            assertTrue(dao.replaceLibraryFolderTracks(folderId, mapOf(idA to "f1", idB to "f2")))
            assertTrue(dao.replaceLibraryFolderTracks(folderId, mapOf(idA to "f1")))

            val remaining = dao.getLibraryFolderTracks(folderId)
            assertEquals(listOf(LibraryFolderTrackRef(idA, "f1")), remaining)
        }

    private suspend fun insertSource(
        provider: RemoteProvider,
        name: String,
    ): Long =
        dao.insertSource(
            RemoteSourceEntity(
                provider = provider.key,
                name = name,
                addedAt = 1L,
            ),
        )

    private suspend fun insertFolder(
        sourceId: Long,
        folderId: String,
    ): Long =
        dao.insertLibraryFolder(
            RemoteLibraryFolderEntity(
                sourceId = sourceId,
                folderId = folderId,
                name = folderId,
                addedAt = 1L,
            ),
        )

    private fun driveListing(
        sourceId: Long,
        remoteId: String,
        title: String,
        updatedAt: Long,
        isFavorite: Boolean = false,
    ) = RemoteTrackEntity(
        sourceId = sourceId,
        provider = RemoteProvider.GOOGLE_DRIVE.key,
        remoteId = remoteId,
        title = title,
        artist = "",
        album = "",
        durationMs = 0L,
        trackNumber = 0,
        discNumber = 1,
        mimeType = "audio/mpeg",
        artworkKey = remoteId,
        isFavorite = isFavorite,
        updatedAt = updatedAt,
    )

    private fun subsonicTrack(
        sourceId: Long,
        remoteId: String,
        title: String,
        durationMs: Long,
        genre: String?,
        year: Int,
        isFavorite: Boolean,
        updatedAt: Long,
    ) = RemoteTrackEntity(
        sourceId = sourceId,
        provider = RemoteProvider.SUBSONIC.key,
        remoteId = remoteId,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = durationMs,
        trackNumber = 1,
        discNumber = 1,
        mimeType = "audio/flac",
        artworkKey = "art-$remoteId",
        isFavorite = isFavorite,
        updatedAt = updatedAt,
        genre = genre,
        year = year,
    )
}
