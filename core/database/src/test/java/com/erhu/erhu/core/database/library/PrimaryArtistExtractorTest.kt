package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryArtistExtractorTest {

    @Test
    fun `extractPrimaryArtist handles feat correctly`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist("The Weeknd feat. Daft Punk")
        assertEquals("The Weeknd", result)
    }

    @Test
    fun `extractPrimaryArtist handles ampersand correctly`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist("Drake & Future")
        assertEquals("Drake", result)
    }

    @Test
    fun `extractPrimaryArtist handles unknown artist`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist("Unknown Artist")
        assertEquals(null, result)
    }

    @Test
    fun `extractPrimaryArtist handles null`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist(null)
        assertEquals(null, result)
    }

    @Test
    fun `extractPrimaryArtist handles blank`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist("   ")
        assertEquals(null, result)
    }

    @Test
    fun `extractPrimaryArtist handles simple artist`() {
        val result = PrimaryArtistExtractor.extractPrimaryArtist("Michael Jackson")
        assertEquals("Michael Jackson", result)
    }
}
