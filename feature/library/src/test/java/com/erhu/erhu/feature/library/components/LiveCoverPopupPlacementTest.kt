package com.erhu.erhu.feature.library.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveCoverPopupPlacementTest {
    private val margin = 48
    private val gap = 24

    // A 120 px button at the cover's top end, under the header, on a 1080 x 2340 window.
    private val button = IntRect(left = 912, top = 300, right = 1032, bottom = 420)
    private val window = IntSize(1080, 2340)

    @Test
    fun `opens below the button with their end edges aligned`() {
        val popup = IntSize(900, 500)

        val at = popupBesideAnchor(button, window, LayoutDirection.Ltr, popup, margin, gap)

        assertEquals(IntOffset(1032 - 900, 420 + gap), at)
    }

    @Test
    fun `on a narrow screen it stays inside the margins`() {
        val narrow = IntSize(720, 1600)
        val anchor = IntRect(600, 300, 700, 400)
        val popup = IntSize(720 - 2 * margin, 500)

        val at = popupBesideAnchor(anchor, narrow, LayoutDirection.Ltr, popup, margin, gap)

        assertEquals(margin, at.x)
    }

    @Test
    fun `opens above the button when there is no room below`() {
        val low = IntRect(912, 1900, 1032, 2020)
        val popup = IntSize(900, 500)

        val at = popupBesideAnchor(low, window, LayoutDirection.Ltr, popup, margin, gap)

        assertEquals(1900 - gap - 500, at.y)
    }

    @Test
    fun `right-to-left aligns the start edges`() {
        val rtlButton = IntRect(48, 300, 168, 420)
        val popup = IntSize(900, 500)

        val at = popupBesideAnchor(rtlButton, window, LayoutDirection.Rtl, popup, margin, gap)

        assertEquals(IntOffset(48, 420 + gap), at)
    }
}
