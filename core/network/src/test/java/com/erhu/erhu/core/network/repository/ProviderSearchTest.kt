package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataProvider
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.model.MusicBrainzGenreDto
import com.erhu.erhu.core.network.provider.ProviderAuthException
import com.erhu.erhu.core.network.provider.ProviderUnavailableException
import com.erhu.erhu.core.network.retry.RetryPolicy
import com.erhu.erhu.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.erhu.erhu.core.network.scorer.MetadataScorer
import com.erhu.erhu.core.network.scorer.UNCERTAIN_THRESHOLD
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Provider selection, failure reporting and conflict resolution of a metadata search. */
class ProviderSearchTest {
    private class FakeProvider(
        override val providerName: String,
        private val answer: () -> List<MetadataCandidate>,
    ) : MetadataProvider {
        var calls = 0

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> {
            calls++
            return answer()
        }

        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? = null
    }

    private val scorer = MetadataScorer()

    private val noRetry = RetryPolicy(maxAttempts = 1)

    private fun search(
        provider: MetadataProvider,
        selection: String = MetadataSources.ALL,
        availability: ProviderAvailability = ProviderAvailability.Available,
        retry: RetryPolicy = noRetry,
        onSessionFailure: (ProviderStatus, String) -> Unit = { _, _ -> },
    ) = runBlocking {
        searchProviderScored(
            provider,
            selection,
            availability,
            scorer,
            "Runaway",
            "Kanye West",
            "My Beautiful Dark Twisted Fantasy",
            548_000L,
            retry = retry,
            onSessionFailure = onSessionFailure,
        )
    }

    private fun candidate(
        title: String,
        artist: String = "Kanye West",
        album: String = "My Beautiful Dark Twisted Fantasy",
        source: String = "MusicBrainz",
    ) = MetadataCandidate(id = title, title = title, artist = artist, album = album, durationMs = 548_000L, sourceName = source)

    @Test
    fun supportedProviderAliasesKeepExclusiveSelection() {
        assertTrue(MetadataSources.matches("iTunes", "iTunes / Apple Music"))
        assertTrue(MetadataSources.matches("MusicBrainz", "musicbrainz"))
        assertTrue(MetadataSources.matches("Deezer", MetadataSources.ALL))
        assertFalse(MetadataSources.matches("Deezer", MetadataSources.MUSICBRAINZ))
    }

    @Test
    fun unselectedProvider_isSkippedWithoutARequest() {
        val deezer = FakeProvider("Deezer") { listOf(candidate("Runaway")) }
        val (candidates, outcome) = search(deezer, selection = MetadataSources.MUSICBRAINZ)
        assertEquals(0, deezer.calls)
        assertTrue(candidates.isEmpty())
        assertEquals(ProviderStatus.SKIPPED, outcome.status)
    }

    @Test
    fun accessRefusedEarlierIsNotRequestedAgain() {
        val source = FakeProvider("MusicBrainz") { listOf(candidate("Runaway")) }
        val (_, outcome) = search(source, availability = ProviderAvailability.Rejected("Access refused (HTTP 403)"))
        assertEquals(0, source.calls)
        assertEquals(ProviderStatus.AUTH_FAILED, outcome.status)
        assertTrue(outcome.errorMessage!!.contains("not retried"))
    }

    @Test
    fun accessRefusalIsReportedOnce() {
        val rejected = mutableListOf<String>()
        val source = FakeProvider("MusicBrainz") { throw ProviderAuthException("Access refused (HTTP 403)") }
        val (candidates, outcome) = search(source, retry = RetryPolicy(maxAttempts = 3, baseDelayMs = 1), onSessionFailure = { _, reason -> rejected += reason })
        assertTrue(candidates.isEmpty())
        assertEquals(ProviderStatus.AUTH_FAILED, outcome.status)
        assertEquals("access refusals are never retried", 1, source.calls)
        assertEquals(listOf("Access refused (HTTP 403)"), rejected)
    }

    @Test
    fun aDnsFailure_isANetworkError_neverNoResults() {
        val musicBrainz =
            FakeProvider("MusicBrainz") {
                throw java.net.UnknownHostException("Unable to resolve host \"musicbrainz.org\": No address associated with hostname")
            }
        val (candidates, outcome) = search(musicBrainz)
        assertTrue(candidates.isEmpty())
        assertEquals(ProviderStatus.NETWORK_ERROR, outcome.status)
        assertTrue(outcome.errorMessage!!.contains("musicbrainz.org"))
    }

    @Test
    fun aTransientFailure_recoversWithinTheRetryBudget() {
        var attempts = 0
        val musicBrainz =
            FakeProvider("MusicBrainz") {
                attempts++
                if (attempts == 1) throw java.net.SocketTimeoutException("timeout") else listOf(candidate("Runaway"))
            }
        val (candidates, outcome) = search(musicBrainz, retry = RetryPolicy(maxAttempts = 3, baseDelayMs = 1, maxDelayMs = 2))
        assertEquals(2, musicBrainz.calls)
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals(1, candidates.size)
    }

    @Test
    fun emptyResults_areNoResults_andSuccessIsSuccess() {
        assertEquals(ProviderStatus.NO_RESULTS, search(FakeProvider("MusicBrainz") { emptyList() }).second.status)
        assertEquals(ProviderStatus.SUCCESS, search(FakeProvider("MusicBrainz") { listOf(candidate("Runaway")) }).second.status)
    }

    @Test
    fun iTunesOnlyQueriesItsOwnSourceAndExplainsSkippedProviders() {
        val itunes = FakeProvider("iTunes") { listOf(candidate("Runaway", source = "iTunes")) }
        val musicBrainz = FakeProvider("MusicBrainz") { listOf(candidate("Runaway")) }
        assertEquals(ProviderStatus.SUCCESS, search(itunes, selection = MetadataSources.ITUNES).second.status)
        val skipped = search(musicBrainz, selection = MetadataSources.ITUNES).second
        assertEquals(1, itunes.calls)
        assertEquals(0, musicBrainz.calls)
        assertEquals(ProviderStatus.SKIPPED, skipped.status)
    }

    @Test
    fun removedSavedProvidersFallBackToWorkingSources() {
        for (saved in listOf("LastFm", "Last.fm", "FanartTv", "Fanart.tv", "Genius", "DuckDuckGo", "Discogs", "Wikipedia", "Wikimedia")) {
            val provider = FakeProvider("MusicBrainz") { listOf(candidate("Runaway")) }
            assertEquals(MetadataSources.ALL, MetadataSources.normalizeSelection(saved))
            assertEquals(ProviderStatus.SUCCESS, search(provider, selection = saved).second.status)
            assertEquals(1, provider.calls)
            assertEquals(MetadataSources.hostsFor(MetadataSources.ALL), MetadataSources.hostsFor(saved))
        }
    }

    @Test
    fun preflightHostsFollowOnlyTheRemainingSources() {
        assertEquals(
            listOf("itunes.apple.com", "musicbrainz.org", "api.deezer.com"),
            MetadataSources.hostsFor(MetadataSources.ALL),
        )
        assertEquals(listOf("musicbrainz.org"), MetadataSources.hostsFor(MetadataSources.MUSICBRAINZ))
        assertEquals(listOf("itunes.apple.com"), MetadataSources.hostsFor("iTunes / Apple Music"))
    }

    @Test
    fun providerFailure_isReportedAsFailed_withItsMessage() {
        val deezer = FakeProvider("Deezer") { throw ProviderUnavailableException("Deezer found 255 matches but returned none") }
        val (candidates, outcome) = search(deezer)
        assertTrue(candidates.isEmpty())
        assertEquals(ProviderStatus.FAILED, outcome.status)
        assertEquals("Deezer found 255 matches but returned none", outcome.errorMessage)
    }

    @Test
    fun timeout_isReportedAsTimedOut() {
        val slow = FakeProvider("MusicBrainz") { throw java.net.SocketTimeoutException("timeout") }
        assertEquals(ProviderStatus.TIMED_OUT, search(slow).second.status)
    }

    @Test
    fun status_reflectsTheBestCandidate_notTheFirst() {
        val provider = FakeProvider("MusicBrainz") { listOf(candidate("Something Else Entirely"), candidate("Runaway")) }
        val (candidates, outcome) = search(provider)
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertTrue(candidates.maxOf { it.confidenceScore } >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun liveOrRemixVersions_neverReachTheThreshold() {
        val provider =
            FakeProvider("MusicBrainz") {
                listOf(
                    candidate("Runaway").copy(versionNote = "live, 2011-04-17: Coachella"),
                    candidate("Runaway (Remix)"),
                )
            }
        val (candidates, _) = search(provider)
        assertTrue(candidates.all { it.confidenceScore < UNCERTAIN_THRESHOLD })
    }

    @Test
    fun sameVersionMarkers_onBothSides_stillMatch() {
        val scored = scorer.scoreCandidate(candidate("Runaway (Live)"), "Runaway (Live)", "Kanye West")
        assertTrue(scored.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD)
    }

    // ---- genre choice

    @Test
    fun genres_areEveryMusicBrainzGenre_mostVotedFirst() {
        // Her Loss, MusicBrainz release group 81463cd8…, 2026-10-13.
        val herLoss =
            listOf(
                MusicBrainzGenreDto("contemporary r&b", 1),
                MusicBrainzGenreDto("hip hop", 5),
                MusicBrainzGenreDto("pop rap", 3),
                MusicBrainzGenreDto("trap", 3),
            )
        val found = genreFromMusicBrainz(herLoss) as GenreLookup.Found
        assertEquals(listOf("hip hop", "pop rap", "trap", "contemporary r&b"), found.genres)
        assertEquals("hip hop; pop rap; trap; contemporary r&b", found.genre)
    }

    @Test
    fun tiedGenres_areAllKept_notDropped() {
        // OCTANE, release group 25b63144…: three genres with one vote each. This tie used to give no
        // genre at all (the reported bug: multi-genre results left the genre empty).
        val octane = listOf(MusicBrainzGenreDto("alternative r&b", 1), MusicBrainzGenreDto("hip hop", 1), MusicBrainzGenreDto("trap", 1))
        assertEquals(GenreLookup.Found(listOf("alternative r&b", "hip hop", "trap"), "MusicBrainz release group"), genreFromMusicBrainz(octane))
    }

    @Test
    fun missingBlankOrDuplicateGenres_areCleanedUp() {
        assertTrue(genreFromMusicBrainz(emptyList()) is GenreLookup.NotFound)
        assertTrue(genreFromMusicBrainz(listOf(MusicBrainzGenreDto("  ", 3))) is GenreLookup.NotFound)
        val messy = listOf(MusicBrainzGenreDto(" Synth-pop ", 2), MusicBrainzGenreDto("synth-pop", 1), MusicBrainzGenreDto("electronic", 2))
        assertEquals(listOf("Synth-pop", "electronic"), (genreFromMusicBrainz(messy) as GenreLookup.Found).genres)
    }
}
