package com.lochanpthakke.empee3.core.network.bpm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class TempoEstimatorTest {
    private val rate = 11025

    /**
     * A drum pattern at [bpm]: a kick on every beat, a snare on 2 and 4, hi-hats on every half beat,
     * over quiet noise. [swingMs] shifts the beats randomly (a human drummer).
     */
    private fun drums(
        bpm: Double,
        seconds: Int,
        seed: Int = 1,
        swingMs: Double = 0.0,
        into: FloatArray = FloatArray(seconds * rate),
        from: Int = 0,
    ): FloatArray {
        val random = Random(seed)
        for (i in from until from + seconds * rate) into[i] += (random.nextFloat() - 0.5f) * 0.02f
        val beat = 60.0 / bpm
        var t = 0.0
        var n = 0
        while (t < seconds) {
            val jitter = if (swingMs > 0) (random.nextDouble() - 0.5) * swingMs / 1000 else 0.0
            val at = from + ((t + jitter).coerceAtLeast(0.0) * rate).toInt()
            hit(into, at, 0.15, 0.9f) { s -> sin(2 * PI * 55 * s).toFloat() } // kick: body…
            hit(into, at, 0.012, 0.4f) { (random.nextFloat() - 0.5f) * 2 } // …and its beater click
            if (n % 2 == 1) hit(into, at, 0.08, 0.5f) { (random.nextFloat() - 0.5f) * 2 } // snare
            hit(into, at + (beat / 2 * rate).toInt(), 0.03, 0.2f) { (random.nextFloat() - 0.5f) * 2 } // hat
            t += beat
            n++
        }
        return into
    }

    private fun hit(
        into: FloatArray,
        at: Int,
        seconds: Double,
        gain: Float,
        wave: (Double) -> Float,
    ) {
        val length = (seconds * rate).toInt()
        for (i in 0 until length) {
            val index = at + i
            if (index !in into.indices) return
            val s = i.toDouble() / rate
            into[index] += gain * wave(s) * exp(-s / (seconds / 4)).toFloat()
        }
    }

    private fun found(estimate: TempoEstimate): TempoEstimate.Found {
        assertTrue("expected a tempo, got $estimate", estimate is TempoEstimate.Found)
        return estimate as TempoEstimate.Found
    }

    @Test
    fun steadyBeats_giveTheirTempo_withinOneBpm() {
        for (bpm in listOf(90.0, 100.0, 110.0, 120.0, 128.0, 140.0)) {
            val estimate = found(TempoEstimator.estimate(drums(bpm, 60), rate))
            assertEquals("for $bpm BPM", bpm, estimate.bpm.toDouble(), 1.0)
            assertEquals(3, estimate.sections)
        }
    }

    @Test
    fun anAmbiguousOctave_isNeverGuessed() {
        // Kick + snare + eighth-note hats at these tempos are as much half / double time: no value.
        for (bpm in listOf(84.0, 150.0, 160.0, 174.0)) {
            val estimate = TempoEstimator.estimate(drums(bpm, 60), rate)
            assertTrue("for $bpm BPM: $estimate", estimate is TempoEstimate.Uncertain)
            assertTrue((estimate as TempoEstimate.Uncertain).reason.startsWith("Half-time / double-time ambiguity"))
        }
    }

    @Test
    fun knownLimit_slowSongsWithClearOffBeats_readAtDoubleTempo() {
        // The pulse train of 60 BPM with eighth-note hats is that of 120 BPM; most tempo tools say
        // 120 too. Recorded here so a change in this behaviour is noticed.
        assertEquals(120, found(TempoEstimator.estimate(drums(60.0, 60), rate)).bpm)
        assertEquals(144, found(TempoEstimator.estimate(drums(72.0, 60), rate)).bpm)
    }

    @Test
    fun hatsOnHalfBeats_doNotDoubleTheTempo() {
        // The hats alone tick at 200 per minute; the beat is 100.
        assertEquals(100, found(TempoEstimator.estimate(drums(100.0, 60), rate)).bpm)
    }

    @Test
    fun aHumanDrummer_isStillMeasured() {
        assertEquals(96.0, found(TempoEstimator.estimate(drums(96.0, 60, swingMs = 20.0), rate)).bpm.toDouble(), 1.0)
    }

    @Test
    fun noise_hasNoTempo() {
        val random = Random(7)
        val noise = FloatArray(60 * rate) { (random.nextFloat() - 0.5f) * 0.5f }
        assertTrue(TempoEstimator.estimate(noise, rate) is TempoEstimate.Uncertain)
        assertTrue(TempoEstimator.estimate(FloatArray(60 * rate), rate) is TempoEstimate.Uncertain)
    }

    @Test
    fun aTempoThatKeepsChanging_isNotReported() {
        val audio = FloatArray(60 * rate)
        drums(84.0, 20, into = audio, from = 0)
        drums(132.0, 20, into = audio, from = 20 * rate)
        drums(109.0, 20, into = audio, from = 40 * rate)
        val estimate = TempoEstimator.estimate(audio, rate)
        assertTrue("got $estimate", estimate is TempoEstimate.Uncertain)
    }

    @Test
    fun oneChangedSection_isOutvoted() {
        val audio = FloatArray(60 * rate)
        drums(120.0, 40, into = audio, from = 0)
        drums(84.0, 20, into = audio, from = 40 * rate)
        val estimate = found(TempoEstimator.estimate(audio, rate))
        assertEquals(120, estimate.bpm)
        assertEquals(2, estimate.sectionsAgreeing)
    }

    @Test
    fun tooLittleAudio_isUncertain() {
        assertTrue(TempoEstimator.estimate(drums(120.0, 5), rate) is TempoEstimate.Uncertain)
    }

    @Test
    fun otherSampleRates_work() {
        val rate12k = 12000
        val bpm = 124.0
        val beat = (60.0 / bpm * rate12k)
        val audio = FloatArray(45 * rate12k)
        var at = 0.0
        while (at < audio.size) {
            for (i in 0 until 600) if (at.toInt() + i < audio.size) audio[at.toInt() + i] += sin(2 * PI * 60 * i / rate12k).toFloat() * exp(-i / 150.0).toFloat()
            at += beat
        }
        assertEquals(124.0, found(TempoEstimator.estimate(audio, rate12k)).bpm.toDouble(), 1.0)
    }
}
