package com.lochanpthakke.empee3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuAnchor
import com.lochanpthakke.empee3.core.designsystem.component.glassMenuTrigger
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuConfirmation
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuDivider
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuSection
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs with -PbenchApp in the isolated package; no media library, playback or settings access. */
class GlassMenuInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private var selections = 0

    @Test
    fun repeatedOpenBackAndOutsideDismissal() {
        fixture()
        repeat(3) {
            open()
            compose.onNodeWithText("Music information").assertIsDisplayed()
            Espresso.pressBack()
            compose.waitForIdle()
            compose.onNodeWithText("Music information").assertDoesNotExist()
        }
        open()
        compose.onNode(isPopup()).performTouchInput { click(Offset(4f, height - 4f)) }
        compose.waitForIdle()
        compose.onNodeWithText("Music information").assertDoesNotExist()
        assertEquals(0, selections)
    }

    @Test
    fun disabledAndRapidSelectionDoNotRepeatActions() {
        fixture()
        open()
        compose.onNodeWithText("Unavailable").assertIsNotEnabled()
        compose.onNodeWithText("Music information").performTouchInput {
            click()
            advanceEventTime(16)
            click()
        }
        compose.waitForIdle()
        assertEquals(1, selections)
        compose.onNodeWithText("Music information").assertDoesNotExist()
        open()
        compose.onNodeWithText("Music information").performClick()
        compose.waitForIdle()
        assertEquals(2, selections)
    }

    @Test
    fun longMenuScrollsAndConfirmationCanCancelOrConfirm() {
        fixture(long = true)
        open()
        compose.onNodeWithText("Remove test item").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, selections)
        open()
        compose.onNodeWithText("Remove test item").performScrollTo().performClick()
        compose.onNodeWithText("Confirm removal").performClick()
        compose.waitForIdle()
        assertEquals(1, selections)
    }

    @Test
    fun lightGlassAtBottomEdgeIsUsable() {
        fixture(light = true, bottom = true)
        open()
        compose.onNodeWithText("Music information").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, selections)
    }

    @Test
    fun rtlSolidMenuIsUsable() {
        fixture(rtl = true, glass = false)
        open()
        compose.onNodeWithText("Music information").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, selections)
    }

    @Test
    fun multiSelectionMenuStaysOpenAndAllowsRepeatedSelections() {
        fixture(dismissOnSelection = false)
        open()
        compose.onNodeWithText("Music information").performClick()
        compose.onNodeWithText("Music information").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(2, selections)
    }

    @Test
    fun darkClearGlassRendersNeutralWithReadableContrast() {
        fixture()
        open()
        assertNeutralGlass(dark = true)
    }

    @Test
    fun lightClearGlassRendersNeutralWithReadableContrast() {
        fixture(light = true)
        open()
        assertNeutralGlass(dark = false)
    }

    private fun assertNeutralGlass(dark: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        compose.waitUntil(5000) {
            findInformationNode(automation.rootInActiveWindow) != null
        }
        val root = automation.rootInActiveWindow
        assertEquals(instrumentation.targetContext.packageName, root.packageName.toString())
        var item = checkNotNull(findInformationNode(root))
        while (!item.isClickable && item.parent != null) item = item.parent
        val bounds = android.graphics.Rect()
        item.getBoundsInScreen(bounds)
        // Display capture includes the native popup window; Activity PixelCopy can capture only the underlay.
        val bitmap = checkNotNull(automation.takeScreenshot()).copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val file = java.io.File(instrumentation.targetContext.cacheDir, if (dark) "glass-dark.png" else "glass-light.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val x = (bounds.right - with(compose.density) { 12.dp.toPx() }).toInt().coerceIn(0, bitmap.width - 1)
        val y = bounds.centerY().coerceIn(0, bitmap.height - 1)
        val sample = Color(bitmap.getPixel(x, y))
        assertEquals(sample.red, sample.green, 0.02f)
        assertEquals(sample.green, sample.blue, 0.02f)
        assertTrue("Neutral contrast guard: $sample at $bounds", if (dark) sample.luminance() < 0.18f else sample.luminance() > 0.24f)
    }

    private fun open() {
        compose.onNodeWithContentDescription("Test options").performClick()
        compose.waitForIdle()
    }

    private fun fixture(
        light: Boolean = false,
        bottom: Boolean = false,
        rtl: Boolean = false,
        glass: Boolean = true,
        long: Boolean = false,
        dismissOnSelection: Boolean = true,
    ) {
        compose.setContent {
            EmPee3Theme(
                themeManifest = if (light) BuiltInThemes.DEFAULT_LIGHT else BuiltInThemes.DEFAULT_DARK,
                glassEffectEnabled = glass,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize().background(if (light) Color.Black else Color.White).testTag("canvas")) {
                        GlassMenuAnchor(Modifier.align(if (bottom) Alignment.BottomEnd else Alignment.TopEnd).padding(24.dp)) {
                            var open by remember { mutableStateOf(false) }
                            IconButton(modifier = Modifier.glassMenuTrigger(), onClick = { open = true }) { Icon(Icons.Default.Info, contentDescription = "Test options") }
                            GlassMenu(expanded = open, onDismissRequest = { open = false }, label = "Test options", dismissOnSelection = dismissOnSelection) {
                                GlassMenuSection("Track")
                                GlassMenuItem(text = { Text("Music information") }, icon = Icons.Default.Info, onClick = { selections++ })
                                GlassMenuItem(text = { Text("Unavailable") }, enabled = false, onClick = { error("Disabled action fired") })
                                if (long) {
                                    repeat(25) { index ->
                                        GlassMenuItem(text = { Text("Extra action $index") }, onClick = { selections++ })
                                    }
                                }
                                GlassMenuDivider()
                                GlassMenuItem(
                                    text = { Text("Remove test item") },
                                    destructive = true,
                                    secondaryText = "Only this isolated fixture",
                                    confirmation = GlassMenuConfirmation("Remove?", "This is a test action.", "Confirm removal"),
                                    onClick = { selections++ },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun findInformationNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
    if (node == null) return null
    if (node.text?.toString() == "Music information") return node
    for (index in 0 until node.childCount) {
        findInformationNode(node.getChild(index))?.let { return it }
    }
    return null
}
