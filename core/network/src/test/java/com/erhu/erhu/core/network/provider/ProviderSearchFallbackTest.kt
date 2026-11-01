package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.api.DeezerApi
import com.erhu.erhu.core.network.model.DeezerAlbumDetailDto
import com.erhu.erhu.core.network.model.DeezerAlbumDto
import com.erhu.erhu.core.network.model.DeezerArtistDto
import com.erhu.erhu.core.network.model.DeezerArtistSearchResponse
import com.erhu.erhu.core.network.model.DeezerSearchResponse
import com.erhu.erhu.core.network.model.DeezerTrackDto
import com.erhu.erhu.core.network.repository.DetailedSearchResponse
import com.erhu.erhu.core.network.repository.MatchDecision
import com.erhu.erhu.core.network.repository.MetadataSources
import com.erhu.erhu.core.network.repository.ProviderAvailability
import com.erhu.erhu.core.network.repository.TrackIdentity
import com.erhu.erhu.core.network.repository.TrackMatcher
import com.erhu.erhu.core.network.repository.TrackQuery
import com.erhu.erhu.core.network.repository.searchProviderScored
import com.erhu.erhu.core.network.retry.RetryPolicy
import com.erhu.erhu.core.network.scorer.MetadataScorer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lochan's fallback (2026-10-14): when a search narrowed by album / artist finds nothing, search
 * the title alone. In the merged code it is the matcher's last planned query (visible in the
 * search details, run only when nothing confident was found) instead of a hidden second request
 * inside each provider; and a network error is reported as one, not as an empty result.
 */
class ProviderSearchFallbackTest {
    private val logger =
        object : Logger {
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

    private class FakeDeezerApi : DeezerApi {
        val queries = mutableListOf<String>()
        var strictResponse: List<DeezerTrackDto> = emptyList()
        var fallbackResponse: List<DeezerTrackDto> = emptyList()
        var shouldThrow = false

        override suspend fun searchTrack(query: String): DeezerSearchResponse {
            queries.add(query)
            if (shouldThrow) throw java.io.IOException("Network offline")
            val data = if (query.startsWith("album:")) strictResponse else fallbackResponse
            return DeezerSearchResponse(data = data, total = data.size)
        }

        override suspend fun searchArtist(
            query: String,
            limit: Int,
        ) = DeezerArtistSearchResponse()

        override suspend fun getAlbum(albumId: Long) = DeezerAlbumDetailDto()
    }

    private val scorer = MetadataScorer()

    /** "Diwali" from the album "Soma", 3:30, with no artist tag. */
    private val diwali = TrackIdentity(title = "Diwali", artist = null, album = "Soma", durationMs = 210_000L)

    private fun match(api: FakeDeezerApi) =
        runBlocking {
            val deezer = DeezerProvider(api, logger)
            TrackMatcher(scorer).match(diwali) { query: TrackQuery ->
                val (candidates, outcome) =
                    searchProviderScored(
                        deezer,
                        MetadataSources.ALL,
                        ProviderAvailability.Available,
                        scorer,
                        query.title,
                        query.artist,
                        query.album,
                        diwali.durationMs,
                        retry = RetryPolicy(maxAttempts = 1),
                    )
                DetailedSearchResponse(candidates, listOf(outcome))
            }
        }

    private fun track(
        id: Long,
        album: String,
    ) = DeezerTrackDto(id, "Diwali", duration = 210, artist = DeezerArtistDto(10L, "Skrillex"), album = DeezerAlbumDto(100L + id, album))

    @Test
    fun strictQueryReturnsCandidates_fallbackIsNotExecuted() {
        val api = FakeDeezerApi().apply { strictResponse = listOf(track(1, "Soma")) }

        val result = match(api)

        assertEquals(listOf("album:\"Soma\" track:\"Diwali\""), api.queries)
        assertEquals("Soma", (result.decision as MatchDecision.Confident).match.album)
    }

    @Test
    fun strictQueryReturnsEmpty_fallbackIsExecutedAndCandidatesReturned() {
        val api =
            FakeDeezerApi().apply {
                strictResponse = emptyList()
                fallbackResponse = listOf(track(2, "Owsla"))
            }

        val result = match(api)

        assertEquals(listOf("album:\"Soma\" track:\"Diwali\"", "Diwali"), api.queries)
        assertEquals(TrackQuery("Diwali", null, TrackQuery.Source.TAG), result.queries.last())
        val found = result.candidates.single()
        assertEquals("Diwali", found.title)
        assertEquals("Owsla", found.album)
    }

    @Test
    fun fallbackCandidateScoredAgainstLocalTargetAlbum_evaluatesAccurately() {
        val candidate =
            MetadataCandidate(
                id = "deezer_2",
                title = "Diwali",
                artist = "Lenky",
                album = "Invasion Of The Mysteron Killer Sounds Vol. 1",
                sourceName = "Deezer",
            )
        val scoredMismatch = scorer.scoreCandidate(candidate = candidate, targetTitle = "Diwali", targetArtist = null, targetAlbum = "Soma")
        val scoredMatch =
            scorer.scoreCandidate(candidate = candidate.copy(album = "Soma"), targetTitle = "Diwali", targetArtist = null, targetAlbum = "Soma")
        assertTrue("Matching album should score higher than mismatching album", scoredMatch.confidenceScore > scoredMismatch.confidenceScore)
        assertEquals(1.0f, scoredMatch.confidenceScore, 0.001f)
    }

    @Test
    fun strictQueryFailsDueToNetworkError_isReportedAsANetworkFailure() {
        val api = FakeDeezerApi().apply { shouldThrow = true }

        val result = match(api)

        assertTrue(result.candidates.isEmpty())
        assertTrue(result.attempts.all { attempt -> attempt.outcomes.all { it.status == ProviderStatus.NETWORK_ERROR } })
    }
}
