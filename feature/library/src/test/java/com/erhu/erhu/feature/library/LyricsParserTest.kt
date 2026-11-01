package com.erhu.erhu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun `line timed lrc becomes synced lines in time order`() {
        val lyrics =
            LyricsParser.parse(
                """
                [ar:Someone]
                [ti:Song]
                [00:12.50]Second line
                [00:05.00]First line
                [01:02.3]Third
                """.trimIndent(),
            ) as Lyrics.Synced
        assertEquals(listOf(5_000L, 12_500L, 62_300L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("First line", "Second line", "Third"), lyrics.lines.map { it.text })
        assertFalse(lyrics.hasWordTiming)
    }

    @Test
    fun `several stamps on one line repeat it`() {
        val lyrics = LyricsParser.parse("[00:10.00][00:40.00]Chorus\n[00:20.00]Verse") as Lyrics.Synced
        assertEquals(listOf("Chorus", "Verse", "Chorus"), lyrics.lines.map { it.text })
    }

    @Test
    fun `offset shifts every timestamp`() {
        val lyrics = LyricsParser.parse("[offset:+500]\n[00:10.00]A") as Lyrics.Synced
        assertEquals(9_500L, lyrics.lines.single().timeMs)
    }

    @Test
    fun `enhanced lrc gives word timing`() {
        val lyrics = LyricsParser.parse("[00:10.00]<00:10.00>I <00:10.40>love <00:11.00>this") as Lyrics.Synced
        val line = lyrics.lines.single()
        assertTrue(lyrics.hasWordTiming)
        assertEquals("I love this", line.text)
        assertEquals(listOf(10_000L, 10_400L, 11_000L), line.words!!.map { it.timeMs })
        assertEquals(listOf("I ", "love ", "this"), line.words!!.map { it.text })
    }

    @Test
    fun `active line and word come from a binary search`() {
        val lyrics = LyricsParser.parse("[00:01.00]A\n[00:05.00]<00:05.00>b <00:06.00>c\n[00:09.00]D") as Lyrics.Synced
        assertEquals(-1, lyrics.lineAt(500))
        assertEquals(0, lyrics.lineAt(1_000))
        assertEquals(1, lyrics.lineAt(5_500))
        assertEquals(2, lyrics.lineAt(60_000))
        assertEquals(0, lyrics.wordAt(1, 5_500))
        assertEquals(1, lyrics.wordAt(1, 6_000))
        assertEquals(-1, lyrics.wordAt(0, 6_000))
    }

    @Test
    fun `text without timestamps is plain`() {
        val lyrics = LyricsParser.parse("Just words\nNo timing\n") as Lyrics.Plain
        assertEquals(listOf("Just words", "No timing"), lyrics.lines)
    }

    @Test
    fun `malformed stamps are ignored, never fatal`() {
        val lyrics = LyricsParser.parse("[00:99.00]Bad seconds\n[xx:10]Bad\n[00:03.00]Good") as Lyrics.Synced
        assertEquals(listOf("Good"), lyrics.lines.map { it.text })
        assertEquals(Lyrics.Plain(listOf("[ab:cd]x", "plain")), LyricsParser.parse("[ab:cd]x\nplain"))
    }

    @Test
    fun `blank lyrics are none`() {
        assertNull(LyricsParser.parse(null))
        assertNull(LyricsParser.parse("  \n "))
        assertNull(LyricsParser.parse("[ar:Only tags]\n[ti:Nothing else]"))
    }
}
