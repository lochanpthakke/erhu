package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.Album
import com.erhu.erhu.feature.library.mix.RadioSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedSetlistsTest {
    private fun album(id: Long, artist: Long = id) =
        Album(id, "Album $id", "Artist $artist", artist, 10, 2020, "content://covers/$id")

    private fun radios(albums: List<Album>, artwork: Map<Long, String> = emptyMap()) =
        FeaturedSetlists.select(albums, artwork).filterIsInstance<FeaturedSetlist.RadioPick>()

    @Test
    fun `all album cards retain their metadata order and unique keys beside radios`() {
        val albums = (1L..5L).map { album(it) }
        val picks = FeaturedSetlists.select(albums, emptyMap())
        assertEquals(albums, picks.filterIsInstance<FeaturedSetlist.AlbumPick>().map { it.album })
        assertEquals(8, picks.size)
        assertEquals(picks.size, picks.map { it.key }.toSet().size)
        assertEquals(listOf("album:1", "radio:1", "album:2", "radio:2"), picks.take(4).map { it.key })
    }

    @Test
    fun `each radio launches the existing album seed for the card it describes`() {
        val cards = radios(listOf(album(3), album(7)))
        assertEquals(listOf(RadioSeed.Album(3), RadioSeed.Album(7)), cards.map { it.seed })
        assertEquals(listOf("Artist 3", "Artist 7"), cards.map { it.identity })
    }

    @Test
    fun `repeated artist has one station and only related distinct supporting art`() {
        val albums = listOf(album(1, 10), album(2, 20), album(3, 10), album(4, 10), album(5, 10))
        val cards = radios(albums)
        assertEquals(2, cards.size)
        assertEquals(listOf("content://covers/3", "content://covers/4"), cards.first().supportingArtwork)
        assertTrue(cards.last().supportingArtwork.isEmpty())
    }

    @Test
    fun `local portrait is preferred while remote profile never triggers a request`() {
        val seed = album(1)
        assertEquals("file:///portraits/one.jpg", radios(listOf(seed), mapOf(1L to "file:///portraits/one.jpg")).single().artworkUri)
        assertEquals(seed.artworkUri, radios(listOf(seed), mapOf(1L to "https://images.example/one.jpg")).single().artworkUri)
    }

    @Test
    fun `missing or remote album art uses the clean artwork fallback`() {
        val card = radios(listOf(album(1).copy(artworkUri = "https://images.example/cover.jpg"))).single()
        assertNull(card.artworkUri)
        assertTrue(card.supportingArtwork.isEmpty())
        assertNull(radios(listOf(album(1).copy(artworkUri = null))).single().artworkUri)
    }

    @Test
    fun `unknown artists use album identity without borrowing unrelated images`() {
        val albums = listOf(album(1, 9), album(2, 9)).map { it.copy(artist = "Unknown Artist") }
        val cards = radios(albums, mapOf(9L to "file:///unrelated.jpg"))
        assertEquals(listOf("Album 1", "Album 2"), cards.map { it.identity })
        assertEquals(albums.map { it.artworkUri }, cards.map { it.artworkUri })
        assertTrue(cards.all { it.supportingArtwork.isEmpty() })
    }

    @Test
    fun `empty and one song libraries have no unusable radio cards`() {
        assertTrue(FeaturedSetlists.select(emptyList(), emptyMap()).isEmpty())
        assertTrue(radios(listOf(album(1).copy(songCount = 1))).isEmpty())
        assertTrue(FeaturedSetlists.select(listOf(album(1).copy(songCount = 0)), emptyMap()).isEmpty())
        assertEquals(1, FeaturedSetlists.select(listOf(album(1).copy(songCount = 1)), emptyMap()).size)
    }

    @Test
    fun `compilations stay album cards and presentation is deterministic`() {
        val albums = listOf(album(1).copy(isCompilation = true), album(2), album(2))
        val result = FeaturedSetlists.select(albums, emptyMap())
        assertEquals(listOf(RadioSeed.Album(2)), radios(albums).map { it.seed })
        assertEquals(2, result.filterIsInstance<FeaturedSetlist.AlbumPick>().size)
        assertEquals(result, FeaturedSetlists.select(albums, emptyMap()))
    }
}
