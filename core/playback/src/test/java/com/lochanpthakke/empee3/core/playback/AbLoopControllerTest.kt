package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.AbLoopEdit
import com.lochanpthakke.empee3.core.model.AbLoopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loop against a fake player whose "position reached" events are fired by the test. */
class AbLoopControllerTest {
    private class FakePlayer : LoopPlayer {
        override var currentSongId: Long? = 1L
        override var currentIndex: Int = 0
        override var positionMs: Long = 0L
        override var durationMs: Long = 300_000L
        val seeks = mutableListOf<Long>()

        override fun seekTo(positionMs: Long) {
            seeks += positionMs
            this.positionMs = positionMs
        }
    }

    private class Scheduled(
        val index: Int,
        val positionMs: Long,
        val onReached: () -> Unit,
        var cancelled: Boolean = false,
    )

    private val player = FakePlayer()
    private val schedules = mutableListOf<Scheduled>()
    private val published = mutableListOf<AbLoopState?>()
    private val loop =
        AbLoopController(
            scheduler = { index, position, onReached ->
                val scheduled = Scheduled(index, position, onReached).also { schedules += it }
                Cancellable { scheduled.cancelled = true }
            },
            player = player,
            onStateChanged = { published += it },
        )

    /** Playback reaches B: what the player would do with every live message at that position. */
    private fun reach(positionMs: Long) {
        player.positionMs = positionMs
        schedules.filter { !it.cancelled && it.positionMs == positionMs && it.index == player.currentIndex }.forEach { it.onReached() }
    }

    private fun markers(
        a: Long,
        b: Long,
    ) {
        player.positionMs = a
        assertEquals(AbLoopEdit.OK, loop.setStart())
        player.positionMs = b
        assertEquals(AbLoopEdit.OK, loop.setEnd())
        // Playback carries on between the markers (enabling at or past B would jump to A at once).
        player.positionMs = (a + b) / 2
    }

    @Test
    fun `reaching B returns to A, every pass`() {
        markers(10_000, 20_000)
        assertEquals(AbLoopEdit.OK, loop.setEnabled(true))
        player.positionMs = 15_000
        reach(20_000)
        assertEquals(listOf(10_000L), player.seeks)
        reach(20_000)
        assertEquals(listOf(10_000L, 10_000L), player.seeks)
        assertEquals(AbLoopState(1L, 10_000, 20_000, enabled = true), loop.state)
        assertEquals(loop.state, published.last())
    }

    @Test
    fun `equal, reversed or missing markers are rejected`() {
        player.positionMs = 20_000
        loop.setEnd()
        player.positionMs = 20_000
        assertEquals(AbLoopEdit.A_NOT_BEFORE_B, loop.setStart()) // A == B
        player.positionMs = 25_000
        assertEquals(AbLoopEdit.A_NOT_BEFORE_B, loop.setStart()) // A after B
        player.positionMs = 19_500
        assertEquals(AbLoopEdit.A_NOT_BEFORE_B, loop.setStart()) // shorter than a second: no rapid loop
        assertNull(loop.state?.startMs)
        assertEquals(AbLoopEdit.NOT_READY, loop.setEnabled(true))
        assertTrue(schedules.isEmpty())
    }

    @Test
    fun `B is kept off the very end and nothing works without a track`() {
        player.positionMs = 300_000
        loop.setEnd()
        assertEquals(300_000L - AbLoopController.END_GUARD_MS, loop.state?.endMs)

        player.currentSongId = null
        assertEquals(AbLoopEdit.NO_TRACK, loop.setStart())
    }

    @Test
    fun `pause and resume keep the loop, a track change clears it and stale messages do nothing`() {
        markers(10_000, 20_000)
        loop.setEnabled(true)
        val first = schedules.single()

        // Pausing / resuming does not touch the schedule; the next pass still loops.
        reach(20_000)
        assertEquals(1, player.seeks.size)

        // Another track starts: loop gone, its schedule cancelled.
        player.currentSongId = 2L
        player.currentIndex = 1
        loop.onEntryChanged(sameEntryRepeated = false)
        assertNull(loop.state)
        assertTrue(first.cancelled)

        // Even if the old message fired late, it must not seek the new track.
        first.onReached()
        reach(20_000)
        assertEquals(1, player.seeks.size)
        assertEquals(AbLoopEdit.NOT_READY, loop.setEnabled(true))
    }

    @Test
    fun `repeat of the same entry keeps looping`() {
        markers(10_000, 20_000)
        loop.setEnabled(true)
        loop.onEntryChanged(sameEntryRepeated = true)
        reach(20_000)
        assertEquals(listOf(10_000L), player.seeks)
    }

    @Test
    fun `the same song at another queue position does not inherit a stale loop`() {
        markers(10_000, 20_000)
        loop.setEnabled(true)
        // Same song id, a different queue entry (a duplicate): the transition clears the loop.
        player.currentIndex = 3
        loop.onEntryChanged(sameEntryRepeated = false)
        reach(20_000)
        assertTrue(player.seeks.isEmpty())
    }

    @Test
    fun `disabling or clearing stops enforcement`() {
        markers(10_000, 20_000)
        loop.setEnabled(true)
        loop.setEnabled(false)
        reach(20_000)
        assertTrue(player.seeks.isEmpty())
        assertEquals(AbLoopState(1L, 10_000, 20_000, enabled = false), loop.state)

        player.positionMs = 15_000
        loop.setEnabled(true)
        loop.clear()
        reach(20_000)
        assertTrue(player.seeks.isEmpty())
        assertNull(loop.state)
        assertNull(published.last())
    }

    @Test
    fun `adjusting a marker reschedules and enabling past B starts at A`() {
        markers(10_000, 20_000)
        player.positionMs = 25_000
        loop.setEnabled(true)
        assertEquals(listOf(10_000L), player.seeks) // already past B: jump to A

        player.positionMs = 30_000
        assertEquals(AbLoopEdit.OK, loop.setEnd())
        reach(20_000) // the old B no longer loops
        assertEquals(1, player.seeks.size)
        reach(30_000)
        assertEquals(listOf(10_000L, 10_000L), player.seeks)
        assertEquals(1, schedules.count { !it.cancelled })
    }

    @Test
    fun `without a scheduler the loop cannot be enabled`() {
        val unsupported = AbLoopController(scheduler = null, player = player, onStateChanged = {})
        player.positionMs = 10_000
        unsupported.setStart()
        player.positionMs = 20_000
        unsupported.setEnd()
        assertEquals(AbLoopEdit.UNSUPPORTED, unsupported.setEnabled(true))
        assertFalse(unsupported.state!!.enabled)
    }
}
