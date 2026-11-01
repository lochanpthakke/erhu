package com.erhu.erhu.feature.library

import com.erhu.erhu.core.network.lyrics.CachedLyrics
import com.erhu.erhu.core.network.lyrics.CachedStatus
import com.erhu.erhu.core.network.lyrics.LyricsCandidate
import com.erhu.erhu.core.network.lyrics.LyricsTiming
import com.erhu.erhu.core.network.lyrics.Lyricsfile
import com.erhu.erhu.core.network.lyrics.OnlineLyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricsChoiceTest {
    private fun saved(explicit: Boolean) =
        CachedLyrics(
            songId = 7,
            fingerprint = "identity",
            status = CachedStatus.FOUND,
            candidate = wordTimed,
            fetchedAtMs = 0,
            explicitDownload = explicit,
        )

    @Test
    fun `session response and legacy saved content are never labelled explicit downloads`() {
        val session = chooseLyrics(7, null, online(), ManualLyricsSearch.Idle)
        assertFalse(session.downloaded)
        assertNull(session.saved)
        val legacy = chooseLyrics(7, null, online().copy(saved = saved(false)), ManualLyricsSearch.Idle)
        assertFalse(legacy.downloaded)
        assertFalse(legacy.saved!!.explicitDownload)
        val downloaded = chooseLyrics(7, null, online().copy(saved = saved(true)), ManualLyricsSearch.Idle)
        assertTrue(downloaded.downloaded)
    }

    @Test
    fun `saving and failure preserve the displayed timed text and saved state`() {
        val view = online().copy(saved = saved(true), saving = true)
        val busy = chooseLyrics(7, null, view, ManualLyricsSearch.Idle)
        assertTrue(busy.saving)
        assertTrue(busy.downloaded)
        assertEquals(wordTimed.bestText, busy.text)
        val failed = chooseLyrics(7, null, view.copy(saving = false, saveError = "Could not remove lyrics"), ManualLyricsSearch.Idle)
        assertTrue(failed.downloaded)
        assertEquals("Could not remove lyrics", failed.saveError)
        val removed = chooseLyrics(7, null, view.copy(saved = null, saving = false), ManualLyricsSearch.Idle)
        assertFalse(removed.downloaded)
        assertEquals(wordTimed.bestText, removed.text)
    }

    @Test
    fun `embedded lyrics stay available when a download exists and a refreshed version is separate`() {
        val stored = saved(true)
        val embedded = chooseLyrics(7, fileLyrics, online().copy(saved = stored), ManualLyricsSearch.Idle)
        assertEquals(fileLyrics, embedded.text)
        assertEquals(LyricsOrigin.FILE, embedded.origin)
        assertEquals(stored, embedded.saved)
        assertFalse(embedded.downloaded)
        val refreshed =
            chooseLyrics(7, null, online().copy(candidate = wordTimed.copy(providerId = 99), saved = stored), ManualLyricsSearch.Idle)
        assertFalse(refreshed.downloaded)
        assertEquals(stored, refreshed.saved)
    }

    @Test
    fun `synced fallback supersedes embedded plain text while synced file keeps priority`() {
        val result = chooseLyrics(7, "Untimed embedded lyrics", online(), ManualLyricsSearch.Idle)
        assertEquals(wordTimed.bestText, result.text)
        assertEquals(LyricsOrigin.ONLINE, result.origin)
        assertEquals(fileLyrics, chooseLyrics(7, fileLyrics, online(), ManualLyricsSearch.Idle).text)
        val noNetwork = chooseLyrics(7, "Untimed embedded lyrics", null, ManualLyricsSearch.Idle)
        assertEquals("Untimed embedded lyrics", noNetwork.text)
    }

    private val fileLyrics = "[00:01.00]From the file"
    private val wordTimed =
        LyricsCandidate(
            provider = "LRCLIB",
            providerId = 1,
            trackName = "Song",
            artistName = "Artist",
            syncedLyrics = "[00:12.00]Hello world",
            wordSyncedLrc =
                Lyricsfile.toEnhancedLrc(
                    "lines:\n- text: Hello world\n  start_ms: 12000\n  words:\n  - text: 'Hello '\n    start_ms: 12000\n  - text: world\n    start_ms: 12650",
                ),
        )

    private fun online(
        candidate: LyricsCandidate? = wordTimed,
        chosen: Boolean = false,
        songId: Long = 7,
        lookup: LyricsLookup = LyricsLookup.Idle,
    ) = OnlineLyricsView(songId, lookup, candidate, fromCache = false, userChosen = chosen)

    @Test
    fun `the file's own lyrics take priority over an automatic online result`() {
        val shown = chooseLyrics(7, fileLyrics, online(), ManualLyricsSearch.Idle)
        assertEquals(fileLyrics, shown.text)
        assertEquals(LyricsOrigin.FILE, shown.origin)
        assertTrue(shown.hasFileLyrics)
    }

    @Test
    fun `a result the user chose replaces the file's lyrics, and online fills in when the file has none`() {
        val chosen = chooseLyrics(7, fileLyrics, online(chosen = true), ManualLyricsSearch.Idle)
        assertEquals(LyricsOrigin.ONLINE, chosen.origin)
        assertEquals("LRCLIB", chosen.provider)
        assertTrue(chosen.hasFileLyrics) // so "Use the file's lyrics" is offered
        val noFile = chooseLyrics(7, null, online(), ManualLyricsSearch.Idle)
        assertEquals(wordTimed.wordSyncedLrc, noFile.text)
        assertEquals(LyricsTiming.WORD, noFile.timing)
        // Unreadable file lyrics count as none.
        assertEquals(LyricsOrigin.ONLINE, chooseLyrics(7, "   ", online(), ManualLyricsSearch.Idle).origin)
    }

    @Test
    fun `another song's lookup is never shown, and the lookup state shows when nothing is found`() {
        val stale = chooseLyrics(8, null, online(songId = 7), ManualLyricsSearch.Idle)
        assertNull(stale.text)
        assertEquals(LyricsLookup.Idle, stale.lookup)
        val searching = chooseLyrics(7, null, online(candidate = null, lookup = LyricsLookup.Searching), ManualLyricsSearch.Idle)
        assertNull(searching.text)
        assertEquals(LyricsLookup.Searching, searching.lookup)
        // While a refresh runs, the previous online lyrics stay on screen.
        assertEquals(wordTimed.wordSyncedLrc, chooseLyrics(7, null, online(lookup = LyricsLookup.Searching), ManualLyricsSearch.Idle).text)
    }

    @Test
    fun `online word timing reaches the karaoke parser, line timing stays line level`() {
        val words = LyricsParser.parse(wordTimed.bestText) as Lyrics.Synced
        assertTrue(words.hasWordTiming)
        assertEquals(
            listOf("Hello ", "world"),
            words.lines
                .single()
                .words!!
                .map { it.text },
        )
        assertEquals(
            listOf(12_000L, 12_650L),
            words.lines
                .single()
                .words!!
                .map { it.timeMs },
        )
        val lineOnly = LyricsParser.parse(wordTimed.copy(wordSyncedLrc = null).bestText) as Lyrics.Synced
        assertFalse(lineOnly.hasWordTiming)
        assertTrue(
            LyricsParser.parse(
                wordTimed.copy(wordSyncedLrc = null, syncedLyrics = null, plainLyrics = "Just text").bestText,
            ) is Lyrics.Plain,
        )
    }

    @Test
    fun `results map to what the page shows`() {
        assertEquals(LyricsLookup.Instrumental, OnlineLyricsResult.Instrumental.toView(7, null).lookup)
        assertEquals(LyricsLookup.Offline, OnlineLyricsResult.Offline.toView(7, wordTimed).lookup)
        assertEquals(wordTimed, OnlineLyricsResult.Offline.toView(7, wordTimed).candidate) // keeps what was shown
        val needs = OnlineLyricsResult.NeedsChoice(listOf(wordTimed)).toView(7, null).lookup
        assertTrue(needs is LyricsLookup.NeedsChoice)
    }
}
