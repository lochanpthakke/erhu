package com.erhu.erhu

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.erhu.erhu.feature.settings.theme.ThemeViewModel
import com.erhu.erhu.feature.library.LibraryViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the isolated -PbenchApp package; never changes production preferences or playback. */
class VybeToggleInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for (i in 0 until node.childCount) visit(node.getChild(i))?.let { return it }
            return null
        }
        return visit(automation.rootInActiveWindow)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 20000
        while (!condition()) {
            check(android.os.SystemClock.uptimeMillis() < deadline) { message }
            android.os.SystemClock.sleep(100)
        }
    }

    private fun click(label: String) {
        await("Cannot click $label") {
            var node = find(label)
            while (node != null && !node.isClickable) node = node.parent
            node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun back() {
        shell("input keyevent 4")
        android.os.SystemClock.sleep(600)
    }

    @Test fun repeatedToggleRestorationAndSearchKeepTheHiddenSectionInaccessible() = runBlocking {
        assertTrue("Use -PbenchApp", context.packageName.endsWith(".bench"))
        val permission = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        shell("pm grant ${context.packageName} $permission")
        lateinit var preferences: ThemeViewModel
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            preferences = ViewModelProvider(it)[ThemeViewModel::class.java]
            ViewModelProvider(it)[LibraryViewModel::class.java].setScanEntireDevice()
        }
        withTimeout(5000) { preferences.vybeEnabled.filterNotNull().first() }
        preferences.completeOnboarding()
        preferences.setVybeEnabled(true)
        try {
            await("Enabled VYBE missing") { find("VYBE") != null }
            val searchBefore = Rect().also { requireNotNull(find("Search library")).getBoundsInScreen(it) }
            repeat(3) {
                click("VYBE")
                await("VYBE screen did not open") { find("Describe what you want to hear") != null }
                click("Open navigation drawer")
                click("Settings")
                click("Navigation & Home Page")
                click("Enable VYBE")
                await("Preference did not disable") { preferences.vybeEnabled.value == false }
                back()
                back()
                await("Hidden VYBE remained in navigation") { find("VYBE") == null && find("Home") != null }
                assertNotNull(find("Albums"))
                assertNotNull(find("Library"))
                assertNull(find("Featured Radio Stations"))
                assertNull(find("Describe what you want to hear"))
                val searchAfter = Rect().also { requireNotNull(find("Search library")).getBoundsInScreen(it) }
                assertEquals(searchBefore.right, searchAfter.right)
                assertEquals(searchBefore.width(), searchAfter.width())
                scenario.recreate()
                await("Restoration reopened hidden VYBE") { find("Home") != null && find("Search library") != null }
                assertNull(find("VYBE"))
                click("Search library")
                click("Close search")
                click("Open navigation drawer")
                click("Settings")
                click("Navigation & Home Page")
                click("Enable VYBE")
                await("Preference did not re-enable") { preferences.vybeEnabled.value == true }
                back()
                back()
                await("VYBE was not restored") { find("VYBE") != null }
            }
        } finally {
            preferences.setVybeEnabled(true)
            scenario.close()
        }
    }
}
