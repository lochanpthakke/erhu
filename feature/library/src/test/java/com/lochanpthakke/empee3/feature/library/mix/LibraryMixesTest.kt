package com.lochanpthakke.empee3.feature.library.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMixesTest {
    private fun profile(
        tracks: List<com.lochanpthakke.empee3.core.model.TrackFacts>,
        plays: List<com.lochanpthakke.empee3.core.model.MonthlyPlays> = emptyList(),
    ) = SmartMixEngine.profile(tracks, plays, NOW)

    @Test
    fun spotlight_isTheMostPlayedArtistWithEnoughSongs_titledByName() {
        val tracks = (1L..8L).map { track(it, artist = 1) } + (11L..20L).map { track(it, artist = 2) }
        val mix = LibraryMixes.spotlight(profile(tracks, listOf(played(3, 4, daysAgo = 2))))!!
        assertEquals("Spotlight: Artist 1", mix.title)
        assertEquals(8, mix.songIds.size)
        assertEquals(3L, mix.songIds.first())
    }

    @Test
    fun spotlight_withoutHistory_isTheArtistWithTheMostSongs_andNeedsEnoughOfThem() {
        val tracks = (1L..8L).map { track(it, artist = 1) } + (11L..20L).map { track(it, artist = 2) }
        assertEquals("Spotlight: Artist 2", LibraryMixes.spotlight(profile(tracks))!!.title)
        assertNull(LibraryMixes.spotlight(profile((1L..5L).map { track(it, artist = 1) })))
    }

    @Test
    fun throwbacks_trustOnlyRealOldYears() {
        val old = (1L..8L).map { track(it).copy(year = 2009) }
        val placeholder = (11L..30L).map { track(it).copy(year = 1970) }
        val missing = (31L..40L).map { track(it).copy(year = 0) }
        val recent = (41L..50L).map { track(it).copy(year = 2024) }
        val mix = LibraryMixes.throwbacks(profile(old + placeholder + missing + recent))!!
        assertEquals((1L..8L).toSet(), mix.songIds.toSet())
        assertNull(LibraryMixes.throwbacks(profile(placeholder + missing + recent)))
    }

    @Test
    fun quickHitsAndLongListens_followTheSongsLength() {
        val short = (1L..9L).map { track(it).copy(durationMs = 140_000) }
        val long = (11L..19L).map { track(it).copy(durationMs = 360_000) }
        val p = profile(short + long + (21L..30L).map { track(it) })
        assertEquals((1L..9L).toSet(), LibraryMixes.quickHits(p)!!.songIds.toSet())
        assertEquals((11L..19L).toSet(), LibraryMixes.longListens(p)!!.songIds.toSet())
    }

    @Test
    fun yetToHear_needsRealHistory_andHoldsOnlyUnplayedSongs() {
        val tracks = (1L..20L).map { track(it) }
        assertNull(LibraryMixes.yetToHear(profile(tracks)))
        val plays = (1L..5L).map { played(it, 3, daysAgo = 3) }
        val mix = LibraryMixes.yetToHear(profile(tracks, plays))!!
        assertTrue(mix.songIds.none { it in 1L..5L })
        assertTrue(mix.songIds.size >= 8)
    }

    @Test
    fun everyLibraryMixIsOfferedOnlyWithEnoughSongs() {
        // A tiny library: no concept can be filled honestly, so none is offered.
        assertEquals(emptyList<SmartMix>(), LibraryMixes.build(profile((1L..4L).map { track(it, artist = 1) })))
    }
}
