package com.lochanpthakke.empee3.core.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun leetspeak_and_symbols_match() {
        assertTrue(SearchNormalizer.matches("\$uicideboy\$", "suicideboys"))
        assertTrue(SearchNormalizer.matches("\$uicideboy\$", "suicide boys"))
        assertTrue(SearchNormalizer.matches("Ke\$ha", "kesha"))
        assertTrue(SearchNormalizer.matches("A\$AP Rocky", "asap rocky"))
    }

    @Test
    fun diacritics_and_accents_match() {
        assertTrue(SearchNormalizer.matches("Beyoncé", "beyonce"))
        assertTrue(SearchNormalizer.matches("Motörhead", "motorhead"))
    }

    @Test
    fun punctuation_and_spacing_match() {
        assertTrue(SearchNormalizer.matches("AC/DC", "acdc"))
        assertTrue(SearchNormalizer.matches("Panic! At The Disco", "panic at the disco"))
    }

    @Test
    fun non_matching_strings_return_false() {
        assertFalse(SearchNormalizer.matches("Kanye West", "Kendrick Lamar"))
    }
}
