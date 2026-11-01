package com.erhu.erhu.core.designsystem.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleSlotsTest {
    // Four 200 px cells, 150 px tall; content 80 px wide (one wider label: 170 px).
    private fun slots(wideIndex: Int = -1) =
        CapsuleSlots(4).apply {
            repeat(4) { i ->
                cell(i, Rect(i * 200f, 0f, i * 200f + 200f, 150f))
                content(i, IntSize(if (i == wideIndex) 170 else 80, 100))
            }
        }

    @Test
    fun capsuleWrapsTheContentCentredInItsCell() {
        val r = slots().capsuleAt(1, padding = 20f, inset = 5f)
        assertEquals(Rect(240f, 5f, 360f, 145f), r)
    }

    @Test
    fun aLongLabelWidensTheCapsuleButNeverPastItsCell() {
        val r = slots(wideIndex = 2).capsuleAt(2, padding = 20f, inset = 5f)
        assertEquals(190f, r.width, 0f)
        assertTrue(r.left >= 400f && r.right <= 600f)
    }

    @Test
    fun betweenTwoItemsTheCapsuleBlendsTheirPlaceAndWidth() {
        val s = slots(wideIndex = 1)
        val a = s.capsuleAt(0, 20f, 5f)
        val b = s.capsuleAt(1, 20f, 5f)
        val mid = s.capsuleBetween(0.5f, 20f, 5f)!!
        assertEquals((a.center.x + b.center.x) / 2f, mid.center.x, 1e-3f)
        assertEquals((a.width + b.width) / 2f, mid.width, 1e-3f)
        assertEquals(s.capsuleAt(3, 20f, 5f), s.capsuleBetween(3f, 20f, 5f))
    }

    @Test
    fun pastTheEndsTheMotionCarriesOnALittle() {
        val s = slots()
        assertTrue(s.capsuleBetween(-0.1f, 20f, 5f)!!.center.x < s.capsuleAt(0, 20f, 5f).center.x)
        assertTrue(s.capsuleBetween(3.1f, 20f, 5f)!!.center.x > s.capsuleAt(3, 20f, 5f).center.x)
    }

    @Test
    fun noCapsuleUntilEveryItemHasBeenMeasured() {
        val s = CapsuleSlots(2).apply { cell(0, Rect(0f, 0f, 100f, 50f)) }
        assertNull(s.capsuleBetween(0f, 10f, 2f))
    }

    @Test
    fun stepIsTheDistanceBetweenNeighbouringItems() = assertEquals(200f, slots().step, 0f)

    @Test
    fun emphasisIsFullUnderTheCapsuleAndGoneOneItemAway() {
        assertEquals(1f, capsuleEmphasis(2f, 2), 0f)
        assertEquals(0.5f, capsuleEmphasis(1.5f, 2), 1e-6f)
        assertEquals(0f, capsuleEmphasis(1f, 2), 0f)
        assertEquals(0f, capsuleEmphasis(0f, 2), 0f)
    }
}
