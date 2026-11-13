package com.erhu.erhu.feature.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCardMotionTest {
    private val viewport = Rect(0f, 200f, 1080f, 1500f)
    private val artwork = Rect(120f, 300f, 960f, 1140f)
    private val thumbnail = Rect(60f, 1920f, 192f, 2052f)

    @Test
    fun collapsedArtworkExactlyMatchesTheMeasuredThumbnail() {
        val transform = playerCardTransform(0f, viewport, artwork, thumbnail)
        assertPoint(thumbnail.topLeft, mapped(artwork.topLeft, transform))
        assertPoint(thumbnail.bottomRight, mapped(artwork.bottomRight, transform))
    }

    @Test
    fun expandedTransformIsExactlyIdentity() {
        val transform = playerCardTransform(1f, viewport, artwork, thumbnail)
        assertEquals(1f, transform.scale, 0f)
        assertPoint(Offset.Zero, transform.translation)
    }

    @Test
    fun everyPartOfTheCardUsesTheSameUniformTransform() {
        val title = Offset(120f, 1200f)
        for (step in 0..100) {
            val transform = playerCardTransform(step / 100f, viewport, artwork, thumbnail)
            val separation = mapped(title, transform) - mapped(artwork.topLeft, transform)
            assertPoint((title - artwork.topLeft) * transform.scale, separation)
        }
    }

    @Test
    fun neighbourRevealIsBoundedMonotonicAndReversible() {
        val values = (0..100).map { playerNeighbourReveal(it / 100f) }
        assertEquals(0f, values.first(), 0f)
        assertEquals(1f, values.last(), 0f)
        assertTrue(values.all { it in 0f..1f })
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(values.reversed(), (100 downTo 0).map { playerNeighbourReveal(it / 100f) })
    }

    @Test
    fun collapsedArtworkMotionMatchesThumbnail() {
        val motion = playerArtworkMotion(0f, artwork, thumbnail, 24f, 48f)
        assertEquals(thumbnail.width / artwork.width, motion.scale, 0.0001f)
        assertPoint(thumbnail.center, artwork.center + motion.translation)
        assertEquals(24f, motion.cornerRadiusPx, 0.0001f)
    }

    @Test
    fun expandedArtworkMotionIsIdentity() {
        val motion = playerArtworkMotion(1f, artwork, thumbnail, 24f, 48f)
        assertEquals(1f, motion.scale, 0f)
        assertPoint(Offset.Zero, motion.translation)
        assertEquals(48f, motion.cornerRadiusPx, 0f)
    }

    @Test
    fun artworkMotionPreservesStrictSquareAspectAndCenterInterpolation() {
        for (step in 0..100) {
            val p = step / 100f
            val motion = playerArtworkMotion(p, artwork, thumbnail, 24f, 48f)
            val expectedCenter = androidx.compose.ui.geometry.lerp(thumbnail.center, artwork.center, p)
            assertPoint(expectedCenter, artwork.center + motion.translation)
            val expectedSide = androidx.compose.ui.util.lerp(thumbnail.width, artwork.width, p)
            assertEquals(expectedSide, artwork.width * motion.scale, 0.001f)
            assertEquals(expectedSide, artwork.height * motion.scale, 0.001f)
        }
    }

    private fun mapped(point: Offset, transform: PlayerCardTransform): Offset =
        viewport.topLeft + transform.translation + (point - viewport.topLeft) * transform.scale

    private fun assertPoint(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
