package com.erhu.erhu.feature.library

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The depth carousel's geometry: where each card stands as the strip moves and hands over. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackCarouselDepthTest {
    @Test
    fun atRest_theCurrentCardIsCentred_andTheNeighboursOnePlaceAway() =
        runTest {
            val state = TrackCarouselState(backgroundScope).apply { page = 400f }
            assertEquals(0f, state.distance(0), 0f)
            assertEquals(-1f, state.distance(-1), 0f)
            assertEquals(1f, state.distance(1), 0f)
        }

    @Test
    fun theCardsFollowTheFinger_onePagePerCard() =
        runTest {
            val state = TrackCarouselState(backgroundScope).apply { page = 400f }
            state.startDrag()
            state.dragBy(-100f, canGoNext = true, canGoPrevious = true)
            assertEquals(-0.25f, state.distance(0), 1e-6f)
            assertEquals(0.75f, state.distance(1), 1e-6f)
        }

    @Test
    fun atAQueueBoundary_theStripResists() =
        runTest {
            val state = TrackCarouselState(backgroundScope).apply { page = 400f }
            state.startDrag()
            // No next track: dragging towards it moves the cards a quarter as far.
            state.dragBy(-100f, canGoNext = false, canGoPrevious = true)
            assertEquals(-0.0625f, state.distance(0), 1e-6f)
        }

    @Test
    fun whenTheNextTrackBecomesCurrent_noCardJumps() =
        runTest {
            val state = TrackCarouselState(backgroundScope).apply { page = 400f }
            state.startDrag()
            state.dragBy(-300f, canGoNext = true, canGoPrevious = true)
            // The incoming card (slot +1) is where the finger left it...
            val before = state.distance(1)
            state.release(1, 0f) {}
            state.onTrackChanged(1)
            // ...and after the handoff the same card, now slot 0, stands at the same place.
            assertEquals(before, state.distance(0), 1e-4f)
        }

    @Test
    fun beforeMeasuring_cardsStayInTheirPlaces() =
        runTest {
            val state = TrackCarouselState(backgroundScope)
            assertEquals(0f, state.distance(0), 0f)
            assertEquals(1f, state.distance(1), 0f)
        }
}
