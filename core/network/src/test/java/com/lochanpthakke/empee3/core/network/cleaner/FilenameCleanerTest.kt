package com.lochanpthakke.empee3.core.network.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameCleanerTest {
    private val cleaner = FilenameCleaner()

    @Test
    fun diwaliSpotdownTaggedByJukeblocks_stripsWatermarkAndAttribution() {
        val result = cleaner.cleanFilenameAndExtractJunk("Diwali_spotdown.org_tagged_by_jukeblocks.mp3")
        assertEquals("Diwali", result.cleaned.title)
        assertNull(result.cleaned.artistGuess)
        assertTrue("Extracted junk tokens: ${result.extractedJunkTokens}", result.extractedJunkTokens.contains("spotdown.org"))
        assertTrue("Extracted junk tokens: ${result.extractedJunkTokens}", result.extractedJunkTokens.contains("tagged by jukeblocks"))
    }

    @Test
    fun downloadedFromWatermark_isStrippedAndExtracted() {
        val result = cleaner.cleanFilenameAndExtractJunk("Track_Title_downloaded_from_songsmpk.mp3")
        assertEquals("Track Title", result.cleaned.title)
        assertNull(result.cleaned.artistGuess)
        assertTrue("Extracted junk tokens: ${result.extractedJunkTokens}", result.extractedJunkTokens.contains("downloaded from songsmpk"))
    }

    @Test
    fun uploadedByWatermark_isStrippedAndExtracted() {
        val result = cleaner.cleanFilenameAndExtractJunk("Song_Name_uploaded_by_user123.flac")
        assertEquals("Song Name", result.cleaned.title)
        assertNull(result.cleaned.artistGuess)
        assertTrue("Extracted junk tokens: ${result.extractedJunkTokens}", result.extractedJunkTokens.contains("uploaded by user123"))
    }

    @Test
    fun legitimateTitle_tagged_isPreserved() {
        val cleaned = cleaner.cleanFilename("Tagged.mp3")
        assertEquals("Tagged", cleaned.title)
        assertNull(cleaned.artistGuess)
    }

    @Test
    fun legitimateTitle_byTheWay_isPreserved() {
        val cleaned = cleaner.cleanFilename("By The Way.mp3")
        assertEquals("By The Way", cleaned.title)
        assertNull(cleaned.artistGuess)
    }

    @Test
    fun legitimateTitle_taggedInAPhoto_isPreserved() {
        val cleaned = cleaner.cleanFilename("Tagged in a Photo.mp3")
        assertEquals("Tagged in a Photo", cleaned.title)
        assertNull(cleaned.artistGuess)
    }

    @Test
    fun legitimateTitle_driveByWithArtist_splitsArtistAndTitle() {
        val cleaned = cleaner.cleanFilename("Train - Drive By.mp3")
        assertEquals("Drive By", cleaned.title)
        assertEquals("Train", cleaned.artistGuess)
    }

    @Test
    fun balancedBracketsAtTheEnd_areKept_danglingOnesRemoved() {
        val cleaner = FilenameCleaner()
        assertEquals(
            "Mr. Lonely (feat. Frank Ocean & Steve Lacy)",
            cleaner.cleanFilename("Mr. Lonely (feat. Frank Ocean & Steve Lacy).mp3").title,
        )
        assertEquals("Song", FilenameCleaner.trimUnmatchedBrackets("Song)"))
        assertEquals("Song", FilenameCleaner.trimUnmatchedBrackets("(Song"))
        assertEquals("(Intro) Song", FilenameCleaner.trimUnmatchedBrackets("(Intro) Song"))
        assertEquals("Song [Live]", FilenameCleaner.trimUnmatchedBrackets(" Song [Live] - "))
    }

    @Test
    fun aTitleWithoutExtension_isNotCutAtItsLastDot() {
        val cleaner = FilenameCleaner()
        assertEquals("Mr. Lonely (feat. Frank Ocean & Steve Lacy)", cleaner.cleanFilename("Mr. Lonely (feat. Frank Ocean & Steve Lacy)").title)
        assertEquals("Mr. Brightside", cleaner.cleanFilename("Mr. Brightside.flac").title)
        assertEquals("Yellow", FilenameCleaner.trimUnmatchedBrackets("Yellow ["))
    }
}
