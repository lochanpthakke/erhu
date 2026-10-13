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
        album: String = "Album",
    ) = MetadataCandidate(id = "1", title = title, artist = artist, album = album)

    @Test
    fun exactMatch_titleArtistAlbum_isHighConfidence() {
        val scored = scorer.scoreCandidate(
            candidate("Diwali", "Skrillex", "Soma"),
            targetTitle = "Diwali",
            targetArtist = "Skrillex",
            targetAlbum = "Soma",
        )
        assertEquals(1.0f, scored.confidenceScore, 0.0001f)
    }

    @Test
    fun diwaliVsDiwaliRiddim_targetAlbumMatchesIntendedRecording_andRejectsWrongCandidate() {
        // Target file has title "Diwali", no artist, but album "Soma"
        val wrongCandidate = scorer.scoreCandidate(
            candidate("Diwali Riddim", "Lenky", "Invasion Of The Mysteron Killer Sounds Vol. 1"),
            targetTitle = "Diwali",
            targetArtist = null,
            targetAlbum = "Soma",
        )
        val intendedCandidate = scorer.scoreCandidate(
            candidate("Diwali", "Skrillex", "Soma"),
            targetTitle = "Diwali",
            targetArtist = null,
            targetAlbum = "Soma",
        )

        // Wrong candidate "Diwali Riddim" on wrong album is rejected from auto-write (< 0.85)
        assertTrue("Wrong candidate score was ${wrongCandidate.confidenceScore}", wrongCandidate.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
        // Intended candidate on correct album "Soma" is accepted (1.0 >= 0.85)
        assertTrue("Intended candidate score was ${intendedCandidate.confidenceScore}", intendedCandidate.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD)
        assertTrue(intendedCandidate.confidenceScore > wrongCandidate.confidenceScore)
    }

    @Test
    fun shortTitle_competingAgainstLongerTitle_scoresLower() {
        val exact = scorer.scoreCandidate(candidate("Diwali", "Skrillex"), "Diwali", "Skrillex")
        val longer = scorer.scoreCandidate(candidate("Diwali Riddim", "Skrillex"), "Diwali", "Skrillex")
        assertTrue(exact.confidenceScore > longer.confidenceScore)
    }

    @Test
    fun missingTargetArtist_doesNotTreatTitleAsArtistEvidence() {
        val exactAlbum = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Parachutes"), "Yellow", null, "Parachutes")
        val diffAlbum = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Album"), "Yellow", null, "Parachutes")
        assertEquals(1.0f, exactAlbum.confidenceScore, 0.0001f)
        assertTrue(diffAlbum.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD)
        assertTrue(exactAlbum.confidenceScore > diffAlbum.confidenceScore)
    }

    @Test
    fun missingTargetAlbum_usesTitleAndArtistOnly() {
        val scored = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Parachutes"), "Yellow", "Coldplay", null)
        // Title (1.0) + Artist (1.0) normalized = 1.0
        assertEquals(1.0f, scored.confidenceScore, 0.0001f)
    }

    @Test
    fun candidateWithNoAlbumFromProvider_scoresSafely() {
        val scored = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Unknown Album"), "Yellow", "Coldplay", "Parachutes")
        assertTrue(scored.confidenceScore >= UNCERTAIN_THRESHOLD)
    }

    @Test
    fun candidateOnCompilationOrAlternateRelease_maintainsAcceptableScore() {
        val original = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Parachutes"), "Yellow", "Coldplay", "Parachutes")
        val compilation = scorer.scoreCandidate(candidate("Yellow", "Coldplay", "Greatest Hits"), "Yellow", "Coldplay", "Parachutes")
        assertTrue(compilation.confidenceScore >= UNCERTAIN_THRESHOLD)
        assertTrue(original.confidenceScore > compilation.confidenceScore)
    }

    @Test
    fun incompleteMetadata_bothArtistAndAlbumMissing_capsNonExactTitleBelowHighConfidence() {
        val nonExact = scorer.scoreCandidate(candidate("Diwali Riddim", "Lenky"), "Diwali", null, null)
        assertTrue("Non exact score was ${nonExact.confidenceScore}", nonExact.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun ambiguousCandidate_withoutArtistOrAlbum_isNotAcceptedAutomatically() {
        val scored = scorer.scoreCandidate(candidate("Diwali Riddim", "Lenky", "Invasion"), "Diwali", null, null)
        assertTrue("Score was ${scored.confidenceScore}", scored.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
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
        val base = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", "Coldplay", targetDurationMs = 266_000, candidateDurationMs = 266_000)
        val off = scorer.scoreCandidate(candidate("Yellow", "Coldplay"), "Yellow", "Coldplay", targetDurationMs = 266_000, candidateDurationMs = 400_000)
        assertTrue(off.confidenceScore < base.confidenceScore)
        assertTrue(off.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
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
