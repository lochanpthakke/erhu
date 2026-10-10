package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryTest {
    private var clock = 1_000L
    private val history = SessionHistory(now = { clock })

    private fun song(id: Long) = Song(id = id, title = "t$id", artist = "a", album = "b", durationMs = 1L, uri = "u$id")

    @Test
    fun `a track is recorded once it actually plays`() {
        assertFalse(history.onItemBecameCurrent(song(1), isPlaying = false))
        assertTrue(history.entries.isEmpty())
        assertTrue(history.onPlaybackStarted())
        assertEquals(listOf(1L), history.entries.map { it.song.id })
        // Pause / resume of the same track is not a new play.
        assertFalse(history.onPlaybackStarted())
        assertEquals(1, history.entries.size)
    }

    @Test
    fun `skipping through tracks while paused records only the one that plays`() {
        history.onItemBecameCurrent(song(1), isPlaying = false)
        history.onItemBecameCurrent(song(2), isPlaying = false)
        history.onItemBecameCurrent(song(3), isPlaying = false)
        history.onPlaybackStarted()
        assertEquals(listOf(3L), history.entries.map { it.song.id })
    }

    @Test
    fun `repeats and replays are separate entries, newest first`() {
        history.onItemBecameCurrent(song(1), isPlaying = true)
        clock = 2_000L
        history.onItemBecameCurrent(song(2), isPlaying = true)
        clock = 3_000L
        history.onItemBecameCurrent(song(1), isPlaying = true)
        assertEquals(listOf(1L, 2L, 1L), history.entries.map { it.song.id })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), history.entries.map { it.playedAtMs })
        assertEquals(3, history.entries.map { it.entryId }.distinct().size)
        val first = history.entries.last()
        assertEquals(first, history.find(first.entryId))
    }

    @Test
    fun `an emptied queue records nothing`() {
        assertFalse(history.onItemBecameCurrent(null, isPlaying = true))
        assertFalse(history.onPlaybackStarted())
        assertTrue(history.entries.isEmpty())
    }

    @Test
    fun `history is capped`() {
        val small = SessionHistory(now = { 0L }, capacity = 2)
        (1L..3L).forEach { small.onItemBecameCurrent(song(it), isPlaying = true) }
        assertEquals(listOf(3L, 2L), small.entries.map { it.song.id })
    }
}
