package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryArtistExtractorTest {

    @Test
    fun commaSeparatedArtists_extractsFirstArtist() {
        val primary = PrimaryArtistExtractor.extractPrimaryArtist("ISOKNOCK, ISOxo, Knock2")
        assertEquals("ISOKNOCK", primary)
    }

    @Test
    fun slashSeparatedArtists_extractsFirstArtist() {
        val primary = PrimaryArtistExtractor.extractPrimaryArtist("ISOKNOCK / ISOxo / Knock2")
        assertEquals("ISOKNOCK", primary)
    }

    @Test
    fun featuredArtist_extractsPrimaryArtist() {
        val primary = PrimaryArtistExtractor.extractPrimaryArtist("Drake feat. Future")
        assertEquals("Drake", primary)
    }

    @Test
    fun singleArtist_returnsSameName() {
        val primary = PrimaryArtistExtractor.extractPrimaryArtist("Coldplay")
        assertEquals("Coldplay", primary)
    }

    @Test
    fun nullOrUnknownArtist_returnsNull() {
        assertNull(PrimaryArtistExtractor.extractPrimaryArtist(null))
        assertNull(PrimaryArtistExtractor.extractPrimaryArtist("Unknown Artist"))
        assertNull(PrimaryArtistExtractor.extractPrimaryArtist("  "))
    }
}
