package com.lochanpthakke.empee3.core.sound.visualizer

import com.lochanpthakke.empee3.core.model.VisualizerSettings
import com.lochanpthakke.empee3.core.model.VisualizerSmoothing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class VisualizerAnalysisTest {
    private val rate = 48_000

    private fun sine(
        hz: Double,
        amplitude: Double,
        count: Int = SpectrumAnalyzer.DEFAULT_FFT_SIZE,
        phase: Int = 0,
    ) = FloatArray(count) { (amplitude * sin(2 * PI * hz * (it + phase) / rate)).toFloat() }

    @Test
    fun `fft finds a pure tone in its own bin`() {
        val n = 1024
        val fft = Fft(n)
        val bin = 37
        val re = FloatArray(n) { sin(2 * PI * bin * it / n).toFloat() }
        val im = FloatArray(n)
        fft.transform(re, im)
        val magnitudes = FloatArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(bin, magnitudes.indices.maxBy { magnitudes[it] })
        assertEquals(n / 2f, magnitudes[bin], 0.5f)
        assertTrue(magnitudes.filterIndexed { i, _ -> i != bin }.all { it < 1e-2f })
    }

    @Test
    fun `bands are log spaced, increasing, and follow the real sample rate`() {
        for (sampleRate in listOf(44_100, 48_000, 96_000, 22_050)) {
            val analyzer = SpectrumAnalyzer(sampleRate, 48)
            val centers = analyzer.bandCenterHz
            assertTrue(centers.toList().zipWithNext().all { (a, b) -> b >= a })
            assertTrue(centers.first() in 30f..80f)
            assertTrue(centers.last() < sampleRate / 2f)
            assertTrue(centers.last() <= 16_500f)
        }
    }

    @Test
    fun `a tone lights the band that holds its frequency`() {
        val analyzer = SpectrumAnalyzer(rate, 48)
        val out = FloatArray(48)
        listOf(60.0, 1_000.0, 8_000.0).forEach { hz ->
            analyzer.reset()
            analyzer.analyze(sine(hz, 0.5), out, 0f)
            val loudest = out.indices.maxBy { out[it] }
            val center = analyzer.bandCenterHz[loudest]
            assertTrue("tone $hz Hz shown at $center Hz", abs(center - hz) / hz < 0.25)
            assertTrue(out[loudest] > 0.5f)
        }
    }

    @Test
    fun `silence and near silence produce no activity`() {
        val analyzer = SpectrumAnalyzer(rate, 48)
        val out = FloatArray(48) { 0.7f }
        assertFalse(analyzer.analyze(FloatArray(SpectrumAnalyzer.DEFAULT_FFT_SIZE), out, 0.016f))
        assertTrue(out.all { it == 0f })
        // Dither-level noise (-90 dBFS) is still silence.
        val hiss = FloatArray(SpectrumAnalyzer.DEFAULT_FFT_SIZE) { (if (it % 2 == 0) 3e-5f else -3e-5f) }
        assertFalse(analyzer.analyze(hiss, out, 0.016f))
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun `quiet and loud versions of the same music look alike after a moment`() {
        fun music(scale: Double) =
            FloatArray(SpectrumAnalyzer.DEFAULT_FFT_SIZE) { (scale * (0.5 * sin(2 * PI * 80 * it / rate) + 0.2 * sin(2 * PI * 2_000 * it / rate))).toFloat() }
        val quiet = SpectrumAnalyzer(rate, 32)
        val loud = SpectrumAnalyzer(rate, 32)
        val outQuiet = FloatArray(32)
        val outLoud = FloatArray(32)
        // Five seconds of the same music, 26 dB apart: each settles on its own loudness.
        repeat(5) {
            quiet.analyze(music(0.05), outQuiet, 1f)
            loud.analyze(music(1.0), outLoud, 1f)
        }
        assertTrue(outQuiet.max() > 0.9f)
        for (i in 0 until 32) assertEquals("band $i", outLoud[i], outQuiet[i], 0.05f)
        assertTrue((outQuiet + outLoud).all { it in 0f..1f })
    }

    @Test
    fun `one loud transient does not flatten what follows for long`() {
        val analyzer = SpectrumAnalyzer(rate, 32)
        val out = FloatArray(32)
        analyzer.analyze(sine(200.0, 1.0), out, 0f) // a full-scale hit
        analyzer.analyze(sine(200.0, 0.1), out, 0.02f) // the music right after, 20 dB quieter
        val justAfter = out.max()
        repeat(5) { analyzer.analyze(sine(200.0, 0.1), out, 1f) } // five seconds later
        val later = out.max()
        assertTrue("just after $justAfter, later $later", later > justAfter + 0.2f)
        assertEquals(1f, later, 0.05f)
    }

    @Test
    fun `smoothing rises fast, falls slowly, stays bounded and ignores frame rate`() {
        val smoother = LevelSmoother(2, attackSeconds = 0.04f, releaseSeconds = 0.25f)
        smoother.update(floatArrayOf(1f, 5f), 0.1f)
        assertTrue(smoother.values[0] > 0.9f)
        assertTrue(smoother.values[1] in 0.9f..1f) // a target above 1 is clamped
        smoother.update(floatArrayOf(0f, 0f), 0.1f)
        assertTrue(smoother.values[0] in 0.6f..0.75f)

        // 60 fps and 30 fps reach the same value after the same time.
        val fast = LevelSmoother(1)
        val slow = LevelSmoother(1)
        repeat(60) { fast.update(floatArrayOf(0.8f), 1f / 60) }
        repeat(30) { slow.update(floatArrayOf(0.8f), 1f / 30) }
        assertEquals(fast.values[0], slow.values[0], 1e-4f)
    }

    @Test
    fun `waveform gain shows quiet music readably and keeps silence flat`() {
        val gain = WaveformGain()
        val out = FloatArray(512)
        gain.normalize(sine(440.0, 0.2, 512), 512, out, 0.016f)
        assertEquals(1f, out.max(), 0.05f)
        assertTrue(out.all { it in -1f..1f })
        gain.reset()
        gain.normalize(FloatArray(512), 512, out, 0.016f)
        assertTrue(out.all { it == 0f })
        gain.reset()
        gain.normalize(sine(440.0, 0.01, 512), 512, out, 0.016f) // -40 dB: drawn at its true small size
        assertTrue(out.max() < 0.25f)
    }

    private fun pcm16(samples: FloatArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2 * 2).order(ByteOrder.nativeOrder())
        samples.forEach { s ->
            val v = (s * Short.MAX_VALUE).toInt().toShort()
            buffer.putShort(v)
            buffer.putShort(v)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun `the tap only collects while acquired, leaves buffers untouched and is bounded`() {
        val tap = PlaybackAudioTap()
        tap.onFormat(rate, 2, pcm16 = true, pcmFloat = false)
        val buffer = pcm16(sine(1_000.0, 0.5, 4_800))
        val position = buffer.position()
        tap.write(buffer)
        assertEquals(position, buffer.position()) // playback's buffer is not consumed
        assertEquals(4_800L, tap.framesWritten) // counted even without a reader
        val dest = FloatArray(1024)
        assertFalse(tap.read(dest, 1024, 0)) // nothing collected before anyone listened

        tap.acquire()
        repeat(100) { tap.write(pcm16(sine(1_000.0, 0.5, 4_800, phase = it * 4_800))) } // 10 s, far more than the ring
        assertTrue(tap.read(dest, 1024, 0))
        assertEquals(0.5f, dest.maxOf { abs(it) }, 0.01f) // stereo downmixed, scaled to -1..1
        assertTrue(tap.read(dest, 1024, PlaybackAudioTap.CAPACITY.toLong() * 10)) // asking too far back stays in range

        tap.release()
        assertFalse(tap.isCollecting)
        assertFalse(tap.read(dest, 1024, 0))
    }

    @Test
    fun `a flush or format change starts over and unsupported formats are not read`() {
        val tap = PlaybackAudioTap()
        tap.acquire()
        tap.onFormat(rate, 2, pcm16 = true, pcmFloat = false)
        tap.write(pcm16(sine(500.0, 0.5, 2_048)))
        val generation = tap.generation
        tap.onFormat(rate, 2, pcm16 = true, pcmFloat = false) // seek
        assertTrue(tap.generation != generation)
        assertEquals(0L, tap.framesWritten)
        assertFalse(tap.read(FloatArray(256), 256, 0))

        tap.onFormat(rate, 6, pcm16 = false, pcmFloat = false) // e.g. a compressed passthrough stream
        tap.write(pcm16(sine(500.0, 0.5, 2_048)))
        assertFalse(tap.isFormatSupported)
        assertEquals(0, tap.currentSampleRate)
        assertFalse(tap.read(FloatArray(256), 256, 0))
    }

    /** Synthetic source: playback at 1x with a fixed output delay. */
    private class FakeSource(
        override val currentSampleRate: Int = 48_000,
    ) : VisualizerAudioSource {
        override var generation = 1
        override var flushNanos = 0L
        override var framesWritten = 0L
        override var lastWriteNanos = 0L
        override var isFormatSupported = true
        var signal: (Long) -> Float = { 0f }
        var lastRequestedLatency = -1L

        override fun read(
            dest: FloatArray,
            count: Int,
            framesBeforeNewest: Long,
        ): Boolean {
            lastRequestedLatency = framesBeforeNewest
            val end = framesWritten - framesBeforeNewest
            for (i in 0 until count) dest[i] = signal(end - count + i)
            return true
        }
    }

    @Test
    fun `the engine reacts while playing, rests when paused and maps the heard sample after a seek`() {
        val source = FakeSource()
        val engine = VisualizerEngine(source)
        engine.apply(VisualizerSettings(smoothing = VisualizerSmoothing.RESPONSIVE))
        source.signal = { frame -> (0.5 * sin(2 * PI * 300 * frame / 48_000)).toFloat() }
        var now = 1_000_000_000L

        // Opened mid-song: no anchor yet, so the typical output latency is assumed.
        source.framesWritten = 480_000
        source.lastWriteNanos = now
        engine.update(now, PlaybackClock(isPlaying = true, positionMs = 9_750, entryKey = 1))
        assertEquals(VisualizerEngine.FALLBACK_LATENCY_MS * 48, source.lastRequestedLatency)

        // A seek: flushed just now, playback resumes at 30 s; the output runs 120 ms behind the writer.
        source.generation++
        source.flushNanos = now
        source.framesWritten = 0
        now += 16_000_000
        engine.update(now, PlaybackClock(true, 30_000, 1))
        source.framesWritten = 48_000 + 120 * 48 // 1.12 s written
        source.lastWriteNanos = now
        repeat(20) {
            now += 16_000_000
            engine.update(now, PlaybackClock(true, 31_000, 1)) // 1 s heard
        }
        assertEquals(120L * 48, source.lastRequestedLatency)
        assertEquals(VisualizerStatus.ACTIVE, engine.status)
        assertTrue(engine.spectrum.max() > 0.8f)
        assertTrue(engine.waveform.any { it > 0.5f })

        // Paused: no new samples; levels settle to rest without inventing movement.
        repeat(120) {
            now += 16_000_000
            engine.update(now, PlaybackClock(false, 31_000, 1))
        }
        assertEquals(VisualizerStatus.PAUSED, engine.status)
        assertTrue(engine.spectrum.max() < 0.01f)
        assertTrue(engine.waveform.maxOf { abs(it) } < 0.01f)
    }

    @Test
    fun `audio that never arrives or cannot be read is reported as unavailable`() {
        val source = FakeSource()
        source.isFormatSupported = false
        val engine = VisualizerEngine(source)
        var now = 1L
        engine.update(now, PlaybackClock(true, 0, 1))
        assertEquals(VisualizerStatus.WAITING, engine.status)
        repeat(40) {
            now += 50_000_000
            engine.update(now, PlaybackClock(true, 0, 1))
        }
        assertEquals(VisualizerStatus.UNAVAILABLE, engine.status)
        assertTrue(engine.spectrum.all { it == 0f })
    }

    @Test
    fun `no microphone capture anywhere in the app`() {
        // The visualizer reads only the app's own playback. Guard against microphone APIs creeping in.
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val forbidden = listOf("RECORD_AUDIO", "AudioRecord(", "android.media.audiofx.Visualizer", "MediaRecorder")
        val offenders =
            root
                .walkTopDown()
                .onEnter { it.name != "build" && !it.name.startsWith(".") }
                .filter { it.isFile && (it.extension == "kt" || it.name == "AndroidManifest.xml") && !it.name.endsWith("Test.kt") }
                .filter { file -> forbidden.any { file.readText().contains(it) } }
                .toList()
        assertTrue("Microphone APIs found in: $offenders", offenders.isEmpty())
    }
}
