package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.erhu.erhu.core.designsystem.foundation.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuTest {
    @Test
    fun anchoredMorphSettlesQuicklyWithoutLargeOvershoot() {
        val animation = TargetBasedAnimation(Motion.responsive(visibilityThreshold = 0.001f), Float.VectorConverter, 0f, 1f)
        val values = (0L..1000L step 8).map { animation.getValueFromNanos(it * 1_000_000) }
        assertTrue(values.all { it in 0f..1.01f })
        assertTrue(animation.isFinishedFromNanos(500_000_000))
        assertTrue(animation.isFinishedFromNanos(1_000_000_000))
        assertEquals(1f, values.last(), 0.001f)
    }

    @Test
    fun trailingEdgeFollowsLayoutDirection() {
        val anchor = IntRect(300, 40, 348, 88)
        assertEquals(IntOffset(108, 92), position(anchor, LayoutDirection.Ltr))
        assertEquals(IntOffset(300, 92), position(anchor, LayoutDirection.Rtl))
    }

    @Test
    fun bottomTriggerOpensAboveAndPivotsFromTrigger() {
        var pivot = TransformOrigin.Center
        val provider = GlassMenuPositionProvider(12, 4) { pivot = it }
        val result = provider.calculatePosition(IntRect(540, 720, 588, 768), IntSize(600, 800), LayoutDirection.Ltr, IntSize(240, 300))
        assertEquals(IntOffset(348, 416), result)
        assertEquals(1f, pivot.pivotFractionY)
        assertTrue(pivot.pivotFractionX in 0f..1f)
    }

    @Test
    fun shadowPaddingDoesNotMoveTheSurfacePivotAwayFromTheTrigger() {
        var pivot = TransformOrigin.Center
        val provider = GlassMenuPositionProvider(12, 4, contentInset = 8) { pivot = it }
        provider.calculatePosition(IntRect(300, 40, 348, 88), IntSize(600, 800), LayoutDirection.Ltr, IntSize(256, 316))
        assertEquals(224f / 240f, pivot.pivotFractionX, 0.001f)
        assertEquals(0f, pivot.pivotFractionY)
    }

    @Test
    fun anchorsAtEveryEdgeRemainInsideSmallAndLargeWindows() {
        for (window in listOf(IntSize(280, 320), IntSize(600, 800), IntSize(1200, 600))) {
            val sizes = listOf(200, 240).flatMap { width -> listOf(100, window.height - 24).map { IntSize(width, it) } }
            for (size in sizes) {
                for (direction in LayoutDirection.entries) {
                    assertAnchorsInside(window, size, direction)
                }
            }
        }
    }

    private fun assertAnchorsInside(
        window: IntSize,
        menu: IntSize,
        direction: LayoutDirection,
    ) {
        val anchors =
            listOf(-20, 0, window.width / 2, window.width - 20).flatMap { x ->
                listOf(-20, 0, window.height / 2, window.height - 20).map { y -> IntRect(x, y, x + 48, y + 48) }
            }
        for (anchor in anchors) {
            val result = GlassMenuPositionProvider(12, 4).calculatePosition(anchor, window, direction, menu)
            assertTrue(result.x >= 12 && result.x + menu.width <= window.width - 12)
            assertTrue(result.y >= 12 && result.y + menu.height <= window.height - 12)
        }
    }

    @Test
    fun tinyWindowAndUnmeasuredContentDoNotThrow() {
        val provider = GlassMenuPositionProvider(12, 4)
        assertEquals(IntOffset.Zero, provider.calculatePosition(IntRect.Zero, IntSize(10, 10), LayoutDirection.Ltr, IntSize(240, 300)))
        provider.calculatePosition(IntRect.Zero, IntSize.Zero, LayoutDirection.Rtl, IntSize.Zero)
    }

    private fun position(
        anchor: IntRect,
        direction: LayoutDirection,
    ): IntOffset = GlassMenuPositionProvider(12, 4).calculatePosition(anchor, IntSize(600, 800), direction, IntSize(240, 300))

}
