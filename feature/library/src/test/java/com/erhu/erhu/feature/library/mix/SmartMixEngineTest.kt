package com.erhu.erhu.feature.library.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMixEngineTest {
    /** 80 songs over 8 artists (10 each, two albums per artist), genres by artist. */
    private val library =
        (1L..80L).map { id ->
            val artist = (id - 1) / 10 + 1
            val genre =
                when (artist) {
                    in 1L..4L -> "Hip-Hop"
                    5L -> "Ambient"
                    6L -> "R&B"
                    7L -> "Techno"
                    else -> "Indie Pop"
                }
            track(id, artist = artist, album = artist * 100 + (id % 2), genre = genre)
        }

    private fun profile(vararg plays: com.erhu.erhu.core.model.MonthlyPlays) =
        SmartMixEngine.profile(library, plays.toList(), NOW)

    @Test
    fun rotation_favoursRecentPlays_overOldHeavyOnes() {
        val p =
            profile(
                played(1, 30, daysAgo = 200), // heavy, long ago (outside the window)
                played(2, 3, daysAgo = 2),
                played(3, 6, daysAgo = 40), // heavier but older
                played(4, 3, daysAgo = 1),
                played(5, 2, daysAgo = 3),
                played(6, 2, daysAgo = 5),
                played(7, 2, daysAgo = 6),
            )
        val rotation = SmartMixEngine.rotation(p)!!.songIds
        assertFalse(1L in rotation)
        // Three plays a day ago beat six plays forty days ago.
        assertTrue(rotation.indexOf(4L) < rotation.indexOf(3L))
        assertEquals(4L, rotation.first())
    }

    @Test
    fun recencyWeighting_halvesOverTheHalfLife() {
        val p = profile(played(1, 4, daysAgo = 0), played(2, 4, daysAgo = 21))
        val now = p.history(1).recent
        val older = p.history(2).recent
        assertEquals(now / 2, older, 0.01f)
    }

    @Test
    fun forgottenFavourites_needRealPastListening_andAWhileAway() {
        val p =
            profile(
                played(10, 8, daysAgo = 120),
                played(11, 5, daysAgo = 90),
                played(12, 4, daysAgo = 70),
                played(13, 3, daysAgo = 65),
                played(14, 6, daysAgo = 100),
                played(15, 1, daysAgo = 200), // a single play is not a favourite
                played(16, 9, daysAgo = 3), // still being played
            )
        val forgotten = SmartMixEngine.forgotten(p)!!.songIds
        assertEquals(listOf(10L, 14L, 11L, 12L, 13L), forgotten)
    }

    @Test
    fun deepCuts_areLessPlayedSongsByEnjoyedArtists_withoutDuplicates() {
        // Artists 1 and 2 are loved (a few songs each played a lot); their other songs barely.
        val loved = profile(played(1, 20, 5), played(2, 15, 10), played(3, 1, 30), played(11, 9, 4), played(12, 8, 4))
        val ids = SmartMixEngine.deepCuts(loved)!!.songIds
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in setOf(1L, 2L, 11L, 12L) })
        assertTrue(ids.all { library[(it - 1).toInt()].artistId in setOf(1L, 2L) })
        // Never-played songs come before once-played ones of the same artist and album.
        assertTrue(3L !in ids.take(2))
        // One loved artist with too few other songs is not a mix.
        val small = SmartMixEngine.profile(library.filter { it.songId <= 4 || it.artistId > 1 }, listOf(played(1, 20, 5)), NOW)
        assertNull(SmartMixEngine.deepCuts(small))
    }

    @Test
    fun sparseHistory_offersNoPersonalMixes_andSaysSo() {
        val sparse = SmartMixEngine.build(profile(played(1, 2, 1), played(2, 1, 1)), taste = null)
        assertTrue(sparse.needsMoreListening)
        assertTrue(sparse.mixes.none { it.kind.personal })
        // Library mixes from genres still work: ambient songs are chill, techno is high energy... if enough.
        val withFavourites =
            SmartMixEngine.build(
                SmartMixEngine.profile(library.map { if (it.songId <= 4) it.copy(isFavorite = true) else it }, emptyList(), NOW),
                taste = null,
            )
        assertEquals(listOf(1L, 2L, 3L, 4L).toSet(), withFavourites.mix(MixKind.FAVOURITES)!!.songIds.toSet())
        assertNotNull(withFavourites.mix(MixKind.FRESH))
    }

    @Test
    fun differentHistories_giveDifferentMixes() {
        // Hip-hop is artists 1-4; the fan plays some songs of artists 1 and 2. The other listener plays R&B and indie pop.
        val hipHopFan = ((1L..5L) + (11L..15L)).map { played(it, 5, daysAgo = (it % 7).toInt()) }
        val ambientFan = ((51L..55L) + (71L..75L)).map { played(it, 5, daysAgo = (it % 7).toInt()) }
        val a = SmartMixEngine.build(SmartMixEngine.profile(library, hipHopFan, NOW), null)
        val b = SmartMixEngine.build(SmartMixEngine.profile(library, ambientFan, NOW), null)
        assertNotEquals(a.mix(MixKind.ROTATION)!!.songIds, b.mix(MixKind.ROTATION)!!.songIds)
        assertNotEquals(a.mix(MixKind.YOUR_SOUND)?.songIds, b.mix(MixKind.YOUR_SOUND)?.songIds)
        // The hip-hop fan's sound leans on hip-hop; the other listener's doesn't.
        val aSound = a.mix(MixKind.YOUR_SOUND)!!.songIds.take(3).map { library[(it - 1).toInt()].genre }
        assertTrue(aSound.all { it == "Hip-Hop" })
    }

    @Test
    fun vybeTaste_shapesYourKindOfSound_andIsCredited() {
        val history = ((1L..5L) + (11L..15L)).map { played(it, 5, daysAgo = 1) }
        val profile = SmartMixEngine.profile(library, history, NOW)
        // VYBE finds indie-pop songs close to this taste: they lead, though their genre differs.
        val near = (71L..80L).associateWith { 1f - (it - 71) * 0.01f }
        val withVybe = SmartMixEngine.build(profile, taste = near).mix(MixKind.YOUR_SOUND)!!
        assertEquals(71L, withVybe.songIds.first())
        assertTrue(withVybe.description.contains("VYBE"))
        val without = SmartMixEngine.build(profile, taste = null).mix(MixKind.YOUR_SOUND)!!
        assertFalse(without.description.contains("VYBE"))
    }

    @Test
    fun everyMix_hasNoDuplicates_andIsDeterministic() {
        val history = (1L..45L).map { played(it, (it % 6 + 1).toInt(), daysAgo = (it * 7 % 150).toInt()) }
        val first = SmartMixEngine.build(SmartMixEngine.profile(library, history, NOW), null)
        val again = SmartMixEngine.build(SmartMixEngine.profile(library.shuffled(java.util.Random(4)), history.shuffled(java.util.Random(9)), NOW), null)
        assertEquals(first, again)
        first.mixes.forEach { assertEquals(it.kind.title, it.songIds.size, it.songIds.toSet().size) }
    }

    @Test
    fun tasteSeeds_areTheMostLastingSongs() {
        val p = profile(played(1, 10, 1), played(2, 10, 300), played(3, 1, 1))
        val seeds = SmartMixEngine.tasteSeeds(p)
        assertEquals(listOf(1L, 2L, 3L), seeds.keys.toList())
    }
}
