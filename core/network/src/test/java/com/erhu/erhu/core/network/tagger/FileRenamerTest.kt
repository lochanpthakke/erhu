package com.erhu.erhu.core.network.tagger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileRenamerTest {

    @Test
    fun diwaliTitle_mp3Extension_producesDiwaliMp3() {
        val fileName = FileNamingUtils.buildTargetFileName("Diwali", "Skrillex", "mp3")
        assertEquals("Diwali.mp3", fileName)
        assertFalse("Filename must not contain artist", fileName.contains("Skrillex"))
    }

    @Test
    fun bangarangTitle_flacExtension_producesBangarangFlac() {
        val fileName = FileNamingUtils.buildTargetFileName("Bangarang", "Skrillex", "flac")
        assertEquals("Bangarang.flac", fileName)
        assertFalse("Filename must not contain artist", fileName.contains("Skrillex"))
    }

    @Test
    fun trackTitleWithM4aExtension_producesTrackTitleM4a() {
        val fileName = FileNamingUtils.buildTargetFileName("Track Title", "Artist Name", "m4a")
        assertEquals("Track Title.m4a", fileName)
        assertFalse("Filename must not contain artist", fileName.contains("Artist Name"))
    }

    @Test
    fun invalidFilenameCharacters_areSanitizedCorrectly() {
        val fileName = FileNamingUtils.buildTargetFileName("Diwali: Track? / Name*", "Skrillex", "mp3")
        assertEquals("Diwali Track Name.mp3", fileName)
        assertFalse("Filename must not contain artist", fileName.contains("Skrillex"))
    }

    @Test
    fun blankTitle_usesSafeFallbackTrack() {
        val fileName = FileNamingUtils.buildTargetFileName("   ", "Skrillex", "mp3")
        assertEquals("Track.mp3", fileName)
        assertFalse("Filename must not contain artist", fileName.contains("Skrillex"))
    }
}
