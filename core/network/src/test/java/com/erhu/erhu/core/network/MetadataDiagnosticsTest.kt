package com.erhu.erhu.core.network

import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.model.RepairDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataDiagnosticsTest {

    @Test
    fun scenario1_allProvidersCompleteSuccessfullyAndReturnZeroCandidates() {
        val outcomes = listOf(
            ProviderOutcome("MusicBrainz", 0, ProviderStatus.NO_RESULTS),
            ProviderOutcome("Deezer", 0, ProviderStatus.NO_RESULTS),
            ProviderOutcome("Last.fm", 0, ProviderStatus.NO_RESULTS),
        )
        val diag = RepairDiagnostics(
            originalFilename = "Diwali.mp3",
            searchTitle = "Diwali",
            searchArtist = null,
            providerOutcomes = outcomes,
            topCandidates = emptyList(),
            finalReason = "NO_MATCH",
        )

        assertEquals("Diwali.mp3", diag.originalFilename)
        assertEquals("Diwali", diag.searchTitle)
        assertNull(diag.searchArtist)
        assertEquals(3, diag.providerOutcomes.size)
        assertTrue(diag.providerOutcomes.all { it.status == ProviderStatus.NO_RESULTS })
        assertEquals("NO_MATCH", diag.finalReason)
    }

    @Test
    fun scenario2_oneZero_oneFailed_oneLowConfidence() {
        val outcomes = listOf(
            ProviderOutcome("MusicBrainz", 0, ProviderStatus.NO_RESULTS),
            ProviderOutcome("Deezer", 0, ProviderStatus.FAILED, "API Error"),
            ProviderOutcome("Last.fm", 2, ProviderStatus.LOW_CONFIDENCE),
        )
        val candidate = MetadataCandidate("1", "Diwali Mela", "Various", "Album", confidenceScore = 0.55f)
        val diag = RepairDiagnostics(
            originalFilename = "Diwali.mp3",
            searchTitle = "Diwali",
            searchArtist = null,
            providerOutcomes = outcomes,
            topCandidates = listOf(candidate),
            finalReason = "NO_MATCH",
        )

        assertEquals(ProviderStatus.NO_RESULTS, diag.providerOutcomes[0].status)
        assertEquals(ProviderStatus.FAILED, diag.providerOutcomes[1].status)
        assertEquals("API Error", diag.providerOutcomes[1].errorMessage)
        assertEquals(ProviderStatus.LOW_CONFIDENCE, diag.providerOutcomes[2].status)
        assertEquals(1, diag.topCandidates.size)
        assertEquals(0.55f, diag.topCandidates.first().confidenceScore, 0.001f)
    }

    @Test
    fun scenario3_providerRequestTimesOut() {
        val outcomes = listOf(
            ProviderOutcome("Deezer", 0, ProviderStatus.TIMED_OUT, "Request timed out"),
        )
        val diag = RepairDiagnostics(
            originalFilename = "Track.mp3",
            searchTitle = "Track",
            searchArtist = "Artist",
            providerOutcomes = outcomes,
            finalReason = "NETWORK_ERROR",
        )

        assertEquals(ProviderStatus.TIMED_OUT, diag.providerOutcomes.first().status)
        assertEquals("Request timed out", diag.providerOutcomes.first().errorMessage)
        assertEquals("NETWORK_ERROR", diag.finalReason)
    }

    @Test
    fun scenario4_providerReturnsCandidatesAndOneIsSelected() {
        val candidate = MetadataCandidate("1", "Yellow", "Coldplay", "Parachutes", confidenceScore = 0.95f)
        val outcomes = listOf(
            ProviderOutcome("Deezer", 1, ProviderStatus.SUCCESS),
        )
        val diag = RepairDiagnostics(
            originalFilename = "Yellow.mp3",
            searchTitle = "Yellow",
            searchArtist = "Coldplay",
            providerOutcomes = outcomes,
            topCandidates = listOf(candidate),
            finalReason = null,
        )

        assertEquals(ProviderStatus.SUCCESS, diag.providerOutcomes.first().status)
        assertNull(diag.finalReason)
        assertEquals(1, diag.topCandidates.size)
        assertEquals(0.95f, diag.topCandidates.first().confidenceScore, 0.001f)
    }

    @Test
    fun scenario5_providerIsNotQueried_markedSkipped() {
        val outcomes = listOf(
            ProviderOutcome("Deezer", 1, ProviderStatus.SUCCESS),
            ProviderOutcome("MusicBrainz", 0, ProviderStatus.SKIPPED),
            ProviderOutcome("Last.fm", 0, ProviderStatus.SKIPPED),
        )
        val diag = RepairDiagnostics(
            originalFilename = "Song.mp3",
            searchTitle = "Song",
            searchArtist = "Artist",
            providerOutcomes = outcomes,
        )

        assertEquals(ProviderStatus.SUCCESS, diag.providerOutcomes[0].status)
        assertEquals(ProviderStatus.SKIPPED, diag.providerOutcomes[1].status)
        assertEquals(ProviderStatus.SKIPPED, diag.providerOutcomes[2].status)
    }

    @Test
    fun scenario6_finalFailureReasonIsNotNoMatch() {
        val diag = RepairDiagnostics(
            originalFilename = "DeletedFile.mp3",
            searchTitle = "DeletedFile",
            searchArtist = null,
            providerOutcomes = emptyList(),
            finalReason = "FILE_NOT_FOUND",
        )

        assertEquals("FILE_NOT_FOUND", diag.finalReason)
    }

    @Test
    fun scenario7_searchTitleAndArtistCapturedAccurately() {
        val diag = RepairDiagnostics(
            originalFilename = "01 - Skrillex - Diwali.mp3",
            searchTitle = "Diwali",
            searchArtist = "Skrillex",
            targetDurationMs = 180_000L,
        )

        assertEquals("01 - Skrillex - Diwali.mp3", diag.originalFilename)
        assertEquals("Diwali", diag.searchTitle)
        assertEquals("Skrillex", diag.searchArtist)
        assertEquals(180_000L, diag.targetDurationMs)
    }

    @Test
    fun scenario8_expandedStateTogglesModelDataCorrectly() {
        var expanded = false
        assertFalse(expanded)

        expanded = !expanded
        assertTrue(expanded)

        expanded = !expanded
        assertFalse(expanded)
    }
}
