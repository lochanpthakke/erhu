package com.erhu.erhu.core.network.tagger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagWriteRequestTest {
    private val current = EditableTags(title = "Yellow", artist = "Coldplay", album = "Parachutes")

    private fun build(
        title: String = "Yellow",
        artist: String = "Coldplay",
        album: String = "Parachutes",
        year: Int = 0,
        genre: String = "",
    ) = buildTagWriteRequest("content://media/external/audio/media/1", "/sdcard/Music/y.mp3", current, title, artist, album, year, genre)

    @Test
    fun nothingChanged_returnsNull() {
        assertNull(build())
    }

    @Test
    fun onlyChangedFieldsAreWritten() {
        val request = build(title = "Yellow (Live)")!!
        assertEquals("Yellow (Live)", request.title)
        assertNull(request.artist)
        assertNull(request.album)
        assertNull(request.year)
        assertNull(request.genre)
    }

    @Test
    fun blankFields_keepTheFilesValues() {
        // Clearing a field in the dialog must never erase the tag in the file.
        assertNull(build(title = "  ", artist = "", album = ""))
    }

    @Test
    fun blankGenreAndZeroYear_areNotWritten() {
        val request = build(title = "New", genre = "   ", year = 0)!!
        assertNull(request.genre)
        assertNull(request.year)
    }

    @Test
    fun valuesAreTrimmed_andWhitespaceOnlyChangesIgnored() {
        assertNull(build(title = "  Yellow  "))
        assertEquals("Rock", build(genre = "  Rock ")!!.genre)
    }

    @Test
    fun validYear_isWritten_invalidIgnored() {
        assertEquals(2000, build(year = 2000)!!.year)
        assertNull(build(year = -5))
        assertNull(build(year = 123_456))
    }

    @Test
    fun extension_comesFromFileName_thenMime() {
        assertEquals("mp3", audioFileExtension("/storage/emulated/0/Download/Song.MP3"))
        assertEquals("flac", audioFileExtension("/storage/1234-ABCD/Music/a.b.flac"))
        assertEquals("m4a", audioFileExtension("/storage/emulated/0/Music/noext", "audio/mp4"))
        assertEquals("ogg", audioFileExtension("", "audio/ogg"))
        assertNull(audioFileExtension("/storage/emulated/0/Music/noext", "application/octet-stream"))
        assertNull(audioFileExtension("/storage/emulated/0/Music/weird.<>"))
    }
}
