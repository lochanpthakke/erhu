package com.erhu.erhu.feature.library

import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.SmartMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VybeVisibilityTest {
    @Test fun enabledNavigationPinsHomeAndPreservesShortcutOrder() {
        val sections = navigationQuickAccess(listOf("ARTISTS", "TRACKS", "ALBUMS", "VYBE", "LIBRARY"), true)
        assertEquals(listOf(LibrarySection.TRACKS, LibrarySection.ARTISTS, LibrarySection.ALBUMS, LibrarySection.VYBE), sections)
    }
    @Test fun hiddenVybeCompactsNavigationAndHasSafeRestoredSelection() {
        val sections = navigationQuickAccess(listOf("TRACKS", "ARTISTS", "VYBE", "ALBUMS"), false)
        assertEquals(listOf(LibrarySection.TRACKS, LibrarySection.ARTISTS, LibrarySection.ALBUMS), sections)
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
