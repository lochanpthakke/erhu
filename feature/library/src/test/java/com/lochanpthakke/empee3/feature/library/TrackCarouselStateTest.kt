package com.lochanpthakke.empee3.feature.library

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackCarouselStateTest {
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
        state.offset.snapTo(-40f)
        state.skipFromButton(1) {}
        val incomingPosition = state.position + state.page

        state.onTrackChanged(1)

        assertEquals(incomingPosition, state.position, 0f)
    }
}
