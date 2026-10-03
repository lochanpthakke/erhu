package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test per user-approved acceptance criterion (AC1–AC6, AC11). */
class AcceptanceCriteriaTest {
    private val model by lazy { LibraryModelBuilder.build(LibraryFixture.tracks, emptyMap()) }

    private fun albumsTitled(title: String) = model.albums.filter { it.title == title }

    @Test
    fun `her loss is exactly one album`() {
        assertEquals(1, albumsTitled("Her Loss").size)
        assertEquals(16, albumsTitled("Her Loss").single().songIds.size)
    }

    @Test
    fun `without warning is one album with all three artists resolved`() {
        val album = albumsTitled("Without Warning").single()
        val credits = model.credits.filter { it.songId in album.songIds }
        assertTrue(credits.none { it.isUnresolved })
        for (key in listOf("21 savage", "offset", "metro boomin")) {
            val artist = model.artists.single { it.key == key }
            assertFalse(artist.isUnresolved)
            assertTrue(credits.any { it.artistId == artist.id })
        }
    }

    @Test
    fun `tyler is one artist and the creator does not exist`() {
        assertEquals(1, model.artists.count { it.key == "tyler/ the creator" })
        assertTrue(model.artists.none { it.key == "the creator" || it.key == "tyler" })
    }

    @Test
    fun `nothing nowhere is one artist`() {
        val extra = RawTrack(9_000_001, "Song", "nothing/nowhere.", null, "Album", 1, 1, "/m/nn", false)
        val withNn = LibraryModelBuilder.build(LibraryFixture.tracks + extra, emptyMap())
        assertEquals(1, withNn.artists.count { it.key == "nothing/nowhere." })
        assertTrue(withNn.artists.none { it.key == "nothing" || it.key == "nowhere." })
    }

    @Test
    fun `yameii follows the explicit-evidence identity strategy`() {
        val tracks =
            listOf(
                RawTrack(1, "a", "Yameii", null, "A", 1, 1, "/y", false),
                RawTrack(2, "b", "Yameii Online", null, "B", 1, 1, "/y2", false),
            )
        val separate = LibraryModelBuilder.build(tracks, emptyMap())
        assertNotEquals(separate.artists.single { it.key == "yameii" }.id, separate.artists.single { it.key == "yameii online" }.id)
        val merged = LibraryModelBuilder.build(tracks, mapOf("yameii online" to "yameii"))
        assertEquals(1, merged.artists.size)
        assertEquals(2, merged.artists.single().songCount)
    }

    @Test
    fun `mbdtf stays linked to kanye west`() {
        val album = albumsTitled("My Beautiful Dark Twisted Fantasy").single()
        assertEquals("Kanye West", album.displayArtist)
        assertTrue(model.albumArtists.any { it.albumId == album.id && it.artistId == IdentityHash.of("kanye west") })
    }

    @Test
    fun `credits are identical with and without album presentation`() {
        val resolver = CreditResolver(EvidenceIndex.build(LibraryFixture.tracks))
        for (track in LibraryFixture.tracks) {
            val expected = resolver.resolve(track).map { it.text to it.role }
            val actual = model.credits.filter { it.songId == track.songId }.sortedBy { it.position }.map { it.creditText to it.role }
            assertEquals(expected, actual)
        }
    }
}
