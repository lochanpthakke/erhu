package com.lochanpthakke.empee3.core.network.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnclosedEnclosureTest {
    private val filenameCleaner = FilenameCleaner()
    private val generator = TitleCandidateGenerator(filenameCleaner)

    @Test
    fun unclosedParenthesis_autoCompletesClosingParenthesis() {
        val candidates = generator.generateCandidates("BANK TELLER (with Lil Yachty.mp3")
        assertEquals("BANK TELLER (with Lil Yachty)", candidates[0])
        assertTrue(candidates.contains("BANK TELLER"))
    }

    @Test
    fun unclosedBracket_autoCompletesClosingBracket() {
        val candidates = generator.generateCandidates("Chaleya [128 Kbps.mp3")
        assertEquals("Chaleya [128 Kbps]", candidates[0])
    }
}
