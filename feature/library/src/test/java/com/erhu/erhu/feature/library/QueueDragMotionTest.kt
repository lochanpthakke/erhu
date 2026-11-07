package com.erhu.erhu.feature.library

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class QueueDragMotionTest {
    private val songs = listOf(song(1), song(2), song(1), song(3))

    @Test
    fun landsBeforeCommittingOnceAndPreservesDuplicateOccurrenceIndices() = runTest {
        val motion = QueueDragMotion(100f)
        var queue = songs
        val calls = mutableListOf<Pair<Int, Int>>()
        motion.begin(queue, 2)
        motion.drag(2, -180f)
        withContext(Clock()) {
            motion.finish(true, { queue }) { from, to ->
                assertEquals(-200f, motion.offset, 0f)
                assertEquals(songs, motion.rows)
                assertTrue(motion.settling)
                calls += from to to
                queue = queue.toMutableList().apply { add(to, removeAt(from)) }
                true
            }
            motion.finish(true, { queue }) { _, _ -> error("Repeated commit") }
        }
        assertEquals(listOf(2 to 0), calls)
        assertEquals(listOf(1L, 1L, 2L, 3L), queue.map { it.id })
        assertNull(motion.rows)
        assertNull(motion.index)
        assertEquals(1, motion.handoff)
    }

    @Test
    fun cancellationSettlesBackWithoutChangingTheQueue() = runTest {
        val motion = QueueDragMotion(100f)
        val positions = mutableListOf<Float>()
        motion.begin(songs, 1)
        motion.drag(1, 140f)
        withContext(Clock { positions += motion.offset }) {
            motion.finish(false, { songs }) { _, _ -> error("Cancelled gesture reordered") }
        }
        assertTrue(positions.any { it > 0f && it < 140f })
        assertTrue(positions.all { it in 0f..140f })
        assertEquals(0f, motion.offset, 0f)
        assertEquals(0, motion.handoff)
        assertFalse(motion.settling)
    }

    @Test
    fun externalQueueChangesInvalidateTheOldIndices() = runTest {
        val motion = QueueDragMotion(100f)
        motion.begin(songs, 1)
        motion.drag(1, 100f)
        motion.queueChanged(songs.drop(1))
        withContext(Clock()) {
            motion.finish(true, { songs.drop(1) }) { _, _ -> error("Stale index used") }
        }
        assertNull(motion.index)
    }

    @Test
    fun queueChangeDuringLandingDoesNotCommitStaleMove() = runTest {
        val motion = QueueDragMotion(100f)
        var queue = songs
        motion.begin(queue, 0)
        motion.drag(0, 160f)
        withContext(Clock { queue = songs.drop(1) }) {
            motion.finish(true, { queue }) { _, _ -> error("Stale landing committed") }
        }
        assertNull(motion.rows)
    }

    @Test
    fun rapidFinishesAndNewDragsCannotDuplicateOrRetargetAPendingMove() = runTest {
        val motion = QueueDragMotion(100f)
        var queue = songs
        var calls = 0
        motion.begin(queue, 0)
        motion.drag(0, 110f)
        withContext(Clock()) {
            val first = launch {
                motion.finish(true, { queue }) { from, to ->
                    calls++
                    queue = queue.toMutableList().apply { add(to, removeAt(from)) }
                    true
                }
            }
            delay(32)
            motion.begin(queue, 3)
            assertEquals(0, motion.index)
            motion.finish(true, { queue }) { _, _ -> error("Duplicate finish") }
            first.join()
        }
        assertEquals(1, calls)
        assertNull(motion.index)
    }

    @Test
    fun closingTheSheetDuringLandingCancelsWithoutReordering() = runTest {
        val motion = QueueDragMotion(100f)
        motion.begin(songs, 0)
        motion.drag(0, 140f)
        withContext(Clock()) {
            val landing = launch { motion.finish(true, { songs }) { _, _ -> error("Cancelled job reordered") } }
            delay(32)
            landing.cancelAndJoin()
        }
        assertNull(motion.rows)
        assertFalse(motion.settling)
    }

    @Test
    fun failedAndUnacknowledgedReordersAlwaysReleaseTheVisualSession() = runTest {
        for (success in listOf(false, true)) {
            val motion = QueueDragMotion(100f)
            motion.begin(songs, 0)
            motion.drag(0, 100f)
            withContext(Clock()) { motion.finish(true, { songs }) { _, _ -> success } }
            assertNull(motion.index)
            assertFalse(motion.settling)
        }
    }

    @Test
    fun disabledAnimationsLandImmediatelyAndKeepExactlyOneCommit() = runTest {
        val motion = QueueDragMotion(100f)
        var queue = songs
        var calls = 0
        motion.begin(queue, 0)
        motion.drag(0, 140f)
        val reduced = object : MotionDurationScale { override val scaleFactor = 0f }
        withContext(Clock() + reduced) {
            motion.finish(true, { queue }) { from, to ->
                calls++
                assertEquals(100f, motion.offset, 0f)
                queue = queue.toMutableList().apply { add(to, removeAt(from)) }
                true
            }
        }
        assertEquals(1, calls)
        assertNull(motion.index)
    }

    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 1000, "content://song/$id")

    private class Clock(private val record: () -> Unit = {}) : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            nanos += 16_000_000
            return onFrame(nanos).also { record() }
        }
    }
}
