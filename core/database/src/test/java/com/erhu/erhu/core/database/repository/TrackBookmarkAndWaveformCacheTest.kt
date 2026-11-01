package com.erhu.erhu.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.ErhuDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Bookmarks belong to one song id and survive reopening the database; the waveform cache stores compact levels. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrackBookmarkAndWaveformCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "bookmark-test.db"
    private var db = open()
    private var time = 1_000L

    private fun open(): ErhuDatabase = Room.databaseBuilder(context, ErhuDatabase::class.java, dbName).allowMainThreadQueries().build()

    private fun bookmarks() = TrackBookmarkRepository(db.trackBookmarkDao()) { time++ }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `bookmarks persist per track and two look-alike tracks stay independent`() =
        runBlocking {
            // Songs 1 and 2 could share title, artist and album: only the id ties a bookmark to a track.
            val repo = bookmarks()
            val chorus = (repo.addBookmark(songId = 1, positionMs = 61_000, label = "  Chorus  ") as AppResult.Success).data
            repo.addBookmark(songId = 1, positionMs = 12_000)
            repo.addBookmark(songId = 2, positionMs = 61_000, label = "Other version")

            assertEquals(listOf(12_000L, 61_000L), repo.observeBookmarks(1).first().map { it.positionMs })
            assertEquals(listOf("", "Chorus"), repo.observeBookmarks(1).first().map { it.label })
            assertEquals(listOf("Other version"), repo.observeBookmarks(2).first().map { it.label })

            // Reopen: still there.
            db.close()
            db = open()
            val reopened = bookmarks()
            assertEquals(2, reopened.observeBookmarks(1).first().size)

            reopened.renameBookmark(chorus, "Drop")
            assertEquals("Drop", reopened.observeBookmarks(1).first().single { it.id == chorus }.label)
            reopened.deleteBookmark(chorus)
            assertEquals(listOf(12_000L), reopened.observeBookmarks(1).first().map { it.positionMs })
            assertEquals(1, reopened.observeBookmarks(2).first().size)
        }

    @Test
    fun `negative positions are clamped and long labels shortened`() =
        runBlocking {
            val repo = bookmarks()
            repo.addBookmark(songId = 3, positionMs = -5, label = "x".repeat(200))
            val saved = repo.observeBookmarks(3).first().single()
            assertEquals(0L, saved.positionMs)
            assertEquals(TrackBookmarkRepository.MAX_LABEL_LENGTH, saved.label.length)
        }

    @Test
    fun `waveform cache stores, replaces and removes compact levels`() =
        runBlocking {
            val store = WaveformCacheStore(db.waveformCacheDao()) { time++ }
            assertNull(store.get(1))
            store.put(CachedWaveform(1, "content://m/1", 100, 5, 1, byteArrayOf(0, 10, -1)))
            store.put(CachedWaveform(1, "content://m/1", 120, 6, 1, byteArrayOf(1, 2)))
            val cached = store.get(1)!!
            assertEquals(120L, cached.fileSize)
            assertArrayEquals(byteArrayOf(1, 2), cached.levels)
            store.remove(1)
            assertTrue(store.get(1) == null)
        }
}
