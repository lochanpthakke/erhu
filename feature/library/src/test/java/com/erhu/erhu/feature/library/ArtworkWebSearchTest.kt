package com.erhu.erhu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ArtworkWebSearchTest {
    @Test
    fun albumSearchIncludesAlbumAndArtistInGoogleImages() {
        assertSearch(albumArtworkSearchUrl("SOS", "SZA"), "SOS SZA album cover image")
    }

    @Test
    fun artistSearchMatchesTheIosPhotoQuery() {
        assertSearch(artistArtworkSearchUrl("Akhil Sachdeva"), "Akhil Sachdeva artist photo")
    }

    @Test
    fun punctuationUnicodeAndQueryCharactersStayInsideTheSearchTerm() {
        assertSearch(albumArtworkSearchUrl("A&B + #1?", "AC/DC"), "A&B + #1? AC/DC album cover image")
        assertSearch(artistArtworkSearchUrl("Björk & 坂本龍一"), "Björk & 坂本龍一 artist photo")
    }

    @Test
    fun missingMetadataDoesNotOpenAnEmptyOrMalformedSearch() {
        assertNull(albumArtworkSearchUrl(" ", ""))
        assertNull(artistArtworkSearchUrl(" \n "))
        assertSearch(albumArtworkSearchUrl(" Album ", ""), "Album album cover image")
        assertSearch(albumArtworkSearchUrl("", " Artist "), "Artist album cover image")
        assertSearch(artistArtworkSearchUrl(" Artist "), "Artist artist photo")
    }

    private fun assertSearch(value: String?, expected: String) {
        val uri = URI(requireNotNull(value))
        assertEquals("https", uri.scheme)
        assertEquals("www.google.com", uri.host)
        assertEquals("/search", uri.path)
        assertNull(uri.fragment)
        val parameters = uri.rawQuery.split('&').associate {
            val (name, encoded) = it.split('=', limit = 2)
            name to URLDecoder.decode(encoded, "UTF-8")
        }
        assertEquals(mapOf("q" to expected, "tbm" to "isch"), parameters)
    }
}
