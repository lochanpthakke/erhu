package com.erhu.erhu.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroductionPagesTest {
    @Test fun allAvailableFeaturesHaveThirteenPagesWithBookends() {
        val pages = introductionPages(true, true)
        assertEquals(13, pages.size)
        assertEquals(IntroductionPage.LANGUAGE, pages.first())
        assertEquals(IntroductionPage.READY, pages.last())
        assertEquals(pages.size, pages.distinct().size)
    }
    @Test fun optionalFeaturesNeverLeaveEmptyPages() {
        for (vybe in listOf(false, true)) for (widgets in listOf(false, true)) {
            val pages = introductionPages(vybe, widgets)
            assertEquals(vybe, IntroductionPage.VYBE in pages)
            assertEquals(widgets, IntroductionPage.WIDGETS in pages)
            val contentPages = pages.filterNot {
                it == IntroductionPage.READY || it == IntroductionPage.LANGUAGE
            }
            assertTrue(contentPages.all { it.previews().isNotEmpty() || it.toolGuide() != null })
        }
    }
    @Test fun currentPageIdentitySurvivesOptionalPageChanges() {
        val testPages = listOf(
            IntroductionPage.LANGUAGE,
            IntroductionPage.WELCOME,
            IntroductionPage.LIBRARY,
            IntroductionPage.ARTWORK,
            IntroductionPage.TAGS,
            IntroductionPage.BUILDER,
            IntroductionPage.LISTENING,
            IntroductionPage.TRANSITIONS,
            IntroductionPage.READY,
        )
        for (page in testPages) {
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
    @Test fun unknownSavedPageReturnsToFirstPage() {
        assertEquals(0, restoredIntroductionPage("unknown-old-page", introductionPages(false, false)))
    }
}
