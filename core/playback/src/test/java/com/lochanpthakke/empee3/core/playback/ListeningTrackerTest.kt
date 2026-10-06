package com.lochanpthakke.empee3.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The play-count definition: a start of a song counts once, when at least half of the
 * song was actually heard (real playing time, not the position), driven by the same
 * callbacks the player sends (item changes, playing on / off, progress, end of queue).
 */
class ListeningTrackerTest {
    private var clock = 0L
    private val counted = mutableListOf<Long>()
    private val listened = mutableListOf<Pair<Long, Long>>()
    private val tracker = ListeningTracker({ clock }, { counted += it }, { id, ms -> listened += id to ms })

    /** Every song is 200 s long: 100 s heard is half. */
    private val length = 200_000L

    /** A new queue (or a tap on a song) while paused, then playback starting. */
    private fun startSong(id: Long) {
        tracker.onItemChanged(id, length, isPlaying = false)
        tracker.onPlayingChanged(isPlaying = true, durationMs = length)
    }

    /** [ms] of playback, with the position poll running as it does while playing. */
    private fun listen(ms: Long) {
        val end = clock + ms
        while (clock < end) {
            clock = minOf(clock + POLL_MS, end)
            tracker.onProgress(length)
        }
    }

    private fun pause() = tracker.onPlayingChanged(isPlaying = false, durationMs = length)

    private fun resume() = tracker.onPlayingChanged(isPlaying = true, durationMs = length)

    @Test
    fun `starting a song counts nothing until half of it was heard`() {
        startSong(1)
        listen(99_000L)
        assertEquals(emptyList<Long>(), counted)
        listen(1_000L)
        assertEquals(listOf(1L), counted)
    }

    @Test
    fun `listening on to the end still counts once`() {
        startSong(1)
        listen(length)
        pause()
        assertEquals(listOf(1L), counted)
    }

    @Test
    fun `a song skipped before half of it was heard does not count`() {
        startSong(1)
        listen(60_000L)
        tracker.onItemChanged(2, length, isPlaying = true)
        listen(100_000L)
        assertEquals(listOf(2L), counted)
    }

    @Test
    fun `pause and resume do not count and do not restart the listen`() {
        startSong(1)
        listen(40_000L)
        pause()
        clock += 600_000L // ten minutes paused: not heard
        resume()
        listen(40_000L)
        pause()
        resume()
        assertEquals(emptyList<Long>(), counted)
        listen(20_000L) // 40 + 40 + 20 = half
        assertEquals(listOf(1L), counted)
        pause()
        resume()
        listen(100_000L)
        assertEquals(listOf(1L), counted)
    }

    @Test
    fun `seeking does not count and does not add heard time`() {
        startSong(1)
        listen(10_000L)
        // A seek to near the end rebuffers: playing goes off and on, the item does not change.
        pause()
        resume()
        listen(10_000L)
        assertEquals(emptyList<Long>(), counted)
    }

    @Test
    fun `a restored song counts only when half of it is heard after resuming`() {
        // Restoring hands the saved song to the player without playing it.
        tracker.onItemChanged(7, length, isPlaying = false)
        assertEquals(emptyList<Long>(), counted)
        resume()
        listen(30_000L)
        pause()
        assertEquals(emptyList<Long>(), counted)
        resume()
        listen(70_000L)
        assertEquals(listOf(7L), counted)
    }

    @Test
    fun `the queue advancing starts a new listen of the next song`() {
        startSong(1)
        listen(length)
        tracker.onItemChanged(2, length, isPlaying = true)
        tracker.onPlayingChanged(isPlaying = true, durationMs = length) // a duplicate "playing" callback changes nothing
        listen(100_000L)
        assertEquals(listOf(1L, 2L), counted)
    }

    @Test
    fun `starting the same song again intentionally can count again`() {
        startSong(1)
        listen(120_000L)
        startSong(1)
        listen(100_000L)
        assertEquals(listOf(1L, 1L), counted)
    }

    @Test
    fun `repeat one counts each loop that is half heard`() {
        startSong(1)
        listen(length)
        tracker.onItemChanged(1, length, isPlaying = true) // loop 2
        listen(length)
        tracker.onItemChanged(1, length, isPlaying = true) // loop 3, skipped early
        listen(20_000L)
        tracker.onItemChanged(2, length, isPlaying = true)
        assertEquals(listOf(1L, 1L), counted)
    }

    @Test
    fun `skipping through songs while paused counts nothing`() {
        startSong(1)
        pause()
        tracker.onItemChanged(2, length, isPlaying = false)
        tracker.onItemChanged(3, length, isPlaying = false)
        resume()
        listen(100_000L)
        assertEquals(listOf(3L), counted)
    }

    @Test
    fun `playing again after the queue ended is a new listen`() {
        startSong(1)
        listen(length)
        pause()
        tracker.onEnded()
        resume()
        listen(99_000L)
        assertEquals(listOf(1L), counted)
        listen(1_000L)
        assertEquals(listOf(1L, 1L), counted)
    }

    @Test
    fun `the threshold is also checked when playback stops`() {
        startSong(1)
        clock += 100_000L // no poll ran (e.g. the screen was off), then the song changes
        tracker.onItemChanged(2, length, isPlaying = true)
        assertEquals(listOf(1L), counted)
    }

    @Test
    fun `a song of unknown length counts once its length is known`() {
        tracker.onItemChanged(1, 0L, isPlaying = false)
        tracker.onPlayingChanged(isPlaying = true, durationMs = 0L)
        clock += 150_000L
        tracker.onProgress(0L)
        assertEquals(emptyList<Long>(), counted)
        tracker.onProgress(length)
        assertEquals(listOf(1L), counted)
    }

    @Test
    fun `an empty queue counts nothing`() {
        tracker.onItemChanged(null, 0L, isPlaying = false)
        tracker.onPlayingChanged(isPlaying = true, durationMs = 0L)
        listen(length)
        assertEquals(emptyList<Long>(), counted)
        assertEquals(emptyList<Pair<Long, Long>>(), listened)
    }

    @Test
    fun `listening time is the time spent playing`() {
        startSong(1)
        clock = 30_000L
        pause() // paused for a minute: not counted
        clock = 90_000L
        resume()
        clock = 100_000L
        tracker.onItemChanged(2, length, isPlaying = true)
        clock = 160_000L
        tracker.checkpoint()
        clock = 170_000L
        pause()
        assertEquals(listOf(1L to 30_000L, 1L to 10_000L, 2L to 60_000L, 2L to 10_000L), listened)
    }

    @Test
    fun `a checkpoint while paused records nothing`() {
        startSong(1)
        clock = 5_000L
        pause()
        clock = 50_000L
        tracker.checkpoint()
        assertEquals(listOf(1L to 5_000L), listened)
    }

    private companion object {
        const val POLL_MS = 250L
    }
}
