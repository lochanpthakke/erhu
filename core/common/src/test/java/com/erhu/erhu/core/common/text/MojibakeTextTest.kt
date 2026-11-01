package com.erhu.erhu.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MojibakeTextTest {
    /** Exactly what MediaStore stored on the S23 (title bytes C3 A2 E2 82 AC E2 84 A2 for "’"). */
    private val misread = "IGORâ€™S THEME"
    private val correct = "IGOR’S THEME"

    @Test
    fun theAndroidMisreading_isReproducedExactly() {
        assertEquals(misread, MojibakeText.misreadAsWindows1252(correct))
        assertEquals("plain ASCII is never affected", "EARFQUAKE", MojibakeText.misreadAsWindows1252("EARFQUAKE"))
    }

    @Test
    fun igorsTheme_isRepaired() {
        assertTrue(MojibakeText.looksMisread(misread))
        assertEquals(correct, MojibakeText.repair(misread))
    }

    @Test
    fun correctUnicode_isLeftAlone() {
        val legitimate =
            listOf(
                correct,
                "WHAT’S GOOD",
                "São Paulo",
                "Ångström",
                "Björk",
                "Sigur Rós",
                "Beyoncé",
                "Ãrbol",
                "Crème brûlée",
                "東京",
                "Жизнь",
                "مرحبا",
                "🎵 night drive 🌙",
                // Has the byte pattern, but is not a misreading of valid UTF-8.
                "Price: 5â€",
            )
        for (text in legitimate) {
            assertNull("must not 'repair' $text", MojibakeText.repair(text))
            // Even when a pattern makes the scanner consult the tag, a tag that doesn't prove the misreading changes nothing.
            assertEquals(text, MojibakeText.correctedFromTag(text) { "unrelated" })
        }
        assertFalse(MojibakeText.looksMisread("São Paulo"))
        assertFalse("clean text never costs a tag read", MojibakeText.looksMisread("東京 🎵"))
    }

    @Test
    fun accentedNonLatinAndEmoji_roundTripThroughOneMisreading() {
        for (text in listOf("Beyoncé — Déjà Vu", "東京 🎵", "Ðрай", "Motörhead™")) {
            assertEquals(text, MojibakeText.repair(MojibakeText.misreadAsWindows1252(text)))
        }
    }

    @Test
    fun repairIsIdempotent_andRemovesOneLayerAtATime() {
        val repaired = MojibakeText.repair(misread)!!
        assertNull("repairing repaired text changes nothing", MojibakeText.repair(repaired))
        val twice = MojibakeText.misreadAsWindows1252(misread)
        assertEquals(misread, MojibakeText.repair(twice))
        assertEquals(correct, MojibakeText.repair(MojibakeText.repair(twice)))
    }

    @Test
    fun theIndexedValueIsReplacedOnlyWhenTheFileTagProvesTheMisreading() {
        assertEquals(correct, MojibakeText.correctedFromTag(misread) { correct })
        assertEquals("unreadable tag: keep", misread, MojibakeText.correctedFromTag(misread) { null })
        assertEquals("blank tag: keep", misread, MojibakeText.correctedFromTag(misread) { " " })
        assertEquals("a different tag is not proof", misread, MojibakeText.correctedFromTag(misread) { "Something Else" })
        assertEquals("tag holds the same broken text: keep", misread, MojibakeText.correctedFromTag(misread) { misread })
    }
}
