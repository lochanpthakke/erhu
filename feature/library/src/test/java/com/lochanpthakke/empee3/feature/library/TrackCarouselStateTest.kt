package com.lochanpthakke.empee3.feature.library

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackCarouselStateTest {
    @Test
    fun completedBatchedSwipeRetainsItsMovementBeyondSlop() {
        assertEquals(Offset(-580f, 0f), completedHorizontalSwipe(pointer(900f, true), pointer(300f, false), 20f))
    }

    @Test
    fun completedSwipeCannotStealConsumedMovement() {
        val up = pointer(300f, false).apply { consume() }
        assertNull(completedHorizontalSwipe(pointer(900f, true), up, 20f))
    }

    @Test
    fun completedSwipeIgnoresTapsAndPointersStillDown() {
        assertNull(completedHorizontalSwipe(pointer(900f, true), pointer(890f, false), 20f))
        assertNull(completedHorizontalSwipe(pointer(900f, true), pointer(300f, true), 20f))
    }

    @Test
    fun completedSwipeLeavesVerticalMovementToCollapseGesture() {
        val up = pointer(890f, false).copy(currentPosition = Offset(890f, 600f))
        assertNull(completedHorizontalSwipe(pointer(900f, true), up, 20f))
    }

    private fun pointer(x: Float, pressed: Boolean) =
        PointerInputChange(
            id = PointerId(0),
            uptimeMillis = if (pressed) 0L else 100L,
            position = Offset(x, 0f),
            pressed = pressed,
            previousUptimeMillis = 0L,
            previousPosition = Offset(900f, 0f),
            previousPressed = true,
            isInitiallyConsumed = false,
        )

    @Test
    fun trackHandoffDoesNotSubscribeCompositionToEveryMotionFrame() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { _: Any -> invalidations++ }) { state.onTrackChanged(1) }
            Snapshot.sendApplyNotifications()
            invalidations = 0
            state.startDrag()
            state.dragBy(-20f, canGoNext = true, canGoPrevious = true)
            Snapshot.sendApplyNotifications()
            assertEquals(0, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun fingerMovementIsVisibleWithoutDispatchingACoroutine() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        state.startDrag()
        state.dragBy(-37f, canGoNext = true, canGoPrevious = true)
        assertEquals(-37f, state.position, 0f)
        state.dragBy(12f, canGoNext = true, canGoPrevious = true)
        assertEquals(-25f, state.position, 0f)
    }

    @Test
    fun acknowledgedSwipeKeepsTheSpringAlreadyAimingAtItsDestination() = runTest {
        val clock = CountingFrameClock()
        val state = TrackCarouselState(CoroutineScope(backgroundScope.coroutineContext + clock)).apply { page = 100f }
        state.startDrag()
        state.release(1, -100f) {}
        runCurrent()
        val requests = clock.requests
        state.onTrackChanged(1)
        state.settleAfterTrackChange()
        runCurrent()
        assertEquals("Handoff must not cancel and restart the settling animation", requests, clock.requests)
    }

    @Test
    fun oppositePendingCommandsSettleBackOnTheOriginalPage() = runTest {
        val clock = CountingFrameClock()
        val state = TrackCarouselState(CoroutineScope(backgroundScope.coroutineContext + clock)).apply { page = 100f }
        state.skipFromButton(1) {}
        state.skipFromButton(-1) {}
        runCurrent()
        assertEquals(0f, state.offset.targetValue, 0f)
    }

    @Test
    fun coalescedTrackChangeDoesNotLeaveADelayedWatchdogCorrection() = runTest {
        val clock = CountingFrameClock()
        val state = TrackCarouselState(CoroutineScope(backgroundScope.coroutineContext + clock)).apply { page = 100f }
        state.skipFromButton(1) {}
        state.skipFromButton(1) {}
        runCurrent()
        state.onTrackChanged(0)
        state.settleAfterTrackChange()
        advanceTimeBy(950)
        runCurrent()
        val requests = clock.requests
        advanceTimeBy(150)
        runCurrent()
        assertEquals("A latest-state acknowledgement must clear stale pending skips", requests, clock.requests)
    }

    @Test
    fun buttonSkipExecutesBeforeAnimationFrames() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        val commands = mutableListOf<Int>()

        state.skipFromButton(1, commands::add)

        assertEquals(listOf(1), commands)
        assertEquals(0f, state.offset.value, 0f)
    }

    @Test
    fun committedSwipeExecutesAtRelease() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        val commands = mutableListOf<Int>()

        state.startDrag()
        state.release(-1, 0f, commands::add)

        assertEquals(listOf(-1), commands)
    }

    @Test
    fun cancelledSwipeDoesNotSkip() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        val commands = mutableListOf<Int>()

        state.startDrag()
        state.release(0, 0f, commands::add)

        assertEquals(emptyList<Int>(), commands)
    }

    @Test
    fun rapidCommandsAreNotDelayedOrCoalesced() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        val commands = mutableListOf<Int>()

        state.skipFromButton(1, commands::add)
        state.skipFromButton(1, commands::add)
        state.skipFromButton(-1, commands::add)

        assertEquals(listOf(1, 1, -1), commands)
    }

    @Test
    fun reportedTrackChangeKeepsIncomingPageAtItsDrawnPosition() = runTest {
        val state = TrackCarouselState(backgroundScope).apply { page = 100f }
        state.startDrag()
        state.dragBy(-40f, canGoNext = true, canGoPrevious = true)
        state.skipFromButton(1) {}
        val incomingPosition = state.position + state.page

        state.onTrackChanged(1)

        assertEquals(incomingPosition, state.position, 0f)
    }

    private class CountingFrameClock : MonotonicFrameClock {
        var requests = 0
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            requests++
            delay(16)
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }
}
