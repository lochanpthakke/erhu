package com.lochanpthakke.empee3.core.network.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAttemptCandidateSelectionTest {
    private val filenameCleaner = FilenameCleaner()
    private val generator = TitleCandidateGenerator(filenameCleaner)

    @Test
    fun mrBrightside_preservesPeriodWithoutExtensionTruncation() {
        val candidates = generator.generateCandidates("Mr. Brightside.mp3")
        assertEquals("Mr. Brightside", candidates[0])
    }

    @Test
    fun allTheSignsFeat_preservesFeaturedArtistClause() {
        val candidates = generator.generateCandidates("All The Signs (feat. Teezo Touchdown).mp3")
        assertEquals("All The Signs (feat. Teezo Touchdown)", candidates[0])
    }

    @Test
    fun aPunk_preservesHyphenatedWord() {
        val candidates = generator.generateCandidates("A-Punk.mp3")
        assertEquals("A-Punk", candidates[0])
    }

    @Test
    fun spiderManRadioEdit_preservesRadioEditAnnotation() {
        val candidates = generator.generateCandidates("Spider-Man - Radio Edit - UnknownWatermark.mp3")
        assertTrue(candidates.contains("Spider-Man - Radio Edit - UnknownWatermark"))
    }

    @Test
    fun chaleyaWatermark_stripsBitrateAndDomainWatermarks() {
        val candidates = generator.generateCandidates("Chaleya [128 Kbps]-(SongsPk.Com.Se).mp3")
        assertTrue(candidates.contains("Chaleya"))
    }
}
