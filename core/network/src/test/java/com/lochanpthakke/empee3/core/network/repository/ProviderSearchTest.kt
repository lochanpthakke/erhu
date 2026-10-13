package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.network.model.MusicBrainzGenreDto
import com.lochanpthakke.empee3.core.network.provider.ProviderUnavailableException
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
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

    private fun search(
        provider: MetadataProvider,
        selection: String = MetadataSources.ALL,
        unavailable: String? = null,
    ) = runBlocking {
        searchProviderScored(provider, selection, unavailable, scorer, "Runaway", "Kanye West", "My Beautiful Dark Twisted Fantasy", 548_000L)
    }

    private fun candidate(
        title: String,
        artist: String = "Kanye West",
        album: String = "My Beautiful Dark Twisted Fantasy",
        source: String = "MusicBrainz",
    ) = MetadataCandidate(id = title, title = title, artist = artist, album = album, durationMs = 548_000L, sourceName = source)

    @Test
    fun theStoredLastFmKey_selectsTheLastFmProvider() {
        // Settings stored "LastFm" while the provider is named "Last.fm": the exclusive choice queried nothing.
        assertTrue(MetadataSources.matches("Last.fm", MetadataSources.LAST_FM))
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
    fun unavailableProvider_isSkippedWithItsReason() {
        val lastFm = FakeProvider("Last.fm") { listOf(candidate("Runaway")) }
        val (_, outcome) = search(lastFm, unavailable = "Last.fm rejects the app's API key")
        assertEquals(0, lastFm.calls)
        assertEquals(ProviderStatus.SKIPPED, outcome.status)
        assertEquals("Last.fm rejects the app's API key", outcome.errorMessage)
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
    fun genre_isTheSingleMostVotedMusicBrainzGenre() {
        // Her Loss, MusicBrainz release group 81463cd8…, 2026-10-13.
        val herLoss =
            listOf(
                MusicBrainzGenreDto("contemporary r&b", 1),
                MusicBrainzGenreDto("hip hop", 5),
                MusicBrainzGenreDto("pop rap", 3),
                MusicBrainzGenreDto("trap", 3),
            )
        assertEquals(GenreLookup.Found("hip hop", "MusicBrainz release group"), genreFromMusicBrainz(herLoss))
    }

    @Test
    fun tiedOrMissingGenres_giveNoGenre() {
        // OCTANE, release group 25b63144…: three genres with one vote each.
        val octane = listOf(MusicBrainzGenreDto("alternative r&b", 1), MusicBrainzGenreDto("hip hop", 1), MusicBrainzGenreDto("trap", 1))
        assertTrue(genreFromMusicBrainz(octane) is GenreLookup.NotFound)
        assertTrue(genreFromMusicBrainz(emptyList()) is GenreLookup.NotFound)
    }
}
