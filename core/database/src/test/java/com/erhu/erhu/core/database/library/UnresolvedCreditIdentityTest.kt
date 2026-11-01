package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Artist credit text is not artist identity: an unresolved credit never becomes an
 * artist row. Only resolved credits and explicitly approved aliases do.
 */
class UnresolvedCreditIdentityTest {
    private val model by lazy { LibraryModelBuilder.build(LibraryFixture.tracks, emptyMap()) }

    private fun songId(title: String) = LibraryFixture.songId(title)

    private fun artistNamed(name: String) = model.artists.filter { it.name == name }

    private fun albumTitled(title: String) = model.albums.single { it.title == title }

    // A, B
    @Test
    fun `unresolved Kanye collaborations create no artist`() {
        for (name in listOf("Kanye West/JAY-Z", "Kanye West/Adam Levine", "Kanye West/Bon Iver", "Kanye West/Brandy", "Kanye West/Kid Cudi/Raekwon")) {
            assertTrue(name, artistNamed(name).isEmpty())
        }
    }

    // C, D
    @Test
    fun `unresolved featured credits create no artist`() {
        val tracks =
            listOf(
                RawTrack(1, "911 / Mr. Lonely", "Tyler/ The Creator/Frank Ocean/Steve Lacy", null, "Flower Boy", 1, 1, "/fb", false),
                RawTrack(2, "Song", "Drake feat. Future, Molly Santana", null, "X", 1, 1, "/x", false),
                RawTrack(3, "Solo", "Drake", null, "X", 2, 1, "/x", false),
            )
        val m = LibraryModelBuilder.build(tracks, emptyMap())
        for (key in listOf("frank ocean/steve lacy", "future, molly santana")) {
            assertTrue(key, m.artists.none { it.key == key })
        }
        assertTrue(m.credits.none { it.creditText == "Future, Molly Santana" })
    }

    // E
    @Test
    fun `the approved alias makes Tyler one real artist`() {
        val tyler = model.artists.single { it.key == "tyler, the creator" }
        assertEquals("Tyler, The Creator", tyler.name)
        assertEquals(IdentityHash.of("tyler, the creator"), tyler.id)
        assertTrue(model.artists.none { it.key in setOf("tyler/ the creator", "tyler", "the creator") })
        assertTrue(model.credits.filter { it.songId == songId("Glitter") }.all { it.artistId == tyler.id })
    }

    // F, G
    @Test
    fun `Flower Boy and IGOR are linked to Tyler, The Creator`() {
        val tyler = IdentityHash.of("tyler, the creator")
        for (title in listOf("Flower Boy", "IGOR")) {
            val album = albumTitled(title)
            assertTrue(title, model.albumArtists.any { it.albumId == album.id && it.artistId == tyler })
            assertEquals("Tyler, The Creator", album.displayArtist)
        }
    }

    // H, I
    @Test
    fun `Kanye West keeps its id and MBDTF`() {
        val kanye = model.artists.single { it.key == "kanye west" }
        assertEquals(6684406489965805378L, kanye.id)
        assertEquals("Kanye West", kanye.name)
        val mbdtf = albumTitled("My Beautiful Dark Twisted Fantasy")
        assertTrue(model.albumArtists.any { it.albumId == mbdtf.id && it.artistId == kanye.id })
    }

    // J, K
    @Test
    fun `Her Loss and Without Warning are unchanged`() {
        assertEquals(16, albumTitled("Her Loss").songIds.size)
        assertEquals("Drake & 21 Savage", albumTitled("Her Loss").displayArtist)
        val ww = albumTitled("Without Warning")
        assertEquals(10, ww.songIds.size)
        val credited = model.credits.filter { it.songId in ww.songIds }.map { it.artistId }.toSet()
        for (key in listOf("21 savage", "offset", "metro boomin")) assertTrue(key, IdentityHash.of(key) in credited)
    }

    // L
    @Test
    fun `unresolved Kanye collaborations stay discoverable as unverified credits`() {
        for (title in listOf("Monster", "So Appalled", "Gorgeous")) {
            assertTrue(title, model.unverifiedSegments.any { it.songId == songId(title) && it.segmentKey == "kanye west" })
        }
    }

    // M
    @Test
    fun `a full library build produces no unresolved artist rows`() {
        assertTrue(model.artists.none { it.isUnresolved })
        val credited = model.credits.map { it.artistId }.toSet() + model.albumArtists.map { it.artistId }
        val artistIds = model.artists.map { it.id }.toSet()
        assertEquals(artistIds, credited) // every row is referenced and every reference has a row
        assertTrue(model.artists.none { '/' in it.key })
        assertEquals(model.artists.size, model.artists.map { it.key }.toSet().size)
    }

    @Test
    fun `a song with only unresolved credits has no primary artist rather than a fake one`() {
        assertEquals(NO_ARTIST_ID, model.songs.getValue(songId("Monster")).primaryArtistId)
        assertTrue(model.credits.none { it.songId == songId("Monster") })
    }

    @Test
    fun `a compound tagged album artist creates no artist`() {
        val tracks =
            listOf(
                RawTrack(1, "a", "Drake", "Drake & 21 Savage", "Her Loss", 1, 1, "/hl", false),
                RawTrack(2, "b", "21 Savage", "Drake & 21 Savage", "Her Loss", 2, 1, "/hl", false),
            )
        val m = LibraryModelBuilder.build(tracks, emptyMap())
        assertTrue(m.artists.none { it.key == "drake & 21 savage" })
        assertEquals("Drake & 21 Savage", m.albums.single().displayArtist)
    }

    // N
    @Test
    fun `album grouping is the same with or without the identity fix inputs`() {
        val withAlias = model.albums.map { it.id to it.songIds }
        val noArtists = LibraryModelBuilder.build(LibraryFixture.tracks.map { it.copy(rawArtist = "X") }, emptyMap()).albums.map { it.id to it.songIds }
        assertEquals(withAlias, noArtists)
    }
}
