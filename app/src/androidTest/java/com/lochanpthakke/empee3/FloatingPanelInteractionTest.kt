package com.lochanpthakke.empee3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.lochanpthakke.empee3.core.designsystem.component.GlassFloatingPanel
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.PopupGlassBackdropHost
import com.lochanpthakke.empee3.core.designsystem.component.PopupGlassPreview
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassBackdrop
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop
import com.lochanpthakke.empee3.feature.library.LibraryNavigationBar
import com.lochanpthakke.empee3.feature.library.LibrarySection
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FloatingPanelInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun slidersScrollAndNestedOptionsRemainInteractiveAfterReopening() {
        var value = 0.5f
        var chosen = false
        compose.setContent {
            EmPee3Theme(themeManifest = BuiltInThemes.DEFAULT_DARK, glassEffectEnabled = true) {
                PopupGlassBackdropHost(Modifier.fillMaxSize().background(Color.White)) {
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = true }) { Text("Open panel") }
                    if (open) GlassFloatingPanel(onDismissRequest = { open = false }) {
                        var local by remember { mutableFloatStateOf(value) }
                        Slider(local, { local = it; value = it }, Modifier.fillMaxWidth().testTag("panel-slider"))
                        var options by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { options = true }) { Text("Options") }
                            GlassMenu(options, { options = false }) {
                                GlassMenuItem(text = { Text("Choose option") }, icon = Icons.Default.Info, onClick = { chosen = true })
                            }
                        }
                        repeat(30) { TextButton(onClick = {}) { Text("Setting $it") } }
                        TextButton(onClick = { open = false }) { Text("Close panel") }
                    }
                }
            }
        }
        repeat(2) {
            compose.onNodeWithText("Open panel").performClick()
            compose.onNodeWithTag("panel-slider").performTouchInput {
                swipe(Offset(width * 0.2f, height / 2f), Offset(width * 0.9f, height / 2f), 350)
            }
            compose.runOnIdle { assertTrue("Horizontal slider lost the drag", value > 0.8f) }
            compose.onNodeWithText("Options").performClick()
            compose.onNodeWithText("Choose option").performClick()
            compose.runOnIdle { assertTrue(chosen) }
            compose.onNodeWithText("Close panel").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Open panel").assertIsDisplayed()
        }
        compose.onNodeWithText("Open panel").performClick()
        Espresso.pressBack()
        compose.onNodeWithTag("panel-slider").assertDoesNotExist()
    }

    @Test
    fun materialStrengthUpdatesTheNativePanelWhileOpen() {
        val strength = mutableFloatStateOf(0f)
        compose.setContent {
            EmPee3Theme(themeManifest = BuiltInThemes.DEFAULT_DARK, glassEffectEnabled = true, popupGlassOpacity = strength.floatValue) {
                PopupGlassBackdropHost(Modifier.fillMaxSize().background(Color.White)) {
                    GlassFloatingPanel(onDismissRequest = {}) { Text("Material sample") }
                }
            }
        }
        val values = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { amount ->
            compose.runOnIdle { strength.floatValue = amount }
            compose.waitForIdle()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val bounds = android.graphics.Rect()
            requireNotNull(findNode(automation.rootInActiveWindow, "Material sample")).getBoundsInScreen(bounds)
            val bitmap = requireNotNull(automation.takeScreenshot())
            val color = Color(bitmap.getPixel(bounds.right + 12, bounds.centerY()))
            assertTrue(kotlin.math.abs(color.red - color.blue) < 0.02f)
            val file = java.io.File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "strength-panel-${(amount * 100).toInt()}.png",
            )
            file.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            color.luminance()
        }
        values.zipWithNext().forEach { (less, more) ->
            assertTrue("More Glass must reveal a brighter, clearer neutral backing: $values", more - less > 0.004f)
        }
    }
    @Test
    fun samePreviewHasFiveVisiblyDifferentRenderedStrengths() {
        val strength = mutableFloatStateOf(0f)
        compose.setContent {
            EmPee3Theme(themeManifest = BuiltInThemes.DEFAULT_DARK, glassEffectEnabled = true, popupGlassOpacity = strength.floatValue) {
                val source = rememberGlassBackdrop("strength-navigation")
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().glassBackdropSource(source)) { drawRect(Color.White) }
                    PopupGlassPreview(Modifier.semantics { contentDescription = "Strength preview" })
                    CompositionLocalProvider(LocalGlassBackdrop provides source) {
                        var selected by remember { mutableStateOf(LibrarySection.TRACKS) }
                        LibraryNavigationBar(selected, { selected = it }, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        val images = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { amount ->
            compose.runOnIdle { strength.floatValue = amount }
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val automation = instrumentation.uiAutomation
            val bounds = android.graphics.Rect()
            requireNotNull(findNode(automation.rootInActiveWindow, "Strength preview")).getBoundsInScreen(bounds)
            val screen = requireNotNull(automation.takeScreenshot())
            val preview = android.graphics.Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
            for ((name, bitmap) in listOf("preview" to preview, "screen" to screen)) {
                java.io.File(instrumentation.targetContext.cacheDir, "strength-$name-${(amount * 100).toInt()}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            screen.recycle()
            preview
        }
        images.zipWithNext().forEachIndexed { index, (before, after) ->
            var sum = 0L
            var count = 0
            for (y in 0 until before.height step 4) for (x in 0 until before.width step 4) {
                val a = before.getPixel(x, y)
                val b = after.getPixel(x, y)
                for (shift in listOf(0, 8, 16)) sum += kotlin.math.abs(((a shr shift) and 255) - ((b shr shift) and 255))
                count += 3
            }
            assertTrue("Adjacent preview strengths $index and ${index + 1} are visually indistinguishable", sum.toDouble() / count > 1.5)
        }
        images.forEach { it.recycle() }
    }

}

private fun findNode(
    node: android.view.accessibility.AccessibilityNodeInfo?,
    label: String,
): android.view.accessibility.AccessibilityNodeInfo? {
    if (node == null) return null
    return if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
        node
    } else {
        (0 until node.childCount).firstNotNullOfOrNull { findNode(node.getChild(it), label) }
    }
}
