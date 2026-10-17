package com.lochanpthakke.empee3.perf

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.lochanpthakke.empee3.core.common.perf.Interactions
import java.util.Locale

/**
 * Debug builds: frame diagnostics with JankStats. Every frame is counted against the
 * interactions active while it was drawn ([Interactions]: screen, scrolling, player motion,
 * artwork decoding, VYBE work...), and logged under the "Smoothness" tag:
 *
 * - each janky frame (JankStats' default: longer than twice the display's frame period), with
 *   its duration, overrun (Android 12+) and active interactions;
 * - every [SUMMARY_INTERVAL_MS] with frames, and when the app leaves the screen, one summary
 *   line per set of interactions: frames, janky frames, worst frame.
 *
 * Logs hold only labels and timings: no song titles, file names or other library contents.
 * Debug timings are slower than release ones (no R8, debuggable ART); compare like with like.
 */
internal object FrameDiagnostics {
    private const val TAG = "Smoothness"
    private const val SUMMARY_INTERVAL_MS = 10_000L
    private const val NANOS_PER_MILLI = 1_000_000.0

    private val main = Handler(Looper.getMainLooper())

    /** The visible activity's state holder; interactions reported elsewhere land here. */
    @Volatile
    private var holder: PerformanceMetricsState.Holder? = null

    fun install(activity: ComponentActivity) {
        val activityHolder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
        holder = activityHolder
        Interactions.sink =
            Interactions.Sink { key, value ->
                main.post {
                    val state = holder?.state ?: return@post
                    if (value == null) state.removeState(key) else state.putState(key, value)
                }
            }
        val summary = Summary()
        val stats = JankStats.createAndTrack(activity.window, summary)
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> stats.isTrackingEnabled = true
                    Lifecycle.Event.ON_PAUSE -> {
                        stats.isTrackingEnabled = false
                        summary.flush()
                    }
                    Lifecycle.Event.ON_DESTROY -> if (holder === activityHolder) holder = null
                    else -> Unit
                }
            },
        )
    }

    /** Frame counts per set of active interactions, logged and reset by [flush]. */
    private class Summary : JankStats.OnFrameListener {
        private class Bucket {
            var frames = 0
            var janky = 0
            var worstMs = 0.0
        }

        private val buckets = HashMap<String, Bucket>()
        private var lastFlush = SystemClock.uptimeMillis()

        override fun onFrame(volatileFrameData: FrameData) {
            // The frame data object is reused: read everything now.
            val interactions =
                volatileFrameData.states
                    .sortedBy { it.key }
                    .joinToString(" ") { "${it.key}=${it.value}" }
                    .ifEmpty { "idle" }
            val durationMs = volatileFrameData.frameDurationUiNanos / NANOS_PER_MILLI
            val bucket = buckets.getOrPut(interactions) { Bucket() }
            bucket.frames++
            if (durationMs > bucket.worstMs) bucket.worstMs = durationMs
            if (volatileFrameData.isJank) {
                bucket.janky++
                val overrun =
                    (volatileFrameData as? FrameDataApi31)
                        ?.let { " overrun ${ms(it.frameOverrunNanos / NANOS_PER_MILLI)}" }
                        .orEmpty()
                Log.d(TAG, "jank ${ms(durationMs)}$overrun | $interactions")
            }
            if (SystemClock.uptimeMillis() - lastFlush >= SUMMARY_INTERVAL_MS) flush()
        }

        fun flush() {
            lastFlush = SystemClock.uptimeMillis()
            if (buckets.isEmpty()) return
            buckets.entries
                .sortedByDescending { it.value.frames }
                .forEach { (interactions, b) ->
                    Log.i(TAG, "summary frames ${b.frames} janky ${b.janky} worst ${ms(b.worstMs)} | $interactions")
                }
            buckets.clear()
        }

        private fun ms(value: Double) = String.format(Locale.US, "%.1f ms", value)
    }
}
