package com.erhu.erhu.feature.library.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodsTest {
    @Test
    fun bpmAlone_neverDecidesAMood() {
        val noGenre = track(1, bpm = 150)
        assertFalse(Moods.isHighEnergy(noGenre))
        assertFalse(Moods.isChill(track(2, bpm = 72)))
        assertFalse(Moods.isLateNight(track(3, bpm = 80)))
    }

    @Test
    fun halfAndDoubleTimeBpm_isTreatedAsUnknown() {
        assertNull(Moods.tempo(track(1, bpm = 64))) // could be 128
        assertNull(Moods.tempo(track(2, bpm = 190))) // could be 95
        assertEquals(120, Moods.tempo(track(3, bpm = 120)))
        // Techno tagged at half time is still high energy: the genre decides, the tempo is unknown.
        assertTrue(Moods.isHighEnergy(track(4, genre = "Techno", bpm = 64)))
    }

    @Test
    fun genresDecide_withTheTempoAsSupport() {
        assertTrue(Moods.isHighEnergy(track(1, genre = "Drum and Bass")))
        assertTrue(Moods.isChill(track(2, genre = "Lo-Fi")))
        assertTrue(Moods.isLateNight(track(3, genre = "R&B")))
        // A trusted fast tempo vetoes late night and a calm-ish genre.
        assertFalse(Moods.isLateNight(track(4, genre = "R&B", bpm = 140)))
        assertFalse(Moods.isChill(track(5, genre = "Folk", bpm = 150)))
        // Explicitly chill genres keep their mood whatever the tag says.
        assertTrue(Moods.isChill(track(6, genre = "Ambient", bpm = 140)))
        // A known non-mood genre needs a clearly fast trusted tempo to count as high energy.
        assertTrue(Moods.isHighEnergy(track(7, genre = "Indie Pop", bpm = 130)))
        assertFalse(Moods.isHighEnergy(track(8, genre = "Indie Pop", bpm = 100)))
    }

    @Test
    fun calmAndEnergeticDoNotOverlap() {
        val softRock = track(1, genre = "Soft Rock")
        assertFalse(Moods.isHighEnergy(softRock))
        assertTrue(Moods.isChill(softRock))
        assertFalse(Moods.isChill(track(2, genre = "Folk Punk")))
    }

    @Test
    fun severalGenresInOneTag_areRead() {
        assertTrue(Moods.isLateNight(track(1, genre = "Pop; Neo Soul")))
        assertEquals(listOf("hip-hop", "r&b"), genresOf(track(2, genre = "Hip-Hop / R&B")))
    }
}
