package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.model.Genres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored genre list format and BPM tag parsing. */
class GenresAndBpmTagTest {
    @Test
    fun genres_areSplitNormalisedAndJoined() {
        assertEquals(listOf("Rock"), Genres.parse("Rock"))
        assertEquals(listOf("Electronic", "Synth-pop", "Alternative"), Genres.parse("Electronic; Synth-pop;Alternative"))
        assertEquals(listOf("Electronic", "Synth pop"), Genres.parse("  Electronic ;  Synth   pop ; electronic ;"))
        // An ID3v2.4 multi-value frame read raw (NUL-separated).
        assertEquals(listOf("Rock", "Pop"), Genres.parse("Rock\u0000Pop"))
        assertEquals("Electronic; Synth-pop", Genres.join(listOf(" Electronic", "Synth-pop", "SYNTH-POP", "")))
        assertTrue(Genres.same("Rock;Pop", "rock; pop"))
        assertFalse(Genres.same("Rock; Pop", "Pop; Rock"))
    }

    @Test
    fun genreNamesWithSlashesOrAmpersands_stayWhole() {
        assertEquals(listOf("Hip Hop/Rap", "R&B"), Genres.parse("Hip Hop/Rap; R&B"))
        assertEquals(listOf("Drum & Bass"), Genres.parse("Drum & Bass"))
    }

    @Test
    fun missingOrJunkGenres_areDropped() {
        assertEquals(emptyList<String>(), Genres.parse(null))
        assertEquals(emptyList<String>(), Genres.parse(" ; ;"))
        assertEquals(emptyList<String>(), Genres.parse("Unknown; <unknown>; 12; (17)"))
        assertEquals(emptyList<String>(), Genres.parse("x".repeat(65)))
        assertNull(Genres.join(listOf("", null, "unknown genre")))
    }

    @Test
    fun bpmTags_areParsedAndValidated() {
        assertEquals(128, parseBpmTag("128"))
        assertEquals(128, parseBpmTag("127.6"))
        assertEquals(128, parseBpmTag("127,6"))
        assertEquals(93, parseBpmTag(" 93 BPM"))
        assertNull(parseBpmTag("fast"))
        assertNull(parseBpmTag(""))
        assertEquals(0, parseBpmTag("0"))
        assertNull("0 is no tempo", usableBpm("0"))
        assertNull("a year is not a tempo", usableBpm("2010"))
        assertEquals(40, usableBpm("40"))
        assertEquals(300, usableBpm("300"))
    }
}
