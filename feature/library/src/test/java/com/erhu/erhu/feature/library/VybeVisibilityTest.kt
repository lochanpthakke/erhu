package com.erhu.erhu.feature.library

import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.SmartMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VybeVisibilityTest {
    @Test fun enabledNavigationKeepsAllExistingOrders() {
        for (start in LibrarySection.entries) {
            val sections = getOrderedSections(start, true)
            assertEquals(start, sections.first())
            assertEquals(4, sections.size)
            assertEquals(LibrarySection.entries.toSet(), sections.toSet())
        }
    }
    @Test fun hiddenNavigationHasThreeSlotsAndSafeRestoredSelection() {
        for (start in LibrarySection.entries) {
            val sections = getOrderedSections(start, false)
            assertEquals(3, sections.size)
            assertFalse(LibrarySection.VYBE in sections)
            assertTrue(start.visibleWithVybe(false) in sections)
        }
        assertEquals(LibrarySection.TRACKS, LibrarySection.VYBE.visibleWithVybe(false))
        assertEquals(LibrarySection.VYBE, LibrarySection.VYBE.visibleWithVybe(true))
    }
    @Test fun disablingOnlyRemovesVybeRecommendationsAndReenablingRestoresThem() {
        val all = MixKind.entries.map { SmartMix(it, listOf(8), it.title) }
        assertEquals(all, visibleHomeMixes(all, true))
        val hidden = visibleHomeMixes(all, false)
        assertFalse(hidden.any { isVybeMix(it.kind.key) })
        assertEquals(all.filterNot { it.kind in setOf(MixKind.YOUR_SOUND, MixKind.A_LITTLE_DIFFERENT) }, hidden)
        assertEquals(all, visibleHomeMixes(all, true))
    }
}
