package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.erhu.erhu.core.designsystem.foundation.Motion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test
    fun albumPilotHasOneRestrainedOvershootAndSettlesAtNormalFrameRates() {
        val animation = TargetBasedAnimation(Motion.albumMenuReveal(0.001f), Float.VectorConverter, 0f, 1f)
        for (frameMillis in listOf(8L, 16L, 33L)) {
            val scales = (0L..1000L step frameMillis).map {
                albumMenuSurfaceScale(animation.getValueFromNanos(it * 1_000_000))
            }
            assertTrue("No visible spring-back", scales.max() > 1.002f)
            assertTrue(scales.all { it in 1f..1.012f })
            val overshoots = scales.zipWithNext().count { (a, b) -> a == 1f && b > 1f }
            assertEquals("Repeated bouncing", 1, overshoots)
        }
        assertTrue(animation.isFinishedFromNanos(600_000_000))
        assertEquals(1f, albumMenuSurfaceScale(animation.getValueFromNanos(1_000_000_000)), 0f)
    }

    @Test
    fun albumPilotKeepsApertureBoundsAndSafetyInsetsDuringOvershoot() {
        for (panel in listOf(Rect(16f, 16f, 256f, 616f), Rect(16f, 16f, 336f, 116f))) {
            val viewport = panel.inflate(16f)
            for (anchor in listOf(panel.topLeft, panel.topRight, panel.bottomLeft, panel.bottomRight)) {
                for (progress in listOf(-0.1f, 0f, 0.5f, 1f, 1.02f, 1.5f)) {
                    val scale = albumMenuSurfaceScale(progress)
                    val left = anchor.x + (panel.left - anchor.x) * scale
                    val top = anchor.y + (panel.top - anchor.y) * scale
                    val right = anchor.x + (panel.right - anchor.x) * scale
                    val bottom = anchor.y + (panel.bottom - anchor.y) * scale
                    assertTrue(left >= viewport.left && top >= viewport.top)
                    assertTrue(right <= viewport.right && bottom <= viewport.bottom)
                    assertEquals(panel, menuMorphRect(panel, panel, progress))
                }
            }
        }
        assertEquals(1f, albumMenuSurfaceScale(0f), 0f)
        assertEquals(1f, albumMenuSurfaceScale(1f), 0f)
    }

    @Test
    fun albumPilotCanReverseAtItsPeakWithoutAScaleJump() {
        val reveal = TargetBasedAnimation(Motion.albumMenuReveal(0.001f), Float.VectorConverter, 0f, 1f)
        val peakTime = (0L..600L step 4).maxBy { reveal.getValueFromNanos(it * 1_000_000) } * 1_000_000
        val value = reveal.getValueFromNanos(peakTime)
        val close = TargetBasedAnimation(Motion.fade<Float>(150), Float.VectorConverter, value, 0f, reveal.getVelocityVectorFromNanos(peakTime).value)
        assertEquals(albumMenuSurfaceScale(value), albumMenuSurfaceScale(close.getValueFromNanos(0)), 0f)
        val reopening = TargetBasedAnimation(Motion.albumMenuReveal(0.001f), Float.VectorConverter, close.getValueFromNanos(40_000_000), 1f)
        assertEquals(albumMenuSurfaceScale(close.getValueFromNanos(40_000_000)), albumMenuSurfaceScale(reopening.getValueFromNanos(0)), 0f)
        assertEquals(1f, albumMenuSurfaceScale(reopening.getValueFromNanos(1_000_000_000)), 0f)
    }

    @Test
    fun albumPilotRapidRetargetingAndCancellationReturnToExactRest() {
        val progress = Animatable(0f)
        val samples = mutableListOf<Float>()
        runBlocking(MenuFrameClock { samples += albumMenuSurfaceScale(progress.value) }) {
            repeat(20) { index ->
                val target = if (index % 2 == 0) 1f else 0f
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    progress.animateTo(target, if (target == 1f) Motion.albumMenuReveal(0.001f) else Motion.fade(150))
                }
                repeat(4) { yield() }
                job.cancelAndJoin()
            }
            progress.animateTo(0f, Motion.fade(150))
        }
        assertTrue(samples.all { it in 1f..1.012f })
        assertEquals(0f, progress.value, 0f)
        assertEquals(1f, albumMenuSurfaceScale(progress.value), 0f)
    }

    @Test
    fun albumPilotHonoursZeroSystemAnimationScale() {
        val progress = Animatable(0f)
        val samples = mutableListOf<Float>()
        val reduced = object : MotionDurationScale { override val scaleFactor = 0f }
        runBlocking(MenuFrameClock { samples += albumMenuSurfaceScale(progress.value) } + reduced) {
            progress.animateTo(1f, Motion.albumMenuReveal(0.001f))
            progress.animateTo(0f, Motion.fade(150))
        }
        assertTrue(samples.all { it == 1f })
        assertEquals(0f, progress.value, 0f)
    }

    private class MenuFrameClock(private val record: () -> Unit) : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            nanos += 16_000_000L
            return onFrame(nanos).also { record() }
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
