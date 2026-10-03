package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CreditTextTest {
    @Test
    fun `canonical key folds case, width, normal form and spacing`() {
        assertEquals("drake", CreditText.canonicalKey("  DRAKE "))
        assertEquals("drake", CreditText.canonicalKey("ＤＲＡＫＥ"))
        assertEquals(CreditText.canonicalKey("Beyoncé"), CreditText.canonicalKey("Beyoncé"))
        assertEquals("21 savage", CreditText.canonicalKey("21   Savage"))
    }

    @Test
    fun `canonical key ignores the device locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        try {
            assertEquals("lil wayne", CreditText.canonicalKey("LIL WAYNE"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `segments keep original offsets and never decide identity`() {
        val segments = CreditText.segmentsOf("Tyler/ The Creator")
        assertEquals(listOf("Tyler", "The Creator"), segments.map { it.text })
        assertEquals("Tyler/ The Creator".indexOf("The"), segments[1].start)
    }

    @Test
    fun `x and and are separators only when spaced`() {
        assertEquals(1, CreditText.segmentsOf("Lil Nas X").size)
        assertEquals(2, CreditText.segmentsOf("suicideboys x SHAKEWELL").size)
        assertEquals(2, CreditText.segmentsOf("Simon and Garfunkel").size)
        assertEquals(1, CreditText.segmentsOf("Anderson .Paak").size)
    }

    @Test
    fun `adjacent duplicate segments collapse`() {
        val keys = CreditText.segmentsOf("S.P. Balasubrahmanyam, T.K. Pugazhendi, T.K. Pugazhendi, R.N. Jayagopal").map { it.key }
        assertEquals(listOf("s.p. balasubrahmanyam", "t.k. pugazhendi", "r.n. jayagopal"), keys)
    }

    @Test
    fun `artist text feat marker produces a featured run`() {
        val runs = CreditText.runs("Drake feat. Future, Young Thug")
        assertEquals(listOf(false, true), runs.map { it.featured })
        assertEquals(listOf("Future", "Young Thug"), runs[1].segments.map { it.text })
        val bracketed = CreditText.runs("Drake (with Future)")
        assertEquals(listOf("Future"), bracketed[1].segments.map { it.text })
    }

    @Test
    fun `punctuation only text has no runs`() {
        assertTrue(CreditText.runs(" / ").isEmpty())
        assertTrue(CreditText.runs(" & ").isEmpty())
    }

    @Test
    fun `block key ignores separators without splitting`() {
        assertEquals(CreditText.blockKey("Tyler, The Creator"), CreditText.blockKey("Tyler/ The Creator"))
        assertEquals(CreditText.blockKey("Rex Orange County & Anna of the North"), CreditText.blockKey("Rex Orange County/Anna of the North"))
    }
}
