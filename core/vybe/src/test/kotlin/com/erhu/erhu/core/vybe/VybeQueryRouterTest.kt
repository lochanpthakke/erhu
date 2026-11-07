package com.erhu.erhu.core.vybe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Experiment 3's artist / album queries, answered from metadata only. */
class VybeQueryRouterTest {
    private val library =
        listOf(
            SongText(1, "EARFQUAKE", "Tyler/ The Creator", "IGOR"),
            SongText(2, "NEW MAGIC WAND", "Tyler/ The Creator", "IGOR"),
            SongText(3, "911 / Mr. Lonely", "Tyler, The Creator feat. Frank Ocean & Steve Lacy", "Flower Boy"),
            SongText(4, "Runaway", "Kanye West feat. Pusha T", "My Beautiful Dark Twisted Fantasy"),
            SongText(5, "Rich Flex", "Drake & 21 Savage", "Her Loss"),
            SongText(6, "Late", "Kanye West", "Late Registration"),
        )

    private fun route(query: String) = VybeQueryRouter.route(query, library)

    @Test
    fun moreFromAnArtist_findsEveryCredit_despiteABrokenSeparator() {
        val route = route("more music from Tyler, the Creator") as VybeRoute.Named
        assertEquals(EntityKind.ARTIST, route.kind)
        assertEquals(listOf(1L, 2L, 3L), route.songIds)
        assertEquals(false, route.wantsSimilar)
    }

    @Test
    fun songsLikeAFeaturedArtist_findsTheSongsCreditingThem() {
        val route = route("songs like Frank Ocean") as VybeRoute.Named
        assertEquals(listOf(3L), route.songIds)
        assertTrue("similar songs are asked for, not inferred", route.wantsSimilar)
    }

    @Test
    fun albumAndTitleQueries_matchWholeNames() {
        assertEquals(VybeRoute.Named(EntityKind.ALBUM, "IGOR", listOf(1L, 2L), true), route("albums like IGOR"))
        assertEquals(VybeRoute.Named(EntityKind.TITLE, "Runaway", listOf(4L), true), route("songs like Runaway"))
    }

    @Test
    fun aNameNotInTheLibrary_isReportedUnknown_notGuessed() {
        assertEquals(VybeRoute.UnknownName("Blonde", "songs like Blonde"), route("songs like Blonde"))
    }

    @Test
    fun moodQueries_goToSemanticSearch_evenWithAnAlbumWordInThem() {
        assertEquals(VybeRoute.Describe("late night music"), route("late night music"))
        assertEquals(VybeRoute.Describe("dark atmospheric hip-hop"), route("dark atmospheric hip-hop"))
    }

    @Test
    fun knownArtistAliases_areSearchOnly_andOtherShortNamesAreNotGuessed() {
        assertEquals(listOf(4L, 6L), (route("songs by Ye") as VybeRoute.Named).songIds)
        assertTrue(route("songs by Li") is VybeRoute.UnknownName)
    }
}
