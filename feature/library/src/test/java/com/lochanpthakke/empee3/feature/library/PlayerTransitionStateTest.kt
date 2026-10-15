package com.lochanpthakke.empee3.feature.library

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
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
