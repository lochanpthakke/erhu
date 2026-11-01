package com.erhu.erhu.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.common.result.getOrNull
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.ArtistEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ArtistArtworkBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val storeDir by lazy { folder.newFolder("artist_artwork") }
    private val repository by lazy {
        ArtistArtworkRepositoryImpl(db, db.artistDao(), db.libraryModelDao(), ArtistArtworkStore(storeDir), TimeSource { 1000L })
    }
    private val backupManager by lazy {
        ArtistArtworkBackupManager(db.libraryModelDao(), db.artistDao(), storeDir)
    }

    @After
    fun close() = db.close()

    @Test
    fun exportAndImportRestoresCustomArtistArtwork() = runBlocking {
        db.artistDao().upsertAll(listOf(ArtistEntity(id = 1L, name = "Test Artist", songCount = 1, albumCount = 0, artistKey = "test artist")))
        val imageBytes = "fake-jpg-binary-content".toByteArray()

        val setResult = repository.setCustomArtwork(1L, imageBytes)
        assertTrue(setResult is com.erhu.erhu.core.common.result.AppResult.Success)

        val outStream = ByteArrayOutputStream()
        val exportResult = backupManager.exportBackup(outStream)
        val count = exportResult.getOrNull()
        assertEquals(1, count)

        // Clear DB and store directory
        db.artistDao().deleteAll()
        db.libraryModelDao().deleteCustomArtwork("test artist")
        storeDir.listFiles()?.forEach { it.delete() }

        // Re-add artist row
        db.artistDao().upsertAll(listOf(ArtistEntity(id = 1L, name = "Test Artist", songCount = 1, albumCount = 0, artistKey = "test artist")))

        // Import backup
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val importResult = backupManager.importBackup(inStream)
        val restoredCount = importResult.getOrNull()
        assertEquals(1, restoredCount)

        val restoredRow = db.libraryModelDao().getArtwork("test artist")
        assertNotNull(restoredRow)
        assertEquals("CUSTOM", restoredRow?.source)
    }
}
