package com.erhu.erhu.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntroductionPagesTest {
    @Test fun fullOnboardingAlwaysOffersEightPagesIncludingVybeChoice() {
        val expected = listOf(
            IntroductionPage.LANGUAGE, IntroductionPage.WELCOME, IntroductionPage.ARTWORK,
            IntroductionPage.LYRICS, IntroductionPage.SOUND, IntroductionPage.VYBE,
            IntroductionPage.QUICK_ACCESS, IntroductionPage.READY,
        )
        for (enabled in listOf(false, true)) {
            assertEquals(expected, introductionPages(enabled, initialSetup = true))
        }
    }

    @Test fun everyLegacyIdentityMapsToItsCombinedPage() {
        val expected = mapOf(
            "LIBRARY" to IntroductionPage.WELCOME,
            "LISTENING" to IntroductionPage.WELCOME,
            "ARTWORK" to IntroductionPage.ARTWORK,
            "TAGS" to IntroductionPage.ARTWORK,
            "BUILDER" to IntroductionPage.ARTWORK,
            "TRANSITIONS" to IntroductionPage.SOUND,
            "WIDGETS" to IntroductionPage.QUICK_ACCESS,
        )
        val pages = introductionPages(false, initialSetup = true)
        expected.forEach { (old, target) -> assertEquals(target, pages[restoredIntroductionPage(old, pages)]) }
    }

    @Test fun currentIdentitiesSurviveRestoration() {
        val pages = introductionPages(false, initialSetup = true)
        pages.forEach { assertEquals(it, pages[restoredIntroductionPage(it.name, pages)]) }
    }

    @Test fun replayWithoutVybeAdvancesToNavigation() {
        val pages = introductionPages(false)
        assertFalse(IntroductionPage.VYBE in pages)
        assertEquals(IntroductionPage.QUICK_ACCESS, pages[restoredIntroductionPage("VYBE", pages)])
    }

    @Test fun upgradeOnlyRestorationStaysOnPicker() {
        val pages = listOf(IntroductionPage.QUICK_ACCESS)
        for (saved in listOf("LANGUAGE", "READY", "WIDGETS", "QUICK_ACCESS", "unknown")) {
            assertEquals(0, restoredIntroductionPage(saved, pages))
        }
    }

    @Test fun unknownSavedIdentityStartsAtLanguage() {
        assertEquals(0, restoredIntroductionPage("unknown", introductionPages(false, initialSetup = true)))
    }
}
