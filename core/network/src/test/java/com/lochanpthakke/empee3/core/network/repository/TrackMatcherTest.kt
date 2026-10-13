package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.model.MusicBrainzSearchResponse
import com.lochanpthakke.empee3.core.network.provider.musicBrainzCandidates
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "THANK YOU.mp3" on the S23: embedded title "GONE, GONE / THANK YOU", artist "Tyler/ The Creator",
 * album IGOR, 6:15. MusicBrainz (real response, 2026-10-13) returns the 6:15 explicit album
 * recording, a 4:32 IGOR recording and a 4:39 live session, for both the file-name and the tag query.
 */
class TrackMatcherTest {
    private val scorer = MetadataScorer()
    private val matcher = TrackMatcher(scorer)
    private val json = Json { ignoreUnknownKeys = true }

    private val musicBrainz: List<MetadataCandidate> =
        musicBrainzCandidates(
            json.decodeFromString<MusicBrainzSearchResponse>(
                requireNotNull(javaClass.classLoader?.getResource("musicbrainz_gone_gone_thank_you_search.json")).readText(),
            ),
            "IGOR",
            "MusicBrainz",
            LocalDate.of(2026, 10, 13),
        )

    /** A different, short song whose title is the file name. */
    private val decoy =
        MetadataCandidate("dz_1", "Thank You", "Dido", "No Angel", durationMs = 218_000L, sourceName = "Deezer")

    private val thankYouFile =
        TrackIdentity(
            title = "GONE, GONE / THANK YOU",
            artist = "Tyler/ The Creator",
            album = "IGOR",
            durationMs = 375_000L,
            fileTitles = listOf("THANK YOU"),
        )

    private fun provider(found: List<MetadataCandidate>) =
        object : MetadataProvider {
            override val providerName = "MusicBrainz"

            override suspend fun searchTrack(
                title: String,
                artist: String?,
                album: String?,
            ) = found

            override suspend fun fetchAlbumArt(
                albumTitle: String,
                artist: String?,
            ): String? = null
        }

    /** The real search path (provider search scored by the repository), recording each query. */
    private fun runMatch(
        identity: TrackIdentity,
        found: List<MetadataCandidate> = musicBrainz + decoy,
        queries: MutableList<TrackQuery> = mutableListOf(),
    ): MatchResult =
        runBlocking {
            matcher.match(identity) { query ->
                queries += query
                val (candidates, outcome) =
                    searchProviderScored(provider(found), "ALL", null, scorer, query.title, query.artist, identity.album, identity.durationMs)
                DetailedSearchResponse(candidates.sortedByDescending { it.confidenceScore }, listOf(outcome))
            }
        }

    @Test
    fun theOldFilenameScoring_reproducesTheFailure() {
        // Before: every candidate was scored against the query text, i.e. the file name.
        val scored = musicBrainz.map { scorer.scoreCandidate(it, "THANK YOU", "Tyler/ The Creator", "IGOR", 375_000L) }
        assertTrue(
            "best was ${scored.maxOf { it.confidenceScore }}",
            scored.maxOf { it.confidenceScore } < HIGH_CONFIDENCE_THRESHOLD,
        )
    }

    @Test
    fun theAlbumVersionIsFound_fromTheEmbeddedTitle() {
        val queries = mutableListOf<TrackQuery>()
        val result = runMatch(thankYouFile, queries = queries)

        val decision = result.decision as MatchDecision.Confident
        assertEquals(375_000L, decision.match.durationMs)
        assertEquals("GONE, GONE / THANK YOU", decision.match.title)
        assertEquals("IGOR", decision.match.album)
        assertEquals("explicit", decision.match.versionNote)
        assertEquals("2019-08-23", decision.match.releaseDate)
        // The tag title is searched first, and a confident answer ends the search.
        assertEquals(listOf(TrackQuery("GONE, GONE / THANK YOU", "Tyler/ The Creator", TrackQuery.Source.TAG)), queries)
    }

    @Test
    fun durationSeparatesTheIgorRecordings_withoutRejectingTheOthers() {
        val ranked = runMatch(thankYouFile).candidates.filter { it.sourceName == "MusicBrainz" }
        val album = ranked.single { it.durationMs == 375_000L }
        val short = ranked.single { it.durationMs == 272_000L }
        assertTrue(album.confidenceScore > short.confidenceScore)
        assertTrue("a signal, not a rejection", short.confidenceScore > 0.5f)
        assertTrue(short.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun theUnrelatedShortSong_isNeverAccepted() {
        val withDecoy = runMatch(thankYouFile)
        assertTrue(withDecoy.candidates.single { it.id == "dz_1" }.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)

        // Even searching by the file name alone, the decoy's exact title doesn't make it a match.
        val fileNameOnly = thankYouFile.copy(title = null)
        val decision = runMatch(fileNameOnly, found = listOf(decoy)).decision
        assertFalse("$decision", decision is MatchDecision.Confident)
    }

    @Test
    fun withoutTags_anAbbreviatedFileNameStaysUncertain_andSaysWhy() {
        val decision = runMatch(thankYouFile.copy(title = null), found = musicBrainz).decision
        assertFalse(decision is MatchDecision.Confident)
        val reason = (decision as? MatchDecision.LowConfidence)?.reason ?: (decision as MatchDecision.NoMatch).reason.orEmpty()
        assertTrue(reason, reason.contains("GONE, GONE / THANK YOU") && reason.contains("vs file 6:15") && reason.contains("%"))
    }

    @Test
    fun candidatesFoundByTwoQueries_areScoredOnce() {
        val identity = thankYouFile.copy(fileTitles = listOf("THANK YOU", "THANK"))
        val result = runMatch(identity.copy(title = null), found = musicBrainz)
        assertEquals(result.candidates.map { it.id }.distinct(), result.candidates.map { it.id })
        assertEquals(musicBrainz.size, result.candidates.size)
    }

    @Test
    fun equallyGoodMatchesThatWouldWriteDifferentTags_areAmbiguous() {
        val a = MetadataCandidate("a", "Runaway", "Kanye West", "My Beautiful Dark Twisted Fantasy", durationMs = 548_000L, sourceName = "MusicBrainz")
        val b = a.copy(id = "b", album = "Runaway (Single)")
        val identity = TrackIdentity("Runaway", "Kanye West", null, 548_000L)
        val decision = runMatch(identity, found = listOf(a, b)).decision
        assertTrue("$decision", decision is MatchDecision.Ambiguous)

        // Two releases that write the same tags (clean / explicit pressings) are not a conflict.
        val sameTags = a.copy(id = "c", artist = "Kanye West feat. Pusha T")
        assertTrue(runMatch(identity, found = listOf(a, sameTags)).decision is MatchDecision.Confident)
    }

    @Test
    fun queries_comeFromTagsFirst_thenTheFileName_withoutDuplicates() {
        val planned = planQueries(thankYouFile.copy(fileTitles = listOf("THANK YOU", "gone gone thank you")))
        assertEquals(
            listOf(
                TrackQuery("GONE, GONE / THANK YOU", "Tyler/ The Creator", TrackQuery.Source.TAG),
                TrackQuery("THANK YOU", "Tyler/ The Creator", TrackQuery.Source.FILE_NAME),
            ),
            planned,
        )
    }

    @Test
    fun watermarkedPlaceholderOrMisreadTags_areNotUsedAsTheIdentity() {
        assertEquals(null, usableIdentityText("Unknown Artist"))
        assertEquals(null, usableIdentityText("runaway_spotidownloader.com"))
        assertEquals(null, usableIdentityText("IGORâ€™S THEME"))
        assertEquals("IGOR’S THEME", usableIdentityText(" IGOR’S THEME "))
    }
}
