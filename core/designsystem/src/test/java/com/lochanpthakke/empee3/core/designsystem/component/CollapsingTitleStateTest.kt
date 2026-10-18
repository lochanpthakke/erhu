package com.lochanpthakke.empee3.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsingTitleStateTest {
    private val title = CollapsingTitleState(distancePx = 200f)

    @Test
    fun inPlaceAtTheTopAndWithNoListReporting() {
        assertEquals(0f, title.progress, 0f)
        title.attach("list") { title.fromFirstItem(index = 0, offset = 0) }
        assertEquals(0f, title.progress, 0f)
    }

    @Test
    fun partialScrollHoldsAPartialMergeAndReversingRetracesIt() {
        var offset = 50
        title.attach("list") { title.fromFirstItem(0, offset) }
        assertEquals(0.25f, title.progress, 1e-6f)
        offset = 150
        assertEquals(0.75f, title.progress, 1e-6f)
        offset = 50
        assertEquals(0.25f, title.progress, 1e-6f)
    }

    @Test
    fun goneOnceScrolledFarEnoughOrPastTheFirstItem() {
        var index = 0
        var offset = 400
        title.attach("grid") { title.fromFirstItem(index, offset) }
        assertEquals(1f, title.progress, 0f)
        index = 3
        offset = 0
        assertEquals(1f, title.progress, 0f)
    }

    @Test
    fun theNewestShownListDrivesItAndAnOldOneLeavingDoesNotClearIt() {
        title.attach("tracks") { 1f }
        title.attach("albums") { 0.5f }
        title.detach("tracks")
        assertEquals(0.5f, title.progress, 0f)
        title.detach("albums")
        assertEquals(0f, title.progress, 0f)
    }

    @Test
    fun theButtonsPulseIsAtRestAtBothEnds() {
        assertEquals(1f, mergePulse(0f), 0f)
        assertEquals(1f, mergePulse(1f), 1e-6f)
        assertTrue(mergePulse(0.8f) > 1f)
    }
}
