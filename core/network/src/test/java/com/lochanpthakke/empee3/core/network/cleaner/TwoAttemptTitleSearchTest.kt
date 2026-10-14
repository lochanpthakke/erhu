package com.lochanpthakke.empee3.core.network.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoAttemptTitleSearchTest {
    private val filenameCleaner = FilenameCleaner()
    private val generator = TitleCandidateGenerator(filenameCleaner)

    @Test
    fun caseA_chaleyaRadioEdit_generatesCompleteTitleFirstAndChaleyaAsFallback() {
        val candidates = generator.generateCandidates("Chaleya - Radio Edit.mp3")
        assertEquals("Chaleya - Radio Edit", candidates[0])
        assertTrue(candidates.contains("Chaleya"))
    }

    @Test
    fun caseB_chaleyaWatermark_generatesCompleteWatermarkTitleFirstAndChaleyaAsFallback() {
        val candidates = generator.generateCandidates("Chaleya - (SongsPk.Com.Se).mp3")
        assertEquals("Chaleya - (SongsPk.Com.Se)", candidates[0])
        assertTrue(candidates.contains("Chaleya"))
    }

    @Test
    fun caseB_chaleyaBracketWatermark_generatesChaleyaFallback() {
        val candidates = generator.generateCandidates("Chaleya [128 Kbps]-(SongsPk.Com.Se).mp3")
        assertEquals("Chaleya [128 Kbps]-(SongsPk.Com.Se)", candidates[0])
        assertTrue(candidates.contains("Chaleya"))
    }

    @Test
    fun caseC_diwaliWatermark_generatesCompleteWatermarkTitleFirstAndDiwaliAsFallback() {
        val candidates = generator.generateCandidates("Diwali - UnknownDJWatermark.mp3")
        assertEquals("Diwali - UnknownDJWatermark", candidates[0])
        assertTrue(candidates.contains("Diwali"))
    }

    @Test
    fun caseD_aPunk_generatesOnlyCompleteTitleAndDoesNotShortenToA() {
        val candidates = generator.generateCandidates("A-Punk.mp3")
        assertEquals(1, candidates.size)
        assertEquals("A-Punk", candidates[0])
    }

    @Test
    fun meltdownFeat_generatesCoreTitleFallback() {
        val candidates = generator.generateCandidates("MELTDOWN (feat. Travis Scott).mp3")
        assertEquals("MELTDOWN (feat. Travis Scott)", candidates[0])
        assertTrue(candidates.contains("MELTDOWN"))
    }

    @Test
    fun theHillsRemix_generatesCoreTitleFallback() {
        val candidates = generator.generateCandidates("The Hills (Remix).mp3")
        assertEquals("The Hills (Remix)", candidates[0])
        assertTrue(candidates.contains("The Hills"))
    }
}
