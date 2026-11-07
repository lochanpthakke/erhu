package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.erhu.erhu.core.designsystem.foundation.Motion
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
    fun releaseVelocityStillOverridesThePositionThresholdInBothDirections() = runTest {
        val closing = PlayerTransitionState()
        closing.progress.snapTo(0.85f)
        withContext(RecordingFrameClock {}) { closing.release(-2f) }
        assertEquals(0f, closing.fraction, 0f)
        val opening = PlayerTransitionState()
        opening.progress.snapTo(0.2f)
        withContext(RecordingFrameClock {}) { opening.release(2f) }
        assertEquals(1f, opening.fraction, 0f)
    }

    @Test
    fun fastEndpointReleasesKeepEveryDrawingFractionInBounds() = runTest {
        for (velocity in listOf(-4f, 4f)) {
            val state = PlayerTransitionState()
            state.progress.snapTo(if (velocity > 0f) 0.95f else 0.05f)
            val frames = mutableListOf<Float>()
            withContext(RecordingFrameClock { frames += state.fraction }) { state.release(velocity) }
            assertTrue(frames.all { it in 0f..1f })
            assertEquals(if (velocity > 0f) 1f else 0f, state.fraction, 0f)
        }
    }

    @Test
    fun collapseReachesThumbnailBeforeHandingOff() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.progress.value }) { state.collapse() }
        assertSubpixelHandoff(frames, 0f)
    }

    @Test
    fun expansionReachesItsExactEndpointWithoutCorrection() = runTest {
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
    fun expandedLayeringOnlyResumesAtVisualEndpoint() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(0.9995f)
        assertFalse(state.isSettledOpen)
        state.progress.snapTo(1f)
        assertTrue(state.isSettledOpen)
    }

    @Test
    fun collapseEndsAtRestWithoutASecondMotionOwner() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.fraction }) { state.collapse() }
        assertTrue(frames.all { it in 0f..1f })
        assertEquals(0f, state.fraction, 0f)
        assertFalse(state.progress.isRunning)
    }

    @Test
    fun expandNeverOvershootsAndSettlesAtTheHero() = runTest {
        val state = PlayerTransitionState()
        val progress = mutableListOf<Float>()
        withContext(RecordingFrameClock { progress += state.fraction }) { state.expand() }
        assertTrue(progress.all { it in 0f..1f })
        assertTrue(state.isSettledOpen)
        assertFalse(state.progress.isRunning)
    }

    @Test
    fun noSettleWhenThePlayerWasAlreadyResting() = runTest {
        val state = PlayerTransitionState()
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.fraction }) { state.collapse() }
        assertTrue(frames.all { it == 0f })
    }

    @Test
    fun draggingFromCollapsedRestHasNoResidualTransform() = runTest {
        val state = PlayerTransitionState()
        state.dragBy(0.1f)
        assertEquals(0.1f, state.fraction, 1e-6f)
        assertTrue(state.isOpen)
        assertFalse(state.progress.isRunning)
    }

    @Test
    fun dragFeedbackIsImmediateAndReversesWithoutAnotherJob() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        state.dragBy(-0.4f)
        assertEquals(0.6f, state.fraction, 1e-6f)
        state.dragBy(0.2f)
        assertEquals(0.8f, state.fraction, 1e-6f)
        withContext(RecordingFrameClock {}) { state.release(0f) }
        assertEquals(1f, state.fraction, 0f)
    }

    @Test
    fun fingerTakesOverAnInProgressExpansionUntilRelease() = runTest {
        val state = PlayerTransitionState()
        withContext(RecordingFrameClock {}) {
            val opening = launch { state.expand() }
            while (state.fraction < 0.3f) delay(16)
            state.dragBy(-0.1f)
            val held = state.fraction
            delay(80)
            assertEquals(held, state.fraction, 0f)
            state.release(-2f)
            opening.join()
        }
        assertEquals(0f, state.fraction, 0f)
    }

    @Test
    fun rapidTapCommandsFinishAtTheLatestRequestedState() = runTest {
        val state = PlayerTransitionState()
        withContext(RecordingFrameClock {}) {
            val opening = launch { state.expand() }
            delay(32)
            val closing = launch { state.collapse() }
            delay(32)
            state.expand()
            opening.join()
            closing.join()
        }
        assertEquals(1f, state.fraction, 0f)
        assertFalse(state.progress.isRunning)
    }

    @Test
    fun shortCancelledPullReturnsBrisklyWithoutBounceOrHandoffSnap() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        state.dragBy(-0.025f)
        val improved = mutableListOf<Float>()
        withContext(RecordingFrameClock { improved += state.fraction }) { state.release(0f) }
        val old = Animatable(0.975f, visibilityThreshold = 0.0001f)
        val baseline = mutableListOf<Float>()
        withContext(RecordingFrameClock { baseline += old.value }) {
            old.animateTo(1f, Motion.gestureSettle(0.0001f))
        }
        assertTrue(improved.size < baseline.size)
        assertTrue(improved.all { it in 0.975f..1f })
        assertSubpixelHandoff(improved, 1f)
    }

    @Test
    fun reducedMotionReleaseHasNoIntermediateFrames() = runTest {
        val state = PlayerTransitionState()
        state.progress.snapTo(1f)
        state.dragBy(-0.1f)
        val reduced = object : MotionDurationScale { override val scaleFactor = 0f }
        val frames = mutableListOf<Float>()
        withContext(RecordingFrameClock { frames += state.fraction } + reduced) { state.release(0f) }
        assertTrue(frames.all { it == 1f })
        assertEquals(1f, state.fraction, 0f)
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
