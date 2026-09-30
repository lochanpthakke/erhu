package com.lochanpthakke.empee3.core.network

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataScoringTest {
    private val scorer = MetadataScorer()
    private val cleaner = FilenameCleaner()

    private fun candidate(
        title: String,
        artist: String,
    ) = MetadataCandidate(id = "1", title = title, artist = artist, album = "Album")

    @Test
    fun exactMatch_isHighConfidence() {
        val scored = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", "Coldplay")
        assertEquals(1.0f, scored.confidenceScore, 0.0001f)
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        val scored = scorer.scoreCandidate(candidate("  YELLOW ", "coldplay"), "yellow", "Coldplay ")
        assertTrue(scored.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun unrelatedTrack_isBelowUncertainThreshold() {
        val scored = scorer.scoreCandidate(candidate("Bohemian Rhapsody", "Queen"), "Yellow", "Coldplay")
        assertTrue("score was ${scored.confidenceScore}", scored.confidenceScore < UNCERTAIN_THRESHOLD)
    }

    @Test
    fun largeDurationMismatch_lowersConfidence() {
        val base = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", "Coldplay", 266_000, 266_000)
        val off = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", "Coldplay", 266_000, 400_000)
        assertTrue(off.confidenceScore < base.confidenceScore)
        assertTrue(off.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun missingArtist_fallsBackToTitleSimilarity() {
        val scored = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", null)
        assertEquals(1.0f, scored.confidenceScore, 0.0001f)
    }

    @Test
    fun emptyTitle_scoresZeroForTitleWithoutCrashing() {
        val scored = scorer.scoreCandidate(candidate("", ""), "Yellow", "Coldplay")
        assertEquals(0.0f, scored.confidenceScore, 0.0001f)
    }

    @Test
    fun cleaner_stripsTrackNumberWatermarkAndSplitsArtist() {
        val cleaned = cleaner.cleanFilename("03 - Coldplay - Yellow [320kbps] pagalworld.mp3")
        assertEquals("Yellow", cleaned.title)
        assertEquals("Coldplay", cleaned.artistGuess)
    }

    @Test
    fun cleaner_withoutArtist_returnsTitleOnly() {
        val cleaned = cleaner.cleanFilename("My_Song.flac")
        assertEquals("My Song", cleaned.title)
        assertNull(cleaned.artistGuess)
    }
}
