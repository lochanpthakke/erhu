package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.network.model.DeezerAlbumDto
import com.erhu.erhu.core.network.model.DeezerArtistDto
import com.erhu.erhu.core.network.model.DeezerErrorDto
import com.erhu.erhu.core.network.model.DeezerSearchResponse
import com.erhu.erhu.core.network.model.DeezerTrackDto
import com.erhu.erhu.core.network.model.MusicBrainzReleaseDto
import com.erhu.erhu.core.network.model.MusicBrainzReleaseGroupDto
import com.erhu.erhu.core.network.model.MusicBrainzSearchResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/** Response parsing and field mapping of the three metadata providers, from fixed responses. */
class ProviderMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun assertUnavailable(
        expectedFragment: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("expected ProviderUnavailableException")
        } catch (e: ProviderUnavailableException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(expectedFragment))
        }
    }

    // ---- Deezer

    /** Exactly what api.deezer.com answered from India on 2026-10-13 ("open": false in /infos). */
    @Test
    fun deezer_resultsWithheldInThisCountry_isAFailureNotNoResults() {
        val response = json.decodeFromString<DeezerSearchResponse>("""{"data":[],"total":255,"next":"https://api.deezer.com/search?index=3"}""")
        assertUnavailable("isn't available in this country") { deezerTrackCandidates(response, "Deezer") }
    }

    @Test
    fun deezer_genuinelyNoMatches_isAnEmptyList() {
        val response = json.decodeFromString<DeezerSearchResponse>("""{"data":[],"total":0}""")
        assertEquals(emptyList<Any>(), deezerTrackCandidates(response, "Deezer"))
    }

    @Test
    fun deezer_apiError_isAFailure() {
        val response = DeezerSearchResponse(error = DeezerErrorDto(type = "Exception", message = "Quota limit exceeded", code = 4))
        assertUnavailable("Quota limit exceeded") { deezerTrackCandidates(response, "Deezer") }
    }

    @Test
    fun deezer_mapsFields_andNeverInventsValues() {
        val response =
            DeezerSearchResponse(
                data =
                    listOf(
                        DeezerTrackDto(
                            id = 7,
                            title = "Runaway",
                            titleVersion = "(Live)",
                            duration = 548,
                            bpm = 0f,
                            artist = DeezerArtistDto(id = 230, name = "Kanye West"),
                            album = DeezerAlbumDto(id = 302, title = "My Beautiful Dark Twisted Fantasy", coverBig = "big.jpg"),
                        ),
                        DeezerTrackDto(id = 8, title = "No Album", artist = null, album = null),
                    ),
                total = 2,
            )
        val (full, bare) = deezerTrackCandidates(response, "Deezer")

        assertEquals("Kanye West", full.artist)
        assertEquals("My Beautiful Dark Twisted Fantasy", full.album)
        assertEquals(548_000L, full.durationMs)
        assertEquals("big.jpg", full.coverArtUrl)
        assertEquals(302L, full.deezerAlbumId)
        assertEquals("(Live)", full.versionNote)
        assertNull("a BPM of 0 is Deezer's 'unknown', not a tempo", full.bpm)
        assertNull("search results have no genre", full.genre)
        // Missing artist / album stay blank: never the placeholder text that would be written into tags.
        assertEquals("", bare.artist)
        assertEquals("", bare.album)
        assertNull(bare.coverArtUrl)
    }

    // ---- MusicBrainz

    private fun runawayFixture(): MusicBrainzSearchResponse =
        json.decodeFromString(
            requireNotNull(javaClass.classLoader?.getResource("musicbrainz_runaway_search.json")).readText(),
        )

    /**
     * Real search response (trimmed): a bootleg demo and a DJ-pool compilation come before the
     * studio recording, and a live bootleg follows. The studio recording on the official album
     * must come first, with that release's album, year and cover.
     */
    @Test
    fun musicBrainz_prefersTheOfficialStudioRelease() {
        val candidates = musicBrainzCandidates(runawayFixture(), "My Beautiful Dark Twisted Fantasy", "MusicBrainz")

        val first = candidates.first()
        assertEquals(548_200L, first.durationMs)
        assertEquals("My Beautiful Dark Twisted Fantasy", first.album)
        assertEquals(2010, first.year)
        assertEquals("5d6e21e1-deb5-428e-bb42-c2a567f3619b", first.releaseGroupId)
        assertEquals(coverArtArchiveUrl("5d6e21e1-deb5-428e-bb42-c2a567f3619b"), first.coverArtUrl)
        // MusicBrainz's credit for the studio recording names the featured artist.
        assertEquals("Kanye West feat. Pusha T", first.artist)
        assertTrue(candidates.any { it.versionNote.orEmpty().startsWith("live") })
        assertNull("MusicBrainz search has no BPM", first.bpm)
    }

    @Test
    fun bestRelease_matchesTheLocalAlbumBeforeAnythingElse() {
        val official = release("Album A", "Official", "Album")
        val compilation = release("Now Hits 80", "Official", "Album", secondary = listOf("Compilation"), date = "2009")
        assertEquals(compilation, bestRelease(listOf(official, compilation), "now hits 80"))
        assertEquals(official, bestRelease(listOf(compilation, official), "Unknown Album"))
        assertEquals(official, bestRelease(listOf(compilation, official), null))
    }

    @Test
    fun bestRelease_officialBeforeBootleg_albumBeforeSingle_thenEarliest() {
        val bootleg = release("X", "Bootleg", "Album", date = "2001")
        val single = release("X", "Official", "Single", date = "2001")
        val laterAlbum = release("X", "Official", "Album", date = "2005")
        val earlierAlbum = release("X", "Official", "Album", date = "2003")
        assertEquals(earlierAlbum, bestRelease(listOf(bootleg, single, laterAlbum, earlierAlbum), null))
        assertNull(bestRelease(emptyList(), "X"))
    }

    // ---- MusicBrainz release dates

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun musicBrainz_dateIsTheEarliestOfficialReleaseOfTheAlbum_withFullPrecision() {
        val candidates = musicBrainzCandidates(runawayFixture(), "My Beautiful Dark Twisted Fantasy", "MusicBrainz", today)
        val studio = candidates.single { it.durationMs == 548_200L }
        // Official MBDTF releases: 2010-11-22, 2010-11-19 and 2010-11 -> the original, as a full date.
        assertEquals("2010-11-19", studio.releaseDate)
        assertEquals(2010, studio.year)
        assertNull(studio.releaseDateIssue)
    }

    @Test
    fun musicBrainz_bootlegDemoLiveAndCompilationDates_areNeverUsed() {
        val candidates = musicBrainzCandidates(runawayFixture(), "My Beautiful Dark Twisted Fantasy", "MusicBrainz", today)
        val demo = candidates.single { it.durationMs == 551_000L }
        val live = candidates.single { it.durationMs == 371_040L }
        val djPool = candidates.single { it.durationMs == 270_093L }
        for (candidate in listOf(demo, live, djPool)) {
            assertNull("${candidate.album} must not give a date", candidate.releaseDate)
            assertEquals(0, candidate.year)
        }
        assertTrue(demo.releaseDateIssue.orEmpty().contains("unofficial releases (Bootleg)"))
        assertTrue(live.releaseDateIssue.orEmpty().contains("unofficial"))
        assertTrue(djPool.releaseDateIssue.orEmpty().contains("compilation"))
    }

    @Test
    fun musicBrainz_aCompilationDate_isUsedWhenItIsTheAlbumTheFileIsTaggedWith() {
        val candidates = musicBrainzCandidates(runawayFixture(), "CD POOL - HITS 26 - November 10", "MusicBrainz", today)
        assertEquals("2010-11-01", candidates.single { it.durationMs == 270_093L }.releaseDate)
    }

    private fun recordingJson(vararg dates: String?) =
        dates.joinToString(",", prefix = """{"recordings":[{"id":"r1","title":"Song","artist-credit":[{"name":"A"}],"releases":[""", postfix = "]}]}") {
            val date = it?.let { d -> ""","date":"$d"""" }.orEmpty()
            """{"id":"x$it","title":"Album","status":"Official"$date,"release-group":{"id":"rg1","primary-type":"Album"}}"""
        }

    private fun singleCandidate(json: String) =
        musicBrainzCandidates(this.json.decodeFromString<MusicBrainzSearchResponse>(json), "Album", "MusicBrainz", today).single()

    @Test
    fun musicBrainz_missingDates_giveNoDate() {
        val candidate = singleCandidate(recordingJson(null, ""))
        assertNull(candidate.releaseDate)
        assertEquals("MusicBrainz has no release date for \"Album\"", candidate.releaseDateIssue)
    }

    @Test
    fun musicBrainz_malformedOrFutureDates_areIgnored() {
        val onlyBad = singleCandidate(recordingJson("2010-13-45", "19/11/2010", "2031-01-01"))
        assertNull(onlyBad.releaseDate)
        assertTrue(onlyBad.releaseDateIssue.orEmpty().contains("isn't a valid date"))
        assertEquals("2004-02-10", singleCandidate(recordingJson("2004-02-10", "2004-99")).releaseDate)
    }

    @Test
    fun musicBrainz_malformedResponse_isAnError_notEmptyData() {
        try {
            json.decodeFromString<MusicBrainzSearchResponse>("""{"recordings":[{"id":"r1"}]}""")
            fail("a recording without a title must not parse")
        } catch (_: kotlinx.serialization.SerializationException) {
            // expected: the provider throws, the search reports FAILED
        }
    }

    private fun release(
        title: String,
        status: String,
        type: String,
        secondary: List<String> = emptyList(),
        date: String = "2010",
    ) = MusicBrainzReleaseDto(
        id = "$title-$status-$type-$date",
        title = title,
        date = date,
        status = status,
        releaseGroup = MusicBrainzReleaseGroupDto(id = "rg-$title-$date", primaryType = type, secondaryTypes = secondary),
    )
}
