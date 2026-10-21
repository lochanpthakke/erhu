package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListState
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingHeaderStateTest {
    // A 200 px bar, a title 600 px down the hero, 100 px of collapse travel: the collapse runs
    // while the hero scrolls from 300 px (title 300 px down the screen) to 400 px (title at the bar).
    private fun state(
        index: Int,
        offset: Int,
        measured: Boolean = true,
    ) = CollapsingHeaderState(LazyListState(index, offset), travelPx = 100f).apply {
        barHeight = 200f
        if (measured) titleTop = 600f
    }

    @Test
    fun expandedAtTheTop() = assertEquals(0f, state(0, 0).progress, 0f)

    @Test
    fun staysExpandedUntilTheTitleNearsTheBar() = assertEquals(0f, state(0, 300).progress, 0f)

    @Test
    fun partialScrollHoldsTheMatchingIntermediateState() {
        assertEquals(0.25f, state(0, 325).progress, 1e-4f)
        assertEquals(0.5f, state(0, 350).progress, 1e-4f)
    }

    @Test
    fun reversingDirectionRetracesTheSameValues() {
        val down = listOf(310, 340, 370).map { state(0, it).progress }
        val up = listOf(370, 340, 310).map { state(0, it).progress }
        assertEquals(down, up.reversed())
    }

    @Test
    fun collapsedOnceTheTitleReachesTheBar() = assertEquals(1f, state(0, 400).progress, 0f)

    @Test
    fun collapsedFurtherDown() = assertEquals(1f, state(0, 900).progress, 0f)

    @Test
    fun collapsedWhenTheHeroHasScrolledAway() = assertEquals(1f, state(3, 0).progress, 0f)

    @Test
    fun expandedBeforeTheTitleHasBeenMeasured() = assertEquals(0f, state(0, 350, measured = false).progress, 0f)
}
