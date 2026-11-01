package com.erhu.erhu.core.designsystem.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupMorphTest {
    @Test
    fun anchoredMorphStartsAtActualTriggerOutsideFinalMenuBounds() {
        val trigger = Rect(300f, 32f, 348f, 80f)
        val panel = Rect(28f, 84f, 348f, 600f)
        assertEquals(trigger, menuMorphRect(trigger, panel, 0f))
        assertEquals(panel, menuMorphRect(trigger, panel, 1f))
        val middle = menuMorphRect(trigger, panel, 0.5f)
        assertEquals(164f, middle.left, 0.001f)
        assertEquals(58f, middle.top, 0.001f)
        assertEquals(348f, middle.right, 0.001f)
        assertEquals(340f, middle.bottom, 0.001f)
        assertEquals(trigger, menuMorphRect(trigger, panel, -0.1f))
        assertEquals(panel, menuMorphRect(trigger, panel, 1.02f))
    }

    @Test
    fun compactShapeStartsAtTriggerAndExpandsWithoutChangingContentSize() {
        val size = Size(300f, 600f)
        for (origin in listOf(TransformOrigin(0f, 0f), TransformOrigin(1f, 0f), TransformOrigin(1f, 1f), TransformOrigin.Center)) {
            val compact = popupMorphBounds(size, origin, 0f, 44f)
            assertEquals(44f, compact.width, 0.001f)
            assertEquals(44f, compact.height, 0.001f)
            assertEquals((size.width - 44f) * origin.pivotFractionX, compact.left, 0.001f)
            assertEquals(Rect(0f, 0f, 300f, 600f), popupMorphBounds(size, origin, 1f, 44f))
            var previous = compact
            for (step in 1..10) {
                val current = popupMorphBounds(size, origin, step / 10f, 44f)
                assertTrue(current.width >= previous.width && current.height >= previous.height)
                assertTrue(current.left >= 0f && current.top >= 0f && current.right <= size.width && current.bottom <= size.height)
                previous = current
            }
        }
    }

    @Test
    fun overshootAndTinyWindowsKeepTheApertureInsideThePopup() {
        for (size in listOf(Size.Zero, Size(20f, 30f), Size(300f, 600f))) {
            for (progress in listOf(-0.1f, 0f, 0.5f, 1f, 1.1f)) {
                val rect = popupMorphBounds(size, TransformOrigin(1f, 0f), progress, 44f)
                assertTrue(rect.width in 0f..size.width && rect.height in 0f..size.height)
            }
        }
    }
}
