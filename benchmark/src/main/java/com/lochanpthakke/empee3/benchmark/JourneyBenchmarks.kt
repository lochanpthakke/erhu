package com.lochanpthakke.empee3.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of everyday journeys. Each iteration starts from the same screen (set up untimed),
 * then only the journey itself is traced. Playback is never started: nothing plays during a run.
 */
@RunWith(AndroidJUnit4::class)
class JourneyBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode(),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()
        }

    @Test
    fun tracksScroll() = frames(setup = { openSection("Home") }) { flingDownAndUp() }

    @Test
    fun albumsScroll() = frames(setup = { openSection("Albums") }) { flingDownAndUp() }

    @Test
    fun albumDetailOpenClose() =
        frames(setup = { openSection("Albums") }) {
            repeat(REPEATS) {
                tapFirstItem()
                back()
            }
        }

    @Test
    fun playerExpandCollapse() =
        frames(setup = { openSection("Home") }) {
            repeat(REPEATS) {
                expandPlayer()
                back()
            }
        }

    @Test
    fun trackSwipe() =
        frames(setup = {
            openSection("Home")
            expandPlayer()
        }) {
            repeat(REPEATS) {
                swipeTrack(forward = true)
                swipeTrack(forward = false)
            }
        }

    @Test
    fun lyricsOpenClose() =
        frames(setup = {
            openSection("Home")
            expandPlayer()
        }) {
            repeat(REPEATS) {
                tap(By.desc("Open queue and lyrics"))
                tap(By.text("Lyrics"))
                back()
            }
        }

    @Test
    fun libraryVybeSwitch() =
        frames(setup = { openSection("Home") }) {
            repeat(REPEATS) {
                openSection("VYBE")
                openSection("Home")
            }
        }

    /** Frames of [journey], after [setup] has brought the app to its starting screen. */
    private fun frames(
        setup: MacrobenchmarkScope.() -> Unit,
        journey: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode(),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            settle()
            setup()
        },
        measureBlock = journey,
    )

    private companion object {
        const val REPEATS = 3
    }
}
