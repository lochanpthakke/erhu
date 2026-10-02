package com.lochanpthakke.empee3.core.database.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistParserTest {
    @Test
    fun parse_singleArtist_returnsPrimaryArtist() {
        val result = ArtistParser.parse("Drake")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Drake", result.primaryArtists[0].name)
        assertTrue(result.featuredArtists.isEmpty())
        assertEquals("Drake", result.displayArtist)
    }

    @Test
    fun parse_featuredArtist_splitsPrimaryAndFeatured() {
        val result = ArtistParser.parse("Drake feat. Playboi Carti")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Drake", result.primaryArtists[0].name)
        assertEquals(1, result.featuredArtists.size)
        assertEquals("Playboi Carti", result.featuredArtists[0].name)
        assertEquals("Drake feat. Playboi Carti", result.displayArtist)
    }

    @Test
    fun parse_tylerTheCreator_remainsSingleArtist() {
        val result = ArtistParser.parse("Tyler, The Creator")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Tyler, The Creator", result.primaryArtists[0].name)
        assertTrue(result.featuredArtists.isEmpty())
        assertEquals("Tyler, The Creator", result.displayArtist)
    }

    @Test
    fun parse_earthWindAndFire_remainsSingleArtist() {
        val result = ArtistParser.parse("Earth, Wind & Fire")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Earth, Wind & Fire", result.primaryArtists[0].name)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun parse_acdc_remainsSingleArtist() {
        val result = ArtistParser.parse("AC/DC")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("AC/DC", result.primaryArtists[0].name)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun parse_ftAbbreviation_splitsPrimaryAndFeatured() {
        val result = ArtistParser.parse("Drake ft. Future")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Drake", result.primaryArtists[0].name)
        assertEquals(1, result.featuredArtists.size)
        assertEquals("Future", result.featuredArtists[0].name)
    }

    @Test
    fun parse_multipleFeaturedArtists_splitsAllFeatured() {
        val result = ArtistParser.parse("Drake feat. Playboi Carti & Future")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Drake", result.primaryArtists[0].name)
        assertEquals(2, result.featuredArtists.size)
        assertEquals("Playboi Carti", result.featuredArtists[0].name)
        assertEquals("Future", result.featuredArtists[1].name)
    }

    @Test
    fun parse_ampersand_splitsPrimaryArtists() {
        val result = ArtistParser.parse("Drake & 21 Savage")
        assertEquals(2, result.primaryArtists.size)
        assertEquals("Drake", result.primaryArtists[0].name)
        assertEquals("21 Savage", result.primaryArtists[1].name)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun parse_unknownArtist_returnsUnknownArtist() {
        val result = ArtistParser.parse("Unknown Artist")
        assertEquals(1, result.primaryArtists.size)
        assertEquals("Unknown Artist", result.primaryArtists[0].name)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun generateArtistId_isDeterministic() {
        val id1 = ArtistParser.generateArtistId("drake")
        val id2 = ArtistParser.generateArtistId("drake")
        val idCase = ArtistParser.generateArtistId("DRAKE".lowercase())
        assertEquals(id1, id2)
        assertEquals(id1, idCase)
    }

    @Test
    fun generateAlbumId_sameTitleAndArtist_generatesSameId() {
        val drakeId = ArtistParser.generateArtistId("drake")
        val albumId1 = ArtistParser.generateAlbumId("Dark Lane Demo Tapes", drakeId)
        val albumId2 = ArtistParser.generateAlbumId("  DARK LANE DEMO TAPES  ", drakeId)
        assertEquals(albumId1, albumId2)
    }

    @Test
    fun generateAlbumId_sameTitleDifferentArtist_generatesDifferentId() {
        val drakeId = ArtistParser.generateArtistId("drake")
        val taylorId = ArtistParser.generateArtistId("taylor swift")
        val albumId1 = ArtistParser.generateAlbumId("Greatest Hits", drakeId)
        val albumId2 = ArtistParser.generateAlbumId("Greatest Hits", taylorId)
        assertNotEquals(albumId1, albumId2)
    }
}
