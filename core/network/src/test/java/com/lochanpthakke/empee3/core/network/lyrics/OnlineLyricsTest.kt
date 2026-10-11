package com.lochanpthakke.empee3.core.network.lyrics

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.LrclibApi
import com.lochanpthakke.empee3.core.network.model.LrclibLyricsDto
import com.lochanpthakke.empee3.core.network.provider.LrclibProvider
import com.lochanpthakke.empee3.core.network.provider.LrclibProvider.Companion.toCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

class OnlineLyricsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val wordSyncedLyricsfile =
        """
        version: '1.0'
        metadata:
          title: Test Song
          artist: Someone
          duration_ms: 200000
          instrumental: false
        lines:
        - text: Hello world
          start_ms: 12000
          end_ms: 14000
          words:
          - text: 'Hello '
            start_ms: 12000
          - text: world
            start_ms: 12650
        - text: Second line
          start_ms: 15000
        plain: |-
          Hello world
          Second line
        """.trimIndent()

    private fun dto(
        id: Long = 1,
        title: String = "Test Song",
        artist: String = "Someone",
        album: String? = "Album",
        duration: Float = 200f,
        synced: String? = "[00:12.00]Hello world\n[00:15.00]Second line",
        wordSync: Boolean = false,
        lyricsfile: String? = null,
        instrumental: Boolean = false,
    ) = LrclibLyricsDto(id, title, artist, album, duration, instrumental, "Hello world\nSecond line", synced, wordSync, lyricsfile)

    // Parsing and word-sync detection

    @Test
    fun `api response fields are parsed, including word sync`() {
        val body =
            """{"id":390,"trackName":"Test Song","artistName":"Someone","albumName":"Album","duration":200.0,"instrumental":false,
              "hasWordSync":true,"plainLyrics":"Hello world","syncedLyrics":"[00:12.00]Hello world","lyricsfile":${json.encodeToString(
                kotlinx.serialization.serializer<String>(),
                wordSyncedLyricsfile,
            )},"name":"Test Song"}"""
        val parsed = json.decodeFromString<LrclibLyricsDto>(body)
        assertTrue(parsed.hasWordSync)
        val candidate = parsed.toCandidate()!!
        assertEquals(LyricsTiming.WORD, candidate.timing)
        assertEquals("[00:12.000]<00:12.000>Hello <00:12.650>world\n[00:15.000]Second line", candidate.wordSyncedLrc)
    }

    @Test
    fun `synced lyrics alone are line timing, never word timing`() {
        val line = dto().toCandidate()!!
        assertEquals(LyricsTiming.LINE, line.timing)
        assertNull(line.wordSyncedLrc)
        // A Lyricsfile is only trusted for word timing when the record says it has word sync.
        assertNull(dto(lyricsfile = wordSyncedLyricsfile, wordSync = false).toCandidate()!!.wordSyncedLrc)
        assertEquals(LyricsTiming.PLAIN, dto(synced = null).toCandidate()!!.timing)
    }

    @Test
    fun `lyricsfile lines with incomplete word timing keep line timing only`() {
        val partial =
            """
            lines:
            - text: a b
              start_ms: 1000
              words:
              - text: 'a '
                start_ms: 1000
              - text: b
            - text: c d
              start_ms: 3000
              words:
              - text: 'c '
                start_ms: 3000
              - text: d
                start_ms: 3400
            """.trimIndent()
        assertEquals("[00:01.000]a b\n[00:03.000]<00:03.000>c <00:03.400>d", Lyricsfile.toEnhancedLrc(partial))
        assertNull(Lyricsfile.toEnhancedLrc("lines:\n- text: only lines\n  start_ms: 5"))
        assertNull(Lyricsfile.toEnhancedLrc("not: [valid"))
        assertNull(Lyricsfile.toEnhancedLrc(null))
        assertTrue(Lyricsfile.isInstrumental("metadata:\n  instrumental: true\nlines: []"))
    }

    // Matching

    private val query = LyricsQuery("Test Song", "Someone", "Album", 200_000)

    private fun candidate(
        title: String = "Test Song",
        artist: String = "Someone",
        album: String? = "Album",
        duration: Double? = 200.0,
        id: Long = 1,
        synced: String? = "[00:01.00]x",
        word: String? = null,
    ) = LyricsCandidate("LRCLIB", id, title, artist, album, duration, plainLyrics = "x", syncedLyrics = synced, wordSyncedLrc = word)

    @Test
    fun `an exact match is confident and harmless differences are ignored`() {
        assertTrue(LyricsMatcher.score(query, candidate()).confident)
        assertTrue(LyricsMatcher.score(query, candidate(title = "Test Song (feat. Guest)", artist = "Someone & Guest")).confident)
        assertTrue(LyricsMatcher.score(query, candidate(title = "TEST SONG - 2011 Remaster", duration = 201.5)).confident)
        assertTrue(LyricsMatcher.score(LyricsQuery("Café", "Beyoncé", null, 180_000), candidate("Cafe", "Beyonce", null, 181.0)).confident)
        assertTrue(LyricsMatcher.score(LyricsQuery("Stay With Me", "Sam Smith", null, 172_000), candidate("Stay With Me", "Sam Smith", null, 172.0)).confident)
        assertTrue(LyricsMatcher.score(LyricsQuery("Help!", "The Beatles", null, 140_000), candidate("Help!", "Beatles", null, 139.0)).confident)
        // A different artist whose name contains the other is never confident.
        assertFalse(LyricsMatcher.score(query, candidate(artist = "Someone Else")).confident)
    }

    @Test
    fun `other versions and other lengths are rejected`() {
        assertTrue(LyricsMatcher.score(query, candidate(title = "Test Song (Live)")).rejected)
        assertTrue(LyricsMatcher.score(query, candidate(album = "Live at Wembley")).rejected)
        assertTrue(LyricsMatcher.score(query, candidate(title = "Test Song - Remix")).rejected)
        assertTrue(LyricsMatcher.score(query, candidate(title = "Test Song (Acoustic)")).rejected)
        assertTrue(LyricsMatcher.score(query, candidate(duration = 230.0)).rejected)
        // The same version on both sides is fine.
        val live = LyricsQuery("Test Song (Live)", "Someone", null, 210_000)
        assertTrue(LyricsMatcher.score(live, candidate(title = "Test Song - Live", album = null, duration = 210.0)).confident)
        // Clean vs explicit only lowers the score.
        assertFalse(LyricsMatcher.score(query, candidate(title = "Test Song (Clean)")).rejected)
    }

    @Test
    fun `weak matches are not confident but can be offered`() {
        val weak = candidate(title = "Test Song Two", artist = "Someone Else")
        assertFalse(LyricsMatcher.score(query, weak).confident)
        // Unknown duration: only an exact title and artist are trusted.
        val noDuration = LyricsQuery("Test Song", "Someone", null, 0)
        assertTrue(LyricsMatcher.score(noDuration, candidate(duration = null)).confident)
        assertFalse(LyricsMatcher.score(noDuration, candidate(title = "Test Songs", duration = null)).confident)
        assertTrue(LyricsMatcher.plausible(query, listOf(candidate(title = "Test Song", artist = "Someone Else"))).isNotEmpty())
        assertTrue(LyricsMatcher.plausible(query, listOf(candidate(title = "Test Song (Live)"))).isEmpty()) // rejected never offered
    }

    @Test
    fun `ranking prefers word timing, then line timing, then plain`() {
        val plain = candidate(id = 1, synced = null)
        val line = candidate(id = 2)
        val word = candidate(id = 3, word = "[00:01.000]<00:01.000>x")
        assertEquals(3L, LyricsMatcher.best(query, listOf(plain, line, word))!!.providerId)
        assertEquals(2L, LyricsMatcher.best(query, listOf(plain, line))!!.providerId)
        assertNull(LyricsMatcher.best(query, listOf(candidate(title = "Other"))))
    }

    // Provider: HTTP handling

    private class FakeApi : LrclibApi {
        var calls = 0
        var get: () -> Response<LrclibLyricsDto> = { throw IllegalStateException("set in the test") }
        var search: () -> Response<List<LrclibLyricsDto>> = { Response.success(emptyList()) }

        override suspend fun getLyrics(
            trackName: String,
            artistName: String,
            albumName: String?,
            durationInSeconds: Int?,
        ): Response<LrclibLyricsDto> {
            calls++
            return get()
        }

        override suspend fun searchLyrics(
            query: String?,
            trackName: String?,
            artistName: String?,
            albumName: String?,
        ): Response<List<LrclibLyricsDto>> {
            calls++
            return search()
        }
    }

    private object SilentLogger : Logger {
        override fun v(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    private var clock = 1_000_000L
    private val api = FakeApi()
    private val provider = LrclibProvider(api, SilentLogger, { clock }, 0L)

    @Test
    fun `404 is not found and success is a candidate`() =
        runBlocking {
            api.get = { error(404) }
            assertEquals(ProviderResponse.NotFound, provider.exact(query))
            api.get = { Response.success(dto()) }
            assertEquals(1L, (provider.exact(query) as ProviderResponse.Success).value.providerId)
        }

    @Test
    fun `429 blocks every request until Retry-After has passed`() =
        runBlocking {
            api.get = { error(429, "Retry-After" to "30") }
            val limited = provider.exact(query) as ProviderResponse.RateLimited
            assertEquals(clock + 30_000, limited.retryAtMs)
            val callsBefore = api.calls
            assertTrue(provider.search(query) is ProviderResponse.RateLimited) // no request sent
            assertEquals(callsBefore, api.calls)
            clock += 31_000
            api.get = { Response.success(dto()) }
            assertTrue(provider.exact(query) is ProviderResponse.Success)
            assertEquals(60_000L, provider.retryAfterMs(null)) // missing header: a sensible wait
            assertEquals(5_000L, provider.retryAfterMs("5"))
        }

    @Test
    fun `no connection is offline, other failures are failures`() =
        runBlocking {
            api.get = { throw UnknownHostException("lrclib.net") }
            assertEquals(ProviderResponse.Offline, provider.exact(query))
            api.get = { error(500) }
            assertTrue(provider.exact(query) is ProviderResponse.Failed)
        }

    private fun <T> error(
        code: Int,
        header: Pair<String, String>? = null,
    ): Response<T> {
        val raw =
            okhttp3.Response
                .Builder()
                .code(code)
                .message("x")
                .protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("https://lrclib.net/api/get").build())
                .apply { header?.let { header(it.first, it.second) } }
                .build()
        return Response.error("{}".toResponseBody(), raw)
    }

    // Repository: cache, dedup, switching

    private class MemoryCache : OnlineLyricsCache {
        val entries = mutableMapOf<Long, CachedLyrics>()

        override suspend fun get(songId: Long) = entries[songId]

        override suspend fun put(entry: CachedLyrics) {
            entries[entry.songId] = entry
        }

        override suspend fun remove(songId: Long) {
            entries.remove(songId)
        }
    }

    private class FakeProvider : OnlineLyricsProvider {
        override val name = "Fake"
        var exactCalls = 0
        var searchCalls = 0
        var exactResult: ProviderResponse<LyricsCandidate> = ProviderResponse.NotFound
        var searchResult: ProviderResponse<List<LyricsCandidate>> = ProviderResponse.Success(emptyList())
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate> {
            exactCalls++
            gate?.await()
            return exactResult
        }

        override suspend fun search(
            query: LyricsQuery,
            freeText: String?,
        ): ProviderResponse<List<LyricsCandidate>> {
            searchCalls++
            return searchResult
        }
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Default
        override val default = Dispatchers.Default
    }

    private val fake = FakeProvider()
    private val cache = MemoryCache()
    private val repository = OnlineLyricsRepository(listOf(fake), cache, TestDispatchers) { clock }
    private val track = LyricsTrack(7, query)

    @Test
    fun `a confident result is cached and the next lookup is a cache hit`() =
        runBlocking {
            fake.exactResult = ProviderResponse.Success(candidate(word = "[00:01.000]<00:01.000>x"))
            val first = repository.lookup(track) as OnlineLyricsResult.Found
            assertFalse(first.fromCache)
            assertEquals(0, fake.searchCalls) // a confident word-timed exact match needs no search
            val second = repository.lookup(track) as OnlineLyricsResult.Found
            assertTrue(second.fromCache)
            assertEquals(1, fake.exactCalls)
            // Re-tagged file (other metadata): the cached result no longer applies.
            assertNull(repository.cached(track.copy(query = query.copy(title = "Renamed"))))
            // Refresh asks again.
            repository.lookup(track, refresh = true)
            assertEquals(2, fake.exactCalls)
        }

    @Test
    fun `line timed exact result still searches for word timing and prefers it`() =
        runBlocking {
            fake.exactResult = ProviderResponse.Success(candidate(id = 1))
            fake.searchResult = ProviderResponse.Success(listOf(candidate(id = 2, word = "[00:01.000]<00:01.000>x")))
            val found = repository.lookup(track) as OnlineLyricsResult.Found
            assertEquals(2L, found.candidate.providerId)
        }

    @Test
    fun `uncertain results are offered, not shown, and not cached`() =
        runBlocking {
            fake.searchResult = ProviderResponse.Success(listOf(candidate(title = "Test Song", artist = "Someone Else", id = 5)))
            val result = repository.lookup(track)
            assertTrue(result is OnlineLyricsResult.NeedsChoice)
            assertTrue(cache.entries.isEmpty())
            // The user picks it: kept and preferred.
            repository.choose(track, (result as OnlineLyricsResult.NeedsChoice).candidates.single())
            assertTrue(cache.entries.getValue(7).userChosen)
        }

    @Test
    fun `not found is remembered for a while, failures and offline are not`() =
        runBlocking {
            assertEquals(OnlineLyricsResult.NotFound, repository.lookup(track))
            repository.lookup(track)
            assertEquals(1, fake.exactCalls)
            clock += OnlineLyricsRepository.NOT_FOUND_TTL_MS + 1
            repository.lookup(track)
            assertEquals(2, fake.exactCalls)

            cache.entries.clear()
            fake.exactResult = ProviderResponse.Offline
            assertEquals(OnlineLyricsResult.Offline, repository.lookup(track))
            assertTrue(cache.entries.isEmpty())
            fake.exactResult = ProviderResponse.RateLimited(clock + 5_000)
            assertTrue(repository.lookup(track) is OnlineLyricsResult.RateLimited)
            assertTrue(cache.entries.isEmpty())
        }

    @Test
    fun `instrumental is reported and cached`() =
        runBlocking {
            fake.exactResult = ProviderResponse.Success(candidate().copy(instrumental = true, plainLyrics = null, syncedLyrics = null))
            assertEquals(OnlineLyricsResult.Instrumental, repository.lookup(track))
            assertEquals(OnlineLyricsResult.Instrumental, repository.lookup(track))
            assertEquals(1, fake.exactCalls)
        }

    @Test
    fun `concurrent lookups of one track share one request, and a caller leaving does not cancel it`() =
        runBlocking {
            fake.exactResult = ProviderResponse.Success(candidate(word = "[00:01.000]<00:01.000>x"))
            fake.gate = CompletableDeferred()
            val a = async(Dispatchers.Default) { repository.lookup(track) }
            val b = async(Dispatchers.Default) { repository.lookup(track) }
            val leaving = async(Dispatchers.Default) { repository.lookup(track) }
            withTimeout(2_000) { while (fake.exactCalls == 0) kotlinx.coroutines.delay(5) }
            leaving.cancel() // the user switched tracks
            fake.gate!!.complete(Unit)
            assertTrue(a.await() is OnlineLyricsResult.Found)
            assertTrue(b.await() is OnlineLyricsResult.Found)
            assertEquals(1, fake.exactCalls)
            assertTrue(cache.entries.containsKey(7)) // the shared lookup still finished and was cached
        }
}
