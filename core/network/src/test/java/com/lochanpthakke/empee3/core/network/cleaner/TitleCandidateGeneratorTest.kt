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
        assertTrue(candidates.size >= 2)
        assertEquals("Diwali - UnknownDJWatermark", candidates[0])
        assertTrue(candidates.contains("Diwali"))
    }

    @Test
    fun legitimateTitleWithRadioEdit_generatesCandidates() {
        val candidates = generator.generateCandidates("Love Me Again - Radio Edit.mp3")
        assertEquals("Love Me Again - Radio Edit", candidates[0])
        assertTrue(candidates.contains("Love Me Again"))
    }

    @Test
    fun legitimateHyphenatedSingleWord_generatesCandidate() {
        val candidates = generator.generateCandidates("A-Punk.mp3")
        assertEquals(1, candidates.size)
        assertEquals("A-Punk", candidates[0])
    }

    @Test
    fun candidatesAreDeduplicatedAndLimitedToMaximumThree() {
        val candidates = generator.generateCandidates("Diwali - UnknownSource.mp3")
        assertTrue(candidates.size <= 3)
        assertEquals(candidates.distinct().size, candidates.size)
    }
}
