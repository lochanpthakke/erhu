package com.lochanpthakke.empee3.core.network.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCandidateGeneratorTest {
    private val filenameCleaner = FilenameCleaner()
    private val generator = TitleCandidateGenerator(filenameCleaner)

    @Test
    fun hyphenatedWatermark_generatesPrimaryAndFallbackCandidate() {
        val candidates = generator.generateCandidates("Diwali - UnknownDJWatermark.mp3")
        assertTrue("Candidates: $candidates", candidates.contains("Diwali"))
    }

    @Test
    fun bracketedWatermark_generatesPrimaryAndFallbackCandidate() {
        val candidates = generator.generateCandidates("Diwali [Promo by DJ Rahul].mp3")
        assertTrue(candidates.contains("Diwali"))
    }

    @Test
    fun spaceSeparatedWatermark_generatesPrimaryAndFallbackCandidate() {
        val candidates = generator.generateCandidates("Diwali SomeCompletelyUnknownWatermark.mp3")
        assertEquals("Diwali SomeCompletelyUnknownWatermark", candidates[0])
        assertTrue(candidates.contains("Diwali"))
    }

    @Test
    fun legitimateTitleWithRadioEdit_generatesCandidates() {
        val candidates = generator.generateCandidates("Love Me Again - Radio Edit.mp3")
        assertEquals("Love Me Again", candidates[0])
        assertTrue(candidates.contains("Love Me Again - Radio Edit"))
    }

    @Test
    fun legitimateHyphenatedSingleWord_generatesCandidate() {
        val candidates = generator.generateCandidates("A-Punk.mp3")
        assertEquals("A-Punk", candidates[0])
    }

    @Test
    fun candidatesAreDeduplicatedAndLimitedToMaximumThree() {
        val candidates = generator.generateCandidates("Diwali [Promo] - UnknownSource.mp3")
        assertTrue(candidates.size <= 3)
        assertEquals(candidates.distinct().size, candidates.size)
    }
}
