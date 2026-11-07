package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.LyricsTimingAdjustment
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.LyricsTimingOffset
import com.erhu.erhu.core.model.Song
import org.junit.Assert.*
import org.junit.Test

class LyricsTimingStateTest {
    private val song = Song(7, "Song", "Artist", "Album", 10000, "content://songs/7")
    private val lyrics = SongLyrics(7, "[00:01.00]<00:01.00>First <00:01.50>word\n[00:03.00]Second line", LyricsOrigin.ONLINE, "LRCLIB", sourceId = "LRCLIB:1")

    @Test fun offsetDirectionMovesBothLinesAndWordsAndSeekingReversesTheSameTransform() {
        val parsed = LyricsParser.parse(lyrics.text) as Lyrics.Synced
        assertEquals(-1, parsed.lineAt(LyricsTimingOffset.lyricsPosition(1500, 1200)))
        val adjusted = LyricsTimingOffset.lyricsPosition(2700, 1200)
        assertEquals(0, parsed.lineAt(adjusted))
        assertEquals(1, parsed.wordAt(0, adjusted))
        assertEquals(2700L, LyricsTimingOffset.playbackPosition(parsed.lines[0].words!![1].timeMs, 1200))
        assertEquals(1000L, LyricsTimingOffset.lyricsPosition(500, -500))
        assertEquals(500L, LyricsTimingOffset.playbackPosition(1000, -500))
        assertEquals(0L, LyricsTimingOffset.playbackPosition(100, -500))
        assertEquals(10000L, LyricsTimingOffset.playbackPosition(20000, 1000, 10000))
        assertEquals(Long.MAX_VALUE, LyricsTimingOffset.lyricsPosition(Long.MAX_VALUE, -30000))
    }

    @Test fun embeddedLrcOffsetAndUserOffsetComposeWithoutRewritingTheText() {
        val text = "[offset:200]\n[00:01.00]Line"
        val parsed = LyricsParser.parse(text) as Lyrics.Synced
        assertEquals(800L, parsed.lines.first().timeMs)
        assertEquals(0, parsed.lineAt(LyricsTimingOffset.lyricsPosition(1200, 400)))
        assertEquals(1200L, LyricsTimingOffset.playbackPosition(parsed.lines.first().timeMs, 400))
        val shown = withLyricsTiming(song, lyrics.copy(text = text), emptyMap())!!
        assertEquals(text, shown.text)
    }

    @Test fun contextSeparatesTracksAndVersionsWhileOfflineCopiesRetainTiming() {
        val context = withLyricsTiming(song, lyrics, emptyMap())!!.timingContext!!
        val saved = mapOf(context.id to LyricsTimingAdjustment(context.songKey, context.sourceKey, context.label, 1250, 1))
        val offline = withLyricsTiming(song, lyrics.copy(origin = LyricsOrigin.ONLINE_SAVED), saved)!!
        assertEquals(context, offline.timingContext)
        assertEquals(1250L, offline.offsetMs)
        val changed = withLyricsTiming(song, lyrics.copy(sourceId = "LRCLIB:2"), saved)!!
        assertEquals(0L, changed.offsetMs)
        assertEquals(1250L, changed.previousTiming.single().offsetMs)
        assertEquals(0L, withLyricsTiming(song, lyrics.copy(text = lyrics.text + "\n[00:04.00]New"), saved)!!.offsetMs)
        val another = song.copy(id = 8, uri = "content://songs/8")
        assertEquals(0L, withLyricsTiming(another, lyrics.copy(songId = 8), saved)!!.offsetMs)
        assertNull(withLyricsTiming(another, lyrics, saved))
    }

    @Test fun plainOrMissingLyricsNeverExposeStaleTiming() {
        val context = LyricsTimingContext.create(song, lyrics.text!!, "file", "File lyrics")
        for (text in listOf(null, "Plain lyrics")) {
            val shown = withLyricsTiming(song, lyrics.copy(text = text, timingContext = context, offsetMs = 900), emptyMap())!!
            assertNull(shown.timingContext)
            assertEquals(0L, shown.offsetMs)
        }
    }

    @Test fun editorDraftBoundsAndPartialNumericInputDoNotChangeSavedValues() {
        val draft = LyricsTimingDraft(250)
        draft.input("-")
        assertFalse(draft.valid)
        assertEquals(250L, draft.offsetMs)
        draft.input("-1250")
        assertTrue(draft.valid)
        assertEquals(-1250L, draft.offsetMs)
        draft.step(1)
        assertEquals(-1200L, draft.offsetMs)
        draft.slide(1227f)
        assertEquals(1250L, draft.offsetMs)
        draft.input("90000")
        assertEquals(30000L, draft.offsetMs)
        draft.step(1)
        assertEquals(30000L, draft.offsetMs)
        draft.set(-90000)
        assertEquals(-30000L, draft.offsetMs)
        draft.set(0)
        assertEquals("0", draft.input)
        assertEquals(250L, LyricsTimingDraft(250).offsetMs) // Discarding a draft is Cancel.
    }
}
