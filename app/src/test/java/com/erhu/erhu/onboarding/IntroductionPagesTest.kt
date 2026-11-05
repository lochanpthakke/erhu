package com.erhu.erhu.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroductionPagesTest {
    @Test fun allAvailableFeaturesHaveTwelvePagesWithBookends() {
        val pages = introductionPages(true, true)
        assertEquals(12, pages.size)
        assertEquals(IntroductionPage.WELCOME, pages.first())
        assertEquals(IntroductionPage.READY, pages.last())
        assertEquals(pages.size, pages.distinct().size)
    }
    @Test fun optionalFeaturesNeverLeaveEmptyPages() {
        for (vybe in listOf(false, true)) for (widgets in listOf(false, true)) {
            val pages = introductionPages(vybe, widgets)
            assertEquals(vybe, IntroductionPage.VYBE in pages)
            assertEquals(widgets, IntroductionPage.WIDGETS in pages)
            assertTrue(pages.filterNot { it == IntroductionPage.READY }.all { it.previews().isNotEmpty() || it.toolGuide() != null })
        }
    }
    @Test fun currentPageIdentitySurvivesOptionalPageChanges() {
        for (page in listOf(IntroductionPage.LIBRARY, IntroductionPage.ARTWORK, IntroductionPage.TAGS, IntroductionPage.BUILDER, IntroductionPage.LISTENING, IntroductionPage.TRANSITIONS, IntroductionPage.READY)) {
            for (vybe in listOf(false, true)) {
                val pages = introductionPages(vybe, true)
                assertEquals(page, pages[restoredIntroductionPage(page.name, pages)])
            }
        }
    }
    @Test fun hidingCurrentVybeAdvancesToTheNextAvailablePage() {
        val withWidgets = introductionPages(false, true)
        assertEquals(IntroductionPage.WIDGETS, withWidgets[restoredIntroductionPage("VYBE", withWidgets)])
        val withoutWidgets = introductionPages(false, false)
        assertEquals(IntroductionPage.READY, withoutWidgets[restoredIntroductionPage("VYBE", withoutWidgets)])
    }
    @Test fun unknownSavedPageReturnsToWelcome() {
        assertEquals(0, restoredIntroductionPage("unknown-old-page", introductionPages(false, false)))
    }
}
