package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.ArtistOnSongs
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioPlan
import com.erhu.erhu.feature.library.mix.RadioSeed
import org.junit.Assert.*
import org.junit.Test

class RadioPortraitsTest {
    private val album = Album(1, "Station", "Lead", 1, 5, 2024)
    private fun song(id: Long) = Song(id, "Track $id", "Do not parse this credit", "Album", 1000, "content://songs/$id", "content://covers/$id")
    private fun artist(id: Long, song: Long) = ArtistOnSongs(Artist(id, "Artist $id", 1, 1, "file:///artist-$id.jpg", "artist $id"), song)
    private fun content(artists: List<ArtistOnSongs>) = RadioStationContent(
        album, RadioPlan(RadioSeed.Album(1), "Station", 3, listOf(1, 2, 3), emptyList()),
        listOf(song(1), song(2), song(3)), artists,
    )

    @Test fun leadIsCenteredAndOnlyTwoDistinctSupportingArtistsFromTheStationAppear() {
        val value = content(listOf(artist(2, 2), artist(1, 3), artist(3, 1), artist(4, 99), artist(2, 1)))
        val portraits = radioPortraits(value)
        assertEquals(listOf(1L, 2L, 3L), portraits.map { it.artistId })
        assertEquals(listOf(3L, 2L, 1L), portraits.map { it.song?.id })
        assertEquals("file:///artist-1.jpg", portraits.first().imageUri)
    }

    @Test fun missingPhotosUseThatArtistsOwnStationTrackAndMissingArtistsDoNotInventExtraPortraits() {
        val noPhoto = artist(1, 3).let { it.copy(artist = it.artist.copy(artworkUri = null)) }
        val portraits = radioPortraits(content(listOf(noPhoto, artist(2, 1))))
        assertEquals(2, portraits.size)
        assertNull(portraits.first().imageUri)
        assertEquals(3L, portraits.first().song?.id)
        val fallback = radioPortraits(content(emptyList())).single()
        assertNull(fallback.artistId)
        assertEquals(3L, fallback.song?.id)
    }

    @Test fun unresolvedAndOffStationArtistsAreNeverShown() {
        val unresolved = artist(1, 1).let { it.copy(artist = it.artist.copy(isUnresolved = true)) }
        assertEquals(listOf(2L), radioPortraits(content(listOf(unresolved, artist(9, 99), artist(2, 2)))).map { it.artistId })
    }
}
