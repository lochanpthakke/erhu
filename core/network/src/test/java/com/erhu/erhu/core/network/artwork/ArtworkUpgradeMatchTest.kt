package com.erhu.erhu.core.network.artwork

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUpgradeMatchTest {
    @Test fun onlyMeaningfullyLargerImagesWithTheSameAspectQualify() {
        assertTrue(ArtworkUpgradeMatch.isLarger(1200, 1200, 300, 300))
        assertTrue(ArtworkUpgradeMatch.isLarger(1000, 1500, 200, 300))
        assertFalse(ArtworkUpgradeMatch.isLarger(320, 320, 300, 300))
        assertFalse(ArtworkUpgradeMatch.isLarger(300, 300, 300, 300))
        assertFalse(ArtworkUpgradeMatch.isLarger(100, 100, 300, 300))
        assertFalse(ArtworkUpgradeMatch.isLarger(600, 1000, 100, 100))
        assertFalse(ArtworkUpgradeMatch.isLarger(0, 1000, 100, 100))
    }

    @Test fun finalSaveLimitCannotUndoTheOfferedUpgrade() {
        assertFalse(ArtworkUpgradeMatch.isLarger(3000, 3000, 1400, 1400))
        assertTrue(ArtworkUpgradeMatch.isLarger(3000, 3000, 1000, 1000))
    }

    @Test fun spatialColoursRejectDifferentPortraitsAndSimilarSilhouettes() {
        val a = image()
        assertTrue(ArtworkUpgradeMatch.resembles(a, a.copyOf()))
        assertTrue(ArtworkUpgradeMatch.resembles(a, image(delta = 3)))
        assertFalse(ArtworkUpgradeMatch.resembles(a, image(moved = true)))
        assertFalse(ArtworkUpgradeMatch.resembles(a, IntArray(256) { 0xffdd5522.toInt() }))
        assertFalse(ArtworkUpgradeMatch.resembles(a, IntArray(0)))
    }

    private fun image(moved: Boolean = false, delta: Int = 0): IntArray = IntArray(256) { index ->
        val x = index % 16
        val y = index / 16
        if (x in (if (moved) 10..14 else 2..6) && y in 3..12) 0xffff9900.toInt() + delta else 0xff0033aa.toInt() + delta
    }
}
