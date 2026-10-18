package com.lochanpthakke.empee3.feature.library

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlayerTransitionStateTest {
    @Test
    fun collapseReachesThumbnailBeforeHandingOff() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.progress.value }) { state.collapse() }
        assertSubpixelHandoff(frames, 0f)
    }

    @Test
    fun expandReachesHeroBeforeHandingOff() = runTest {
        val state = PlayerTransitionState()
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.progress.value }) { state.expand() }
        assertSubpixelHandoff(frames, 1f)
    }

    @Test
    fun releasedDragReachesThumbnailBeforeHandingOff() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(0.5f)
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.progress.value }) { state.release(-0.5f) }
        assertSubpixelHandoff(frames, 0f)
    }

    @Test
    fun heroIsNotRevealedBeforeExactEndpoint() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(0.9995f)
        assertFalse(state.isSettledOpen)
        state.progress.snapTo(1f)
        assertTrue(state.isSettledOpen)
    }

    @Test
    fun collapseLandsThenSettlesExactlyToRest() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        val progress = mutableListOf<Float>()
        val arrival = mutableListOf<Float>()
        withContext(RecordingFrameClock { progress += state.progress.value; arrival += state.arrival.value }) { state.collapse() }
        // The transition itself never overshoots; the settle happens as it lands.
        assertTrue(progress.all { it in 0f..1f })
        val settleFrames = arrival.indices.filter { arrival[it] != 0f }
        assertTrue("No settle after collapsing", settleFrames.isNotEmpty())
        assertTrue(settleFrames.all { progress[it] <= 0.01f })
        // Right after it lands, not after the spring's last-pixel creep to exactly 0.
        val landed = progress.indexOfFirst { it <= 0.01f }
        val atRest = progress.indexOfFirst { it == 0f }
        // This clock counts every frame request: with the settle running beside the move, each real
        // frame is two records, and a spring's first frame hasn't moved yet. 4 records = 2 frames.
        assertTrue("Settle started ${settleFrames.first() - landed} records after landing", settleFrames.first() - landed <= 4)
        assertTrue("Settle waited for the creep to rest", settleFrames.first() < atRest)
        assertTrue("Settle too weak: ${arrival.max()}", arrival.max() > 0.5f)
        assertTrue("Settle too strong: ${arrival.max()}", arrival.max() <= 1f)
        assertTrue("Settle must rebound once", arrival.any { it < 0f })
        assertEquals(0f, state.arrival.value, 0f)
    }

    @Test
    fun expandNeverOvershootsAndSettlesAtTheHero() = runTest {
        val state = PlayerTransitionState()
        val progress = mutableListOf<Float>()
        withContext(RecordingFrameClock { progress += state.progress.value }) { state.expand() }
        assertTrue(progress.all { it in 0f..1f })
        assertTrue(state.isSettledOpen)
        assertEquals(0f, state.arrival.value, 0f)
    }

    @Test
    fun noSettleWhenThePlayerWasAlreadyResting() = runTest {
        val state = PlayerTransitionState()
        val arrival = mutableListOf<Float>()
        withContext(RecordingFrameClock { arrival += state.arrival.value }) { state.collapse() }
        assertTrue(arrival.all { it == 0f })
    }

    @Test
    fun aNewDragEndsTheSettleAtOnceAtRest() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        withContext(RecordingFrameClock {}) {
            val closing = launch { state.collapse() }
            // Run until the settle is under way, then grab the player again.
            while (state.progress.value != 0f || state.arrival.value == 0f) delay(16)
            state.dragBy(0.1f)
            closing.join()
        }
        assertEquals(0f, state.arrival.value, 0f)
        assertEquals(0.1f, state.progress.value, 1e-6f)
    }

    private fun assertSubpixelHandoff(frames: List<Float>, endpoint: Float) {
        assertEquals(endpoint, frames.last(), 0f)
        val lastMovingFrame = frames.last { it != endpoint }
        // A phone-sized root-coordinate journey amplifies a progress tolerance.
        val correctionPx = abs(lastMovingFrame - endpoint) * 2400f
        assertTrue("Final handoff moved $correctionPx px", correctionPx < 1f)
    }

    private class RecordingFrameClock(private val record: () -> Unit) : MonotonicFrameClock {
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            timeNanos += 16_000_000L
            return onFrame(timeNanos).also { record() }
        }
    }
}
