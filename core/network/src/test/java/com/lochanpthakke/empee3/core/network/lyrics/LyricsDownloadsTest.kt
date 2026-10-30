package com.lochanpthakke.empee3.core.network.lyrics

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class LyricsDownloadsTest {
    @get:Rule val folder = TemporaryFolder()
    private val track = LyricsTrack(7, LyricsQuery("Song", "Artist", "Album", 200_000))
    private val candidate =
        LyricsCandidate(
            "LRCLIB",
            42,
            "Song",
            "Artist",
            "Album",
            200.0,
            syncedLyrics = "[00:12.000]Hello",
            wordSyncedLrc = "[00:12.000]<00:12.000>Hello <00:12.650>world",
        )
    private var clock = 1_000_000L

    private object Threads : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
    }

    private class Store : OnlineLyricsCache {
        val entries = mutableMapOf<Long, CachedLyrics>()
        var writes = 0
        var failWrite = false
        var failRemove = false
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun get(songId: Long) = entries[songId]

        override suspend fun put(entry: CachedLyrics) {
            gate?.await()
            if (failWrite) throw IOException("disk full")
            writes++
            entries[entry.songId] = entry
        }

        override suspend fun remove(songId: Long) {
            if (failRemove) throw IOException("read only")
            entries.remove(songId)
        }
    }

    private val store = Store()
    private var calls = 0
    private var offline = false
    private var gate: CompletableDeferred<Unit>? = null
    private val provider =
        object : OnlineLyricsProvider {
            override val name = "Fake"

            override suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate> {
                calls++
                gate?.await()
                return if (offline) ProviderResponse.Offline else ProviderResponse.Success(candidate)
            }

            override suspend fun search(
                query: LyricsQuery,
                freeText: String?,
            ) = ProviderResponse.NotFound
        }

    private fun repository(cache: OnlineLyricsCache = store) = OnlineLyricsRepository(listOf(provider), cache, Threads) { clock }

    private fun legacy(id: Long = track.songId) =
        CachedLyrics(
            songId = id,
            fingerprint = fingerprint(track.query),
            status = CachedStatus.FOUND,
            candidate = candidate,
            fetchedAtMs = clock,
            userChosen = true,
        )

    private suspend fun fails(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected an IO failure")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test fun `display and manual choice use memory without a persistent copy`() =
        runBlocking {
            val repo = repository()
            val found = repo.lookup(track) as OnlineLyricsResult.Found
            assertEquals(candidate, found.candidate)
            assertNull(found.saved)
            repo.lookup(track)
            assertEquals(1, calls)
            repo.choose(track, candidate)
            assertTrue((repo.lookup(track) as OnlineLyricsResult.Found).userChosen)
            assertEquals(0, store.writes)
            assertTrue(store.entries.isEmpty())
            offline = true
            assertEquals(OnlineLyricsResult.Offline, repository().lookup(track))
        }

    @Test fun `explicit download is available to a fresh offline repository with exact timestamps`() =
        runBlocking {
            val cache = FileOnlineLyricsCache(folder.root)
            val entry = repository(cache).download(track, candidate, true, null)
            assertTrue(entry.explicitDownload)
            offline = true
            val found = repository(cache).lookup(track) as OnlineLyricsResult.Found
            assertEquals(candidate, found.candidate)
            assertTrue(found.saved!!.explicitDownload)
            assertEquals(candidate.wordSyncedLrc, found.candidate.bestText)
            assertEquals(1, folder.root.listFiles()!!.size)
        }

    @Test fun `concurrent repeated downloads make one serialized write`() =
        runBlocking {
            val repo = repository()
            store.gate = CompletableDeferred()
            val a = async { repo.download(track, candidate, false, null) }
            val b = async { repo.download(track, candidate, false, null) }
            store.gate!!.complete(Unit)
            assertEquals(a.await(), b.await())
            assertEquals(1, store.writes)
            assertEquals(1, store.entries.size)
        }

    @Test fun `removal is per id even when two songs have identical names`() =
        runBlocking {
            val repo = repository()
            val first = repo.download(track, candidate, false, null)
            val other = repo.download(track.copy(songId = 8), candidate, false, null)
            repo.removeDownload(track, first)
            assertNull(repo.cached(track))
            assertEquals(other, repo.cached(track.copy(songId = 8)))
        }

    @Test fun `failed writes and failed removals preserve the prior saved record`() =
        runBlocking {
            val repo = repository()
            store.failWrite = true
            fails { repo.download(track, candidate, true, null) }
            assertNull(repo.cached(track))
            store.failWrite = false
            val saved = repo.download(track, candidate, false, null)
            store.failWrite = true
            fails { repo.download(track, candidate.copy(providerId = 99), true, saved) }
            assertEquals(saved, repo.cached(track))
            store.failRemove = true
            fails { repo.removeDownload(track, saved) }
            assertEquals(saved, repo.cached(track))
        }

    @Test fun `cancelling a pending save does not create a downloaded record`() =
        runBlocking {
            val repo = repository()
            store.gate = CompletableDeferred()
            val job = async { repo.download(track, candidate, false, null) }
            job.cancelAndJoin()
            assertNull(repo.cached(track))
            assertEquals(0, store.writes)
        }

    @Test fun `legacy saved files stay byte identical until an explicit download promotes them`() =
        runBlocking {
            val file = File(folder.root, "7.json")
            file.writeText(
                """
                {"songId":7,"fingerprint":"${fingerprint(track.query)}","status":"FOUND",
                 "candidate":{"provider":"LRCLIB","providerId":42,"trackName":"Song","artistName":"Artist",
                 "syncedLyrics":"[00:12.000]Hello"},"fetchedAtMs":0,"userChosen":true}
                """.trimIndent(),
            )
            val before = file.readBytes()
            val cache = FileOnlineLyricsCache(folder.root)
            val repo = repository(cache)
            offline = true
            val found = repo.lookup(track) as OnlineLyricsResult.Found
            assertFalse(found.saved!!.explicitDownload)
            assertArrayEquals(before, file.readBytes())
            val saved = repo.download(track, found.candidate, true, found.saved)
            assertTrue(saved.explicitDownload)
            assertEquals(1, folder.root.listFiles()!!.size)
        }

    @Test fun `expired negatives are physically removed while positives and explicit content survive`() =
        runBlocking {
            val cache = FileOnlineLyricsCache(folder.root)
            cache.put(legacy().copy(status = CachedStatus.NOT_FOUND, candidate = null, fetchedAtMs = 0))
            clock = OnlineLyricsRepository.NOT_FOUND_TTL_MS + 1
            val repo = repository(cache)
            repo.cached(track)
            assertFalse(File(folder.root, "7.json").exists())
            cache.put(legacy())
            repo.cached(track)
            assertTrue(File(folder.root, "7.json").exists())
            cache.put(legacy().copy(explicitDownload = true))
            repo.cached(track)
            assertTrue(File(folder.root, "7.json").exists())
        }

    @Test fun `recent legacy negatives retain the seven day lookup semantics`() =
        runBlocking {
            store.entries[7] = legacy().copy(status = CachedStatus.NOT_FOUND, candidate = null)
            assertEquals(OnlineLyricsResult.NotFound, repository().lookup(track))
            assertEquals(0, calls)
        }

    @Test fun `changed identity cannot overwrite or delete saved lyrics and stale removal cannot remove a replacement`() =
        runBlocking {
            val repo = repository()
            val saved = repo.download(track, candidate, true, null)
            val renamed = track.copy(query = track.query.copy(title = "Changed"))
            assertNull(repo.cached(renamed))
            fails { repo.removeDownload(renamed, saved) }
            fails { repo.download(renamed, candidate, true, null) }
            val replacement = repo.download(track, candidate.copy(providerId = 99), true, saved)
            fails { repo.removeDownload(track, saved) }
            assertEquals(replacement, repo.cached(track))
        }

    @Test fun `a changed source URI cannot delete or replace another file's new download`() =
        runBlocking {
            val repo = repository()
            val original = track.copy(sourceUri = "content://media/external/audio/media/7")
            val otherFile = original.copy(sourceUri = "content://media/other/audio/media/7")
            val saved = repo.download(original, candidate, false, null)
            assertNull(repo.cached(otherFile))
            fails { repo.removeDownload(otherFile, saved) }
            fails { repo.download(otherFile, candidate, false, saved) }
            assertEquals(saved, repo.cached(original))
        }

    @Test fun `an old network completion cannot recreate a removed download`() =
        runBlocking {
            val repo = repository()
            val saved = repo.download(track, candidate, false, null)
            gate = CompletableDeferred()
            val lookup = async { repo.lookup(track, refresh = true) }
            repo.removeDownload(track, saved)
            gate!!.complete(Unit)
            assertTrue(lookup.await() is OnlineLyricsResult.Found)
            assertNull(repo.cached(track))
            assertEquals(1, store.writes)
        }

    @Test fun `preferring embedded lyrics never removes or rewrites saved text`() =
        runBlocking {
            store.entries[7] = legacy()
            val repo = repository()
            repo.forget(track)
            assertTrue(repo.prefersEmbedded(track))
            assertEquals(legacy(), repo.cached(track))
            assertFalse((repo.lookup(track) as OnlineLyricsResult.Found).userChosen)
            assertEquals(0, store.writes)
        }

    @Test fun `misnamed or corrupt existing files are never overwritten or reported removed`() =
        runBlocking {
            val file = File(folder.root, "7.json").apply { writeText("broken") }
            val repo = repository(FileOnlineLyricsCache(folder.root))
            fails { repo.download(track, candidate, false, null) }
            fails { repo.removeDownload(track, legacy()) }
            assertEquals("broken", file.readText())
        }

    @Test fun `a file save failure preserves the existing record and leaves no temporary files`() =
        runBlocking {
            val cache = FileOnlineLyricsCache(folder.root)
            cache.put(legacy())
            val target = File(folder.root, "8.json")
            target.mkdir()
            File(target, "occupied").writeText("keep")
            fails { cache.put(legacy(8)) }
            assertEquals(legacy(), cache.get(7))
            assertTrue(folder.root.listFiles()!!.none { it.name.endsWith(".tmp") })
        }
}
