package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionMotionTest {
    @Test
    fun aTapReleasedBeforeAnyFrameStillCompressesAndSpringsBack() {
        val state = PressMotionState(0.94f)
        val frames = mutableListOf<Float>()
        runBlocking(FrameClock { frames += state.scale.value }) { state.release() }
        assertTrue(frames.min() < 0.99f)
        assertTrue(frames.max() > 1f)
        assertEquals(1f, state.scale.value, 0f)
    }

    @Test
    fun cancelledPressReturnsToExactRestWithoutReleaseKick() {
        val state = PressMotionState(0.94f)
        runBlocking(FrameClock()) {
            state.press()
            state.cancel()
        }
        assertEquals(1f, state.scale.value, 0f)
    }

    @Test
    fun unchangedStateNeverStartsAJiggle() {
        val state = SelectionMotionState(false)
        var frames = 0
        runBlocking(FrameClock { frames++ }) { state.update(false) }
        assertEquals(0, frames)
        assertEquals(0f, state.displacement.value, 0f)
    }

    @Test
    fun confirmedStateChangeJigglesBothWaysThenRestsExactly() {
        val state = SelectionMotionState(false)
        val frames = mutableListOf<Float>()
        runBlocking(FrameClock { frames += state.displacement.value }) { state.update(true) }
        assertTrue(frames.any { it > 0f })
        assertTrue(frames.any { it < 0f })
        assertTrue(frames.all { it in -1f..1f })
        assertEquals(0f, state.displacement.value, 0f)
    }

    @Test
    fun kickWhenPopsOnlyOnTheChosenChange() {
        // A navigation icon: pops as it becomes selected, stays still as it is deselected.
        val state = SelectionMotionState(false)
        val becomesSelected: (Any?) -> Boolean = { it == true }
        val frames = mutableListOf<Float>()
        runBlocking(FrameClock { frames += state.displacement.value }) { state.update(true, becomesSelected) }
        assertTrue(frames.any { it != 0f })
        assertEquals(0f, state.displacement.value, 0f)
        frames.clear()
        runBlocking(FrameClock { frames += state.displacement.value }) { state.update(false, becomesSelected) }
        assertTrue("Deselection moved the icon", frames.all { it == 0f })
        assertEquals(0f, state.displacement.value, 0f)
    }

    @Test
    fun rapidStateReversalDoesNotAccumulateRotation() {
        val state = SelectionMotionState(false)
        runBlocking(FrameClock()) {
            val first = launch(start = CoroutineStart.UNDISPATCHED) { state.update(true) }
            repeat(6) { yield() }
            state.update(false)
            first.join()
        }
        assertEquals(0f, state.displacement.value, 0f)
    }

    @Test
    fun repeatedTapsKeepTheJiggleWithinItsDesignedAmplitude() {
        val state = SelectionMotionState(false)
        val frames = mutableListOf<Float>()
        runBlocking(FrameClock { frames += state.displacement.value }) {
            repeat(20) { index ->
                val pulse = launch(start = CoroutineStart.UNDISPATCHED) { state.update(index % 2 == 0) }
                repeat(3) { yield() }
                pulse.cancelAndJoin()
            }
            state.update(true)
        }
        assertTrue("Repeated taps amplified the jiggle: ${frames.max()}", frames.all { it in -1f..1f })
        assertEquals(0f, state.displacement.value, 0f)
    }

    @Test
    fun zeroDurationScaleSkipsDecorativeMotion() {
        val press = PressMotionState(0.94f)
        val selection = SelectionMotionState(false)
        val scale = object : MotionDurationScale {
            override val scaleFactor = 0f
        }
        val frames = mutableListOf<Float>()
        runBlocking(FrameClock { frames += selection.displacement.value } + scale) {
            press.release()
            selection.update(true)
        }
        assertEquals(1f, press.scale.value, 0f)
        assertTrue(frames.all { it == 0f })
        assertEquals(0f, selection.displacement.value, 0f)
    }

    @Test
    fun disablingDuringCompressionRestoresExactScale() {
        val state = PressMotionState(0.94f)
        runBlocking(FrameClock()) {
            state.press()
            state.reset()
        }
        assertEquals(1f, state.scale.value, 0f)
    }

    private class FrameClock(private val record: () -> Unit = {}) : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            nanos += 16_000_000L
            return onFrame(nanos).also { record() }
        }
    }
}
