package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedAlbumsTest {
    private fun album(
        id: Long,
        songs: Int = 10,
    ) = Album(id = id, title = "Album $id", artist = "Artist", artistId = 1, songCount = songs, year = 2020)

    private fun track(
        songId: Long,
        albumId: Long,
        favourite: Boolean = false,
    ) = TrackFacts(songId, "Song $songId", 1, "Artist", albumId, "Album $albumId", null, null, 180_000, favourite, 0)

    private fun plays(
        songId: Long,
        count: Int,
    ) = MonthlyPlays(songId, "2026-10", count, 0)

    private val library = (1L..30L).map { album(it) }
    private val tracks = (1L..30L).map { track(songId = it * 100, albumId = it) }

    @Test
    fun mostPlayedAlbumsComeFirst_thenFavourites_thenTheRotation() {
        val withFavourite = tracks.map { if (it.albumId == 9L) it.copy(isFavorite = true) else it }
        val result =
            FeaturedAlbums.select(
                library,
                withFavourite,
                plays = listOf(plays(300, 2), plays(700, 9), plays(300, 1)),
                exclude = emptySet(),
                epochDay = 20_000,
            )
        assertEquals(listOf(7L, 3L, 9L), result.take(3).map { it.id })
        assertEquals(FeaturedAlbums.LIMIT, result.size)
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }

    @Test
    fun recentlyPlayedAlbumsAreLeftOut() {
        val result = FeaturedAlbums.select(library, tracks, listOf(plays(700, 9)), exclude = setOf(7L), epochDay = 20_000)
        assertTrue(result.none { it.id == 7L })
    }

    @Test
    fun withoutHistory_theRotationIsStableThroughTheDay_andChangesWithIt() {
        val today = FeaturedAlbums.select(library, tracks, emptyList(), emptySet(), epochDay = 20_000)
        val again = FeaturedAlbums.select(library, tracks, emptyList(), emptySet(), epochDay = 20_000)
        val tomorrow = FeaturedAlbums.select(library, tracks, emptyList(), emptySet(), epochDay = 20_001)
        assertEquals(today, again)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun theRotationPrefersFullAlbumsOverSingles() {
        val mixed = (1L..20L).map { album(it, songs = if (it <= 10) 1 else 8) }
        val result = FeaturedAlbums.select(mixed, emptyList(), emptyList(), emptySet(), epochDay = 5)
        assertTrue(result.all { it.songCount >= 3 })
    }

    @Test
    fun aSmallLibraryStillFillsFive_withSinglesThenRecentAlbums_neverTwice() {
        // Two full albums, two singles, and three albums on the Recently Played shelf.
        val small = listOf(album(1), album(2), album(3, songs = 1), album(4, songs = 1), album(5), album(6), album(7))
        val result = FeaturedAlbums.select(small + album(1), emptyList(), emptyList(), exclude = setOf(5L, 6L, 7L), epochDay = 9)
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.id }.toSet().size)
        assertEquals(setOf(1L, 2L), result.take(2).map { it.id }.toSet())
        assertEquals(setOf(3L, 4L), result.subList(2, 4).map { it.id }.toSet())
        assertTrue(result[4].id in setOf(5L, 6L, 7L))
    }

    @Test
    fun anEmptyLibraryHasNoFeaturedAlbums() {
        assertEquals(emptyList<Album>(), FeaturedAlbums.select(emptyList(), emptyList(), emptyList(), emptySet(), 1))
    }
}
