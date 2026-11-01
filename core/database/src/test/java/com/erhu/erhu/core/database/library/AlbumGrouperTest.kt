package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGrouperTest {
    private val identity = ArtistIdentityResolver(emptyMap())

    private fun input(
        id: Long,
        album: String = "Her Loss",
        aa: String? = null,
        folder: String = "/Music/Her Loss",
        track: Int = id.toInt(),
        compilation: Boolean = false,
    ) = AlbumInput(id, album, aa, folder, track, 1, compilation)

    @Test
    fun `no album artist groups by title and folder`() {
        val groups = AlbumGrouper.group((1L..16L).map { input(it) }, identity)
        assertEquals(1, groups.size)
        assertEquals(16, groups[0].songIds.size)
    }

    @Test
    fun `same title without album artist in two folders merges into one album`() {
        val groups =
            AlbumGrouper.group(
                listOf(
                    input(1, "Ganapathi Geethamala", folder = "/Download"),
                    input(2, "Ganapathi Geethamala", folder = "/Download/Seal/Audio"),
                ),
                identity,
            )
        assertEquals(1, groups.size)
    }

    @Test
    fun `same album artist and title across folders is one album`() {
        val groups = AlbumGrouper.group(listOf(input(1, aa = "Drake", folder = "/a"), input(2, aa = "Drake", folder = "/b")), identity)
        assertEquals(1, groups.size)
        assertEquals("aa:drake", groups[0].groupingKey)
    }

    @Test
    fun `missing album artist inherits within the folder`() {
        val groups = AlbumGrouper.group(listOf(input(1, aa = "Drake"), input(2), input(3)), identity)
        assertEquals(1, groups.size)
        assertFalse(groups[0].albumArtistInconsistent)
        assertEquals("drake", groups[0].albumArtistKey)
    }

    @Test
    fun `differing album artists without track collisions merge and are flagged`() {
        val groups = AlbumGrouper.group(listOf(input(1, aa = "Drake"), input(2, aa = "Drake"), input(3, aa = "Drake & 21 Savage")), identity)
        assertEquals(1, groups.size)
        assertTrue(groups[0].albumArtistInconsistent)
        assertEquals("drake", groups[0].albumArtistKey)
    }

    @Test
    fun `differing album artists with colliding track numbers split`() {
        val groups =
            AlbumGrouper.group(
                listOf(
                    input(1, "Greatest Hits", "Queen", "/m", 1),
                    input(2, "Greatest Hits", "ABBA", "/m", 1),
                    input(3, "Greatest Hits", null, "/m", 2),
                ),
                identity,
            )
        assertEquals(2, groups.size)
    }

    @Test
    fun `blank album titles group per folder`() {
        val groups = AlbumGrouper.group(listOf(input(1, "", folder = "/a"), input(2, " ", folder = "/a"), input(3, "", folder = "/b")), identity)
        assertEquals(listOf(2, 1), groups.map { it.songIds.size }.sortedDescending())
        assertTrue(groups.all { it.title == UNKNOWN_ALBUM })
    }

    @Test
    fun `untitled tracks with an album artist still group per folder`() {
        // review finding 2: loose singles tagged "Drake" in different folders are not one library-wide album
        val groups =
            AlbumGrouper.group(
                listOf(
                    input(1, "", aa = "Drake", folder = "/Download"),
                    input(2, "Unknown Album", aa = "Drake", folder = "/WhatsApp Documents"),
                    input(3, "", aa = "Drake", folder = "/Music/x"),
                ),
                identity,
            )
        assertEquals(3, groups.size)
        assertTrue(groups.all { it.title == UNKNOWN_ALBUM && it.groupingKey.startsWith("dir:") })
    }

    @Test
    fun `compilation flag is carried`() {
        assertTrue(AlbumGrouper.group(listOf(input(1, compilation = true), input(2)), identity)[0].isCompilationTagged)
    }

    @Test
    fun `ids are deterministic and independent of input order`() {
        val inputs = (1L..10L).map { input(it, folder = if (it % 2 == 0L) "/a" else "/b") }
        assertEquals(AlbumGrouper.group(inputs, identity), AlbumGrouper.group(inputs.reversed(), identity))
    }
}
