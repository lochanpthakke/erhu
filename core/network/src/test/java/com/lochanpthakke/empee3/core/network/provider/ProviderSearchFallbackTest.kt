package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import com.lochanpthakke.empee3.core.network.model.DeezerAlbumDto
import com.lochanpthakke.empee3.core.network.model.DeezerArtistDto
import com.lochanpthakke.empee3.core.network.model.DeezerSearchResponse
import com.lochanpthakke.empee3.core.network.model.DeezerTrackDto
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSearchFallbackTest {
    private val logger = object : Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) {}
        override fun d(tag: String, message: String, throwable: Throwable?) {}
        override fun i(tag: String, message: String, throwable: Throwable?) {}
        override fun w(tag: String, message: String, throwable: Throwable?) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }

    private class FakeDeezerApi : DeezerApi {
        val queries = mutableListOf<String>()
        var strictResponse: List<DeezerTrackDto> = emptyList()
        var fallbackResponse: List<DeezerTrackDto> = emptyList()
        var shouldThrow = false

        override suspend fun searchTrack(query: String): DeezerSearchResponse {
            queries.add(query)
            if (shouldThrow) throw java.io.IOException("Network offline")
            val data = if (query.startsWith("album:")) strictResponse else fallbackResponse
            return DeezerSearchResponse(data = data)
        }

        override suspend fun searchArtist(query: String, limit: Int) = com.lochanpthakke.empee3.core.network.model.DeezerArtistSearchResponse()
    }

    @Test
    fun strictQueryReturnsCandidates_fallbackIsNotExecuted() = runBlocking {
        val api = FakeDeezerApi().apply {
            strictResponse = listOf(
                DeezerTrackDto(1L, "Diwali", artist = DeezerArtistDto(10L, "Skrillex"), album = DeezerAlbumDto(100L, "Soma"))
            )
        }
        val provider = DeezerProvider(api, logger)
        val result = provider.searchTrack("Diwali", artist = null, album = "Soma")

        assertEquals(1, api.queries.size)
        assertEquals("album:\"Soma\" track:\"Diwali\"", api.queries.first())
        assertEquals(1, result.size)
        assertEquals("Diwali", result.first().title)
    }

    @Test
    fun strictQueryReturnsEmpty_fallbackIsExecutedAndCandidatesReturned() = runBlocking {
        val api = FakeDeezerApi().apply {
            strictResponse = emptyList()
            fallbackResponse = listOf(
                DeezerTrackDto(2L, "Diwali", artist = DeezerArtistDto(10L, "Skrillex"), album = DeezerAlbumDto(200L, "Owsla"))
            )
        }
        val provider = DeezerProvider(api, logger)
        val result = provider.searchTrack("Diwali", artist = null, album = "Soma")

        assertEquals(2, api.queries.size)
        assertEquals("album:\"Soma\" track:\"Diwali\"", api.queries[0])
        assertEquals("Diwali", api.queries[1])
        assertEquals(1, result.size)
        assertEquals("Diwali", result.first().title)
        assertEquals("Owsla", result.first().album)
    }

    @Test
    fun fallbackCandidateScoredAgainstLocalTargetAlbum_evaluatesAccurately() = runBlocking {
        val scorer = MetadataScorer()
        val candidate = MetadataCandidate(
            id = "deezer_2",
            title = "Diwali",
            artist = "Lenky",
            album = "Invasion Of The Mysteron Killer Sounds Vol. 1",
            sourceName = "Deezer",
        )
        val scoredMismatch = scorer.scoreCandidate(
            candidate = candidate,
            targetTitle = "Diwali",
            targetArtist = null,
            targetAlbum = "Soma",
        )
        val scoredMatch = scorer.scoreCandidate(
            candidate = candidate.copy(album = "Soma"),
            targetTitle = "Diwali",
            targetArtist = null,
            targetAlbum = "Soma",
        )
        assertTrue("Matching album should score higher than mismatching album", scoredMatch.confidenceScore > scoredMismatch.confidenceScore)
        assertEquals(1.0f, scoredMatch.confidenceScore, 0.001f)
    }

    @Test
    fun strictQueryFailsDueToNetworkError_noFallbackAndHandledSafely() = runBlocking {
        val api = FakeDeezerApi().apply { shouldThrow = true }
        val provider = DeezerProvider(api, logger)
        val result = provider.searchTrack("Diwali", artist = null, album = "Soma")

        assertTrue(result.isEmpty())
        assertEquals(1, api.queries.size)
    }
}
