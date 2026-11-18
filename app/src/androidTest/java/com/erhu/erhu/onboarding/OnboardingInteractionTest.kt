package com.erhu.erhu.onboarding

import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.audioPermission
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class OnboardingInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deniedFullOnboardingCannotSkipOrSwipePastWelcome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ContextCompat.checkSelfPermission(context, audioPermission()) != PackageManager.PERMISSION_GRANTED)
        var completed = false
        compose.setContent {
            OnboardingScreen(
                vybeEnabled = false, onVybeEnabledChange = {}, onExit = {},
                glassEffectEnabled = true, onGlassEffectEnabledChange = {},
                onConfirmShortcuts = { completed = true; true },
            )
        }
        compose.onNodeWithText("Skip").assertIsNotEnabled()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Your music. Your space.").assertIsDisplayed()
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithTag("recording-NOW_PLAYING").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Your music. Your space.").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        assertFalse(completed)
    }

    @Test fun manualCarouselSelectionRestartsTimer() {
        compose.mainClock.autoAdvance = false
        compose.setContent { UtilityPreviewCarousel(active = true, motionEnabled = true) }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithContentDescription("Show preview 2 of 3").performClick()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("recording-TAGS").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithTag("recording-BUILDER").assertIsDisplayed()
    }

    @Test fun inactiveCarouselStopsAndRestartsWithFreshDelay() {
        var active by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { UtilityPreviewCarousel(active = active, motionEnabled = true) }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle { active = false }
        compose.mainClock.advanceTimeBy(10000)
        compose.onNodeWithTag("recording-ARTWORK").assertIsDisplayed()
        compose.runOnIdle { active = true }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("recording-ARTWORK").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2200)
        compose.onNodeWithTag("recording-TAGS").assertIsDisplayed()
    }

    @Test fun reducedMotionKeepsManualSelectionWithoutAutoAdvance() {
        compose.mainClock.autoAdvance = false
        compose.setContent { UtilityPreviewCarousel(active = true, motionEnabled = false) }
        compose.mainClock.advanceTimeBy(20000)
        compose.onNodeWithTag("recording-ARTWORK").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show preview 3 of 3").performClick()
        compose.mainClock.advanceTimeBy(20000)
        compose.onNodeWithTag("recording-BUILDER").assertIsDisplayed()
    }
}
