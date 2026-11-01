package com.erhu.erhu.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app under test: always the separate ".bench" package (debug or benchmark build). */
const val TARGET_PACKAGE = "com.lochanpthakke.empee3.bench"

const val ITERATIONS = 5

private const val UI_TIMEOUT_MS = 5_000L

/** Long enough for the app's springs to come to rest between steps. */
private const val SETTLE_MS = 900L

private const val FLINGS = 3

private const val TAP_ATTEMPTS = 3

/**
 * The app's compilation state, from the `compilation` instrumentation argument:
 * - `partial` (default): as after normal use (JIT profile from warm-up runs, then compiled);
 * - `none`: as right after installing, before the system has compiled anything;
 * - `ignore`: leave it as it is (required for a debuggable build, which can't be compiled).
 */
@OptIn(ExperimentalMacrobenchmarkApi::class)
fun compilationMode(): CompilationMode =
    when (InstrumentationRegistry.getArguments().getString("compilation")) {
        "none" -> CompilationMode.None()
        "full" -> CompilationMode.Full()
        "ignore" -> CompilationMode.Ignore()
        else -> CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3)
    }

fun settle() = Thread.sleep(SETTLE_MS)

/** Opens a section of the library's bottom navigation (its items are described by their label). */
fun MacrobenchmarkScope.openSection(label: String) = tap(By.desc(label))

/**
 * Finds and taps [selector], then waits for the UI to settle. Compose may replace the node between
 * finding and tapping it (a recomposition), so a stale node is looked up again.
 */
fun MacrobenchmarkScope.tap(selector: BySelector) {
    repeat(TAP_ATTEMPTS) {
        val target = device.wait(Until.findObject(selector), UI_TIMEOUT_MS) ?: error("Nothing matches $selector")
        try {
            target.click()
            settle()
            return
        } catch (_: StaleObjectException) {
            // Looked up again below.
        }
    }
    error("$selector kept changing before it could be tapped")
}

/** The tallest scrollable on screen: the page's main list or grid. */
fun MacrobenchmarkScope.mainList(): UiObject2 {
    device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
    val list = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: error("No scrollable list")
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    return list
}

/**
 * Flings the page down and back up again with quick swipes down the middle of the screen
 * (screen coordinates, not a UI node: Compose may replace the list's node mid-fling).
 */
fun MacrobenchmarkScope.flingDownAndUp() {
    device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
    val x = device.displayWidth / 2
    val low = (device.displayHeight * FLING_LOW_FRACTION).toInt()
    val high = (device.displayHeight * FLING_HIGH_FRACTION).toInt()
    repeat(FLINGS) {
        device.swipe(x, low, x, high, FLING_STEPS)
        Thread.sleep(FLING_GAP_MS)
    }
    // A few more upward flings than downward ones, so the next iteration starts at the top.
    repeat(FLINGS + 2) {
        device.swipe(x, high, x, low, FLING_STEPS)
        Thread.sleep(FLING_GAP_MS)
    }
}

/** Taps the first item of the main list or grid (e.g. the first album). */
fun MacrobenchmarkScope.tapFirstItem() {
    repeat(TAP_ATTEMPTS) {
        try {
            mainList().children.first().click()
            settle()
            return
        } catch (_: StaleObjectException) {
            // Looked up again below.
        }
    }
    error("The list kept changing before its first item could be tapped")
}

/** Taps the mini player (its artwork) to open Now Playing. */
fun MacrobenchmarkScope.expandPlayer() = tap(By.desc("Album artwork"))

fun MacrobenchmarkScope.back() {
    device.pressBack()
    settle()
}

/** One horizontal swipe across Now Playing's artwork: [forward] = to the next track. */
fun MacrobenchmarkScope.swipeTrack(forward: Boolean) {
    val y = (device.displayHeight * ARTWORK_Y_FRACTION).toInt()
    val edge = device.displayWidth / SWIPE_EDGE_FRACTION
    val (from, to) = if (forward) device.displayWidth - edge to edge else edge to device.displayWidth - edge
    device.swipe(from, y, to, y, SWIPE_STEPS)
    settle()
}

private const val GESTURE_MARGIN_FRACTION = 5

/** Flings run between these heights (fractions of the screen), clear of the bars. */
private const val FLING_LOW_FRACTION = 0.72f
private const val FLING_HIGH_FRACTION = 0.3f

/** ~40 ms swipes: a fling. */
private const val FLING_STEPS = 8

/** Long enough for a fling to run its course before the next. */
private const val FLING_GAP_MS = 1_000L
private const val ARTWORK_Y_FRACTION = 0.35f
private const val SWIPE_EDGE_FRACTION = 6

/** UiAutomator swipe steps take ~5 ms each: a quick, deliberate swipe. */
private const val SWIPE_STEPS = 12
