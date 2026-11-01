package com.erhu.erhu.core.network.lyrics

import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.network.api.MusixmatchApi
import com.erhu.erhu.core.network.provider.MusixmatchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncedFallbackTest {
    @get:org.junit.Rule val temporary = org.junit.rules.TemporaryFolder()
    private val query = LyricsQuery("Song", "Artist", "Album", 200_000)
    private val track = LyricsTrack(7, query, "content://audio/7")
    private val candidate = LyricsCandidate("Musixmatch", 1, "Song", "Artist", "Album", 200.0,
        syncedLyrics = "[00:01.00]First\n[00:05.00]Second")
    private class Cache : OnlineLyricsCache {
        val entries = mutableMapOf<Long, CachedLyrics>()
        override suspend fun get(songId: Long) = entries[songId]
        override suspend fun put(entry: CachedLyrics) { entries[entry.songId] = entry }
        override suspend fun remove(songId: Long) { entries.remove(songId) }
    }
    private class Provider(val answer: suspend () -> ProviderResponse<LyricsCandidate>) : OnlineLyricsProvider {
        override val name = "Fake"
        var calls = 0
        override suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate> { calls++; return answer() }
        override suspend fun search(query: LyricsQuery, freeText: String?): ProviderResponse<List<LyricsCandidate>> = ProviderResponse.NotFound
    }

    @Test fun `outages rate limits thrown errors and timeouts all reach independent fallback`() = runTest {
        val dispatchers = object : DispatcherProvider {
            override val main = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            override val io = main
            override val default = main
        }
        val failures: List<suspend () -> ProviderResponse<LyricsCandidate>> = listOf(
            { ProviderResponse.Offline }, { ProviderResponse.RateLimited(99_999) },
            { ProviderResponse.Failed("outage") }, { ProviderResponse.NotFound },
            { throw IOException("outage") }, { awaitCancellation() },
            { ProviderResponse.Success(candidate.copy(syncedLyrics = "[00:99.00]bad", plainLyrics = "plain")) },
        )
        for (failure in failures) {
            val fallback = Provider { ProviderResponse.Success(candidate) }
            val repository = OnlineLyricsRepository(listOf(Provider(failure), fallback), Cache(), dispatchers)
            assertEquals(candidate, (repository.lookup(track) as OnlineLyricsResult.Found).candidate)
            assertEquals(1, fallback.calls)
        }
    }

    @Test fun `synced response is reused after repository restart while downloads remain separate`() = runTest {
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = main
            override val default = main
        }
        val disk = FileOnlineLyricsCache(temporary.newFolder("response-cache"))
        val downloads = Cache()
        val provider = Provider { ProviderResponse.Success(candidate) }
        OnlineLyricsRepository(listOf(provider), downloads, dispatchers, disk).lookup(track)
        assertTrue(downloads.entries.isEmpty())
        val offline = Provider { error("must not query network") }
        val restarted = OnlineLyricsRepository(listOf(offline), downloads, dispatchers, disk)
        assertTrue((restarted.lookup(track) as OnlineLyricsResult.Found).fromCache)
        assertEquals(0, offline.calls)
        assertNull(restarted.cached(track)) // cache is not an explicit download
        assertEquals(OnlineLyricsResult.Failed("Lyrics provider unavailable"),
            restarted.lookup(track.copy(query = query.copy(title = "Different"))))
    }

    @Test fun `wrong recordings never replace the queried track`() = runTest {
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = main
            override val default = main
        }
        for (wrong in listOf(candidate.copy(trackName = "Song (Live)"), candidate.copy(durationSec = 250.0),
            candidate.copy(trackName = "Song (Remix)"), candidate.copy(trackName = "Song (Radio Edit)"))) {
            val repository = OnlineLyricsRepository(listOf(Provider { ProviderResponse.Success(wrong) }), Cache(), dispatchers)
            assertEquals(OnlineLyricsResult.NotFound, repository.lookup(track))
        }
    }

    @Test fun `a healthy synced primary skips fallback and legacy misses do not hide a new source`() = runTest {
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = main
            override val default = main
        }
        val fallback = Provider { ProviderResponse.Success(candidate) }
        val primary = Provider { ProviderResponse.Success(candidate.copy(provider = "LRCLIB")) }
        val legacy = Cache()
        legacy.put(CachedLyrics(songId = 7, fingerprint = fingerprint(query), status = CachedStatus.NOT_FOUND,
            fetchedAtMs = System.currentTimeMillis()))
        val repo = OnlineLyricsRepository(listOf(primary, fallback), legacy, dispatchers)
        assertEquals("LRCLIB", (repo.lookup(track) as OnlineLyricsResult.Found).candidate.provider)
        assertEquals(0, fallback.calls)
    }

    private class Api : MusixmatchApi {
        var calls = 0
        var subtitleCalls = 0
        var title = "Song"
        var lrc = "[00:01.00]First\n[00:05.00]Second"
        var status = 200
        var restricted = 0
        var fail = false
        override suspend fun tracks(key: String, title: String, artist: String, limit: Int): Response<JsonObject> {
            calls++
            if (fail) throw IOException()
            return response("""{"track_list":[{"track":{"track_id":1,"track_name":"${this.title}","artist_name":"Artist","album_name":"Album","track_length":200,"has_subtitles":1}}]}""")
        }
        override suspend fun subtitle(key: String, trackId: Long, format: String, duration: Long?, tolerance: Int): Response<JsonObject> {
            subtitleCalls++
            assertEquals("lrc", format)
            assertEquals(200L, duration)
            return response("""{"subtitle":{"restricted":$restricted,"subtitle_body":${Json.encodeToString(kotlinx.serialization.serializer<String>(), lrc)}}}""")
        }
        private fun response(body: String): Response<JsonObject> = Response.success(
            Json.parseToJsonElement("""{"message":{"header":{"status_code":$status},"body":$body}}""") as JsonObject)
    }

    @Test fun `official provider accepts only genuine matching unrestricted LRC`() = runTest {
        val api = Api()
        val provider = MusixmatchProvider(api, "test-key")
        assertTrue(provider.search(query) is ProviderResponse.Success)
        assertEquals(1, api.subtitleCalls)
        api.lrc = "Plain text only"
        assertEquals(ProviderResponse.NotFound, provider.search(query))
        api.lrc = "[00:99.00]Invalid"
        assertEquals(ProviderResponse.NotFound, provider.search(query))
        api.lrc = "[00:01.00]Valid"; api.restricted = 1
        assertEquals(ProviderResponse.NotFound, provider.search(query))
        api.title = "Song (Live)"; val count = api.subtitleCalls
        assertEquals(ProviderResponse.NotFound, provider.search(query))
        assertEquals(count, api.subtitleCalls)
    }

    @Test fun `unconfigured provider makes no calls and quota blocks repeated requests`() = runTest {
        val api = Api()
        assertEquals(ProviderResponse.NotFound, MusixmatchProvider(api, "").search(query))
        assertEquals(0, api.calls)
        api.status = 402
        val provider = MusixmatchProvider(api, "test-key") { 1000 }
        assertTrue(provider.search(query) is ProviderResponse.RateLimited)
        assertTrue(provider.search(query) is ProviderResponse.RateLimited)
        assertEquals(1, api.calls)
    }

    @Test fun `malformed out of range and decreasing timestamps are unusable`() {
        for (text in listOf("plain", "[00:99.00]bad", "[00:02.00]a\n[00:01.00]b", "[04:00.00]too late", "[ar:Artist]")) {
            assertFalse(text, usableSyncedLyrics(text, 200_000))
        }
        assertTrue(usableSyncedLyrics("[ar:Artist]\n[00:01.250]First\n[00:02.500]Second", 200_000))
    }
}
