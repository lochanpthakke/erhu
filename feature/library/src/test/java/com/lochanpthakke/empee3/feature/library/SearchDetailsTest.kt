package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.model.QueryAttempt
import com.lochanpthakke.empee3.core.model.RepairDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Search Details screen for "Mr. Lonely (feat. Frank Ocean & Steve Lacy).mp3", built from separate fields. */
class SearchDetailsTest {
    private val title = "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)"
    private val artist = "Tyler/ The Creator/Frank Ocean/ Steve Lacy"

    private fun outcomes(musicBrainz: ProviderStatus) =
        listOf(
            ProviderOutcome("MusicBrainz", status = musicBrainz, errorMessage = if (musicBrainz == ProviderStatus.NETWORK_ERROR) "dns" else null),
            ProviderOutcome("Deezer", status = ProviderStatus.UNAVAILABLE, errorMessage = "track search isn't available in this country"),
            ProviderOutcome("Last.fm", status = ProviderStatus.NOT_CONFIGURED, errorMessage = "No Last.fm API key configured"),
        )

    private val diag =
        RepairDiagnostics(
            originalFilename = "Mr. Lonely (feat. Frank Ocean & Steve Lacy).mp3",
            searchTitle = "911 / Mr. Lonely",
            searchArtist = artist,
            targetDurationMs = 255_000L,
            embeddedTitle = title,
            embeddedArtist = artist,
            embeddedAlbum = "Flower Boy",
            fileTitles = listOf("Mr. Lonely", "Mr. Lonely (feat. Frank Ocean & Steve Lacy)"),
            queries =
                listOf(
                    QueryAttempt("911 / Mr. Lonely", artist, null, "file tags", outcomes(ProviderStatus.SUCCESS)),
                    QueryAttempt("Mr. Lonely", null, "Flower Boy", "file name", outcomes(ProviderStatus.NO_RESULTS)),
                ),
        )

    @Test
    fun everyFieldIsItsOwnLine_andNothingIsCutOff() {
        val lines = searchDetailLines(diag)
        assertEquals(DetailLine("Title tag", title), lines.single { it.label == "Title tag" })
        assertEquals(artist, lines.single { it.label == "Artist tag" }.value)
        assertEquals("Flower Boy", lines.single { it.label == "Album tag" }.value)
        assertEquals("4:15", lines.single { it.label == "Length" }.value)
        assertEquals(
            listOf("Mr. Lonely", "Mr. Lonely (feat. Frank Ocean & Steve Lacy)"),
            lines.filter { it.label == "Title from file name" }.map { it.value },
        )
    }

    @Test
    fun eachQueryIsShownSeparately_withItsOwnParts_andEachProvidersAnswer() {
        val lines = searchDetailLines(diag)
        assertEquals(
            listOf("“911 / Mr. Lonely” (from file tags)", "“Mr. Lonely” (from file name)"),
            lines.filter { it.label.startsWith("Search ") }.map { it.value },
        )
        assertEquals(listOf(artist), lines.filter { it.label == "artist" }.map { it.value })
        assertEquals(listOf("Flower Boy"), lines.filter { it.label == "album" }.map { it.value })
        val providerLines = lines.filter { it.indent == 2 }.map { "${it.label}: ${it.value}" }
        assertTrue(providerLines.contains("MusicBrainz: ✓ Selected candidate"))
        assertTrue(providerLines.contains("MusicBrainz: No results found"))
        assertTrue(providerLines.contains("Deezer: Not available here: track search isn't available in this country"))
        assertTrue(providerLines.contains("Last.fm: Not configured: No Last.fm API key configured"))
    }

    @Test
    fun noValueCarriesAnotherLabel() {
        val labels = searchDetailLines(diag).map { it.label }.toSet() + setOf("Searched title", "Searched artist")
        for (line in searchDetailLines(diag)) {
            for (label in labels) assertFalse("'${line.value}' contains '$label:'", line.value.contains("$label:"))
        }
    }

    @Test
    fun candidates_showWhatTheyWouldWrite() {
        val studio = MetadataCandidate("1", "911 / Mr. Lonely", "Tyler, The Creator ft. Frank Ocean & Steve Lacy", "Flower Boy", durationMs = 255_000L)
        assertEquals("911 / Mr. Lonely — Tyler, The Creator ft. Frank Ocean & Steve Lacy · Flower Boy · 4:15", candidateLine(studio))
        assertTrue(candidateLine(studio.copy(versionNote = "Dolby Atmos mix")).endsWith("[Dolby Atmos mix]"))
    }

    @Test
    fun aCountryRestrictionOrMissingKey_isNotANetworkFailure() {
        // MusicBrainz answered "nothing", Deezer is withheld here, Last.fm has no key: a genuine no match.
        assertNull(networkVerdict(outcomes(ProviderStatus.NO_RESULTS)))
        // MusicBrainz couldn't be reached and the others can't help: the connection, not the song.
        assertTrue(networkVerdict(outcomes(ProviderStatus.NETWORK_ERROR)) is NetworkVerdict.Unreachable)
    }
}
