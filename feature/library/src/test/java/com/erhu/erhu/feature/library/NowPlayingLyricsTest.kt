package com.erhu.erhu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lyrics panel's active line: from the player's position and the real LRC timestamps, never estimated. */
class NowPlayingLyricsTest {
    private val synced =
        LyricsParser.parse(
            """
            [offset:+200]
            [00:01.20]First line
            [00:05.00]Second line
            [00:09.50]
            [00:12.00][00:40.00]Chorus
            [00:20.00]Verse two
            """.trimIndent(),
        ) as Lyrics.Synced

    private fun lineAt(ms: Long) = synced.lineAt(ms).let { if (it < 0) null else synced.lines[it].text }

    @Test
    fun beforeTheFirstLine_nothingIsActive() {
        assertEquals(null, lineAt(0))
    }

    @Test
    fun theActiveLineFollowsTheRealTimestamps_offsetApplied() {
        // [offset:+200] moves every line 200 ms earlier: 1.2 s becomes 1.0 s.
        assertEquals(null, lineAt(999))
        assertEquals("First line", lineAt(1_000))
        assertEquals("Second line", lineAt(4_800))
        // An empty timed line is an instrumental gap (shown as a note), still a line of its own.
        assertEquals("", lineAt(9_300))
    }

    @Test
    fun aLineWithSeveralTimestampsComesBackAtEachOfThem() {
        assertEquals("Chorus", lineAt(11_800))
        assertEquals("Verse two", lineAt(19_800))
        assertEquals("Chorus", lineAt(39_800))
    }

    @Test
    fun seekingBackOrForwardJumpsStraightToTheRightLine() {
        val seeks = listOf(1_000L, 30_000L, 2_000L, 0L, 45_000L).map(::lineAt)
        assertEquals(listOf("First line", "Verse two", "First line", null, "Chorus"), seeks)
    }

    @Test
    fun plainLyricsHaveNoActiveLine() {
        val plain = LyricsParser.parse("Hello there\nGeneral Kenobi")
        assertTrue(plain is Lyrics.Plain)
    }

    @Test
    fun aNewSongsLyricsAreReadFromScratch() {
        val other = LyricsParser.parse("[00:00.50]Other song") as Lyrics.Synced
        assertEquals(0, other.lineAt(600))
        assertTrue(synced.lineAt(600) < 0)
    }
}
