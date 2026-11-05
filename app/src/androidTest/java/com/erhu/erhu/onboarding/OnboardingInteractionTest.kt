package com.erhu.erhu.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Component tests use the isolated test activity and never touch playback or saved preferences. */
class OnboardingInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nextAndBackCanInterruptPageMotionWithoutCompletingSetup() {
        var exited = false
        var completed = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OnboardingScreen(true, {}, onFinish = { completed = true; true }, onExit = { exited = true })
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Next").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Next").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Give every album its cover.").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Back").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Your music. Your space.").assertIsDisplayed()
        assertEquals(false, completed)
        assertEquals(false, exited)
    }

    @Test fun swipeButtonsAndLiveVybeAvailabilityKeepPageIdentity() {
        compose.setContent {
            var vybe by remember { mutableStateOf(true) }
            OnboardingScreen(vybe, { vybe = it }, onFinish = { true }, onExit = {})
        }
        compose.onNodeWithText("Your music. Your space.").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNodeWithText("Your music library.").assertIsDisplayed()
        compose.onNodeWithText("Albums").performClick()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onNodeWithText("Your music. Your space.").assertIsDisplayed()
        repeat(introductionPages(true, true).indexOf(IntroductionPage.VYBE)) { index ->
            compose.onNodeWithText("Next").performClick()
            compose.waitForIdle()
            if (index + 1 == introductionPages(true, true).indexOf(IntroductionPage.ARTWORK)) {
                compose.onNodeWithText("Details").performScrollTo().performClick()
                compose.onNodeWithText("Album covers and artist photos").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("How to use").performScrollTo().performClick()
                compose.onNodeWithText("1. Find an album or artist").performScrollTo().assertIsDisplayed()
            }
        }
        compose.onNodeWithText("Discover VYBE.").assertIsDisplayed()
        compose.onNodeWithText("Enable VYBE").performClick()
        compose.waitForIdle()
        // The current VYBE page disappears and advances to Widgets, or Ready on unsupported hosts.
        compose.onNodeWithText("Discover VYBE.").assertDoesNotExist()
        if (compose.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Next").performClick()
        }
        compose.onNodeWithText("Everything is in place.").assertIsDisplayed()
        compose.onNodeWithText("Enable VYBE").performClick()
        compose.onNodeWithText("Everything is in place.").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
    }

    @Test fun failedSkipCanRetryAndCompletesOnlyOnce() {
        var attempts = 0
        compose.setContent {
            OnboardingScreen(false, {}, onFinish = { ++attempts > 1 }, onExit = {})
        }
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Couldn't save your setup. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.waitForIdle()
        assertEquals(2, attempts)
    }
}
