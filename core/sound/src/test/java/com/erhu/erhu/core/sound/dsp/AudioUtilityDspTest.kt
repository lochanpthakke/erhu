/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Licensed under the GNU General Public License v3.0.
 * See the LICENSE file in the project root.
 */

package com.erhu.erhu.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.erhu.erhu.core.model.AudioUtilities
import com.erhu.erhu.core.model.ReplayGainMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class AudioUtilityDspTest {
    private fun processor(settings: AudioUtilities = AudioUtilities(), channels: Int = 2) = CustomEqAudioProcessor().apply {
        setUtilities(settings)
        configure(AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_16BIT))
        flush()
    }
    private fun process(p: CustomEqAudioProcessor, samples: ShortArray): ShortArray {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it) }; buffer.flip(); p.queueInput(buffer)
        val result = p.output.order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(result.remaining() / 2) { result.short }
    }
    private fun signal(hz: Float, frames: Int = 24000) = ShortArray(frames * 2) { (sin(2 * PI * hz * (it / 2) / 48000) * 3000).toInt().toShort() }
    private fun peak(samples: ShortArray) = samples.drop(samples.size / 2).maxOf { abs(it.toInt()) }

    @Test fun defaultsAreBitExactIncludingNegativeFullScale() {
        val input = shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE, -123, 456, 0, 0)
        assertArrayEquals(input, process(processor(), input))
    }
    @Test fun monoActuallyMixesBothChannelsWithEqDisabled() {
        val p = processor(AudioUtilities(mono = true)); p.setEqEnabled(false)
        val output = process(p, shortArrayOf(12000, -4000, -8000, 4000))
        assertEquals(4000f, output[0].toFloat(), 1f); assertEquals(output[0], output[1])
        assertEquals(-2000f, output[2].toFloat(), 1f); assertEquals(output[2], output[3])
    }
    @Test fun balanceMutesTheOppositeChannelAndMonoInputStaysAudible() {
        assertArrayEquals(shortArrayOf(0, 10000), process(processor(AudioUtilities(balance = 1f)), shortArrayOf(10000, 10000)))
        assertArrayEquals(shortArrayOf(10000, 0), process(processor(AudioUtilities(balance = -1f)), shortArrayOf(10000, 10000)))
        assertArrayEquals(shortArrayOf(10000), process(processor(AudioUtilities(balance = 1f), 1), shortArrayOf(10000)))
    }
    @Test fun preampRunsBeforeEqSoHeadroomCanPreventIntermediateClipping() {
        val p = processor(AudioUtilities(preampDb = -12f)); p.setBandGain(3, 12f)
        val output = process(p, signal(1000f))
        assertTrue(peak(output) in 2000..4000)
    }
    @Test fun bassShelfBoostsBassWithoutRequiringEqEnabled() {
        val p = processor(AudioUtilities(bassDb = 9f)); p.setEqEnabled(false)
        val low = peak(process(p, signal(40f)))
        assertTrue("Bass shelf peak: $low", low > 6500)
        val treble = processor(AudioUtilities(bassDb = 9f))
        val highSignal = signal(8000f)
        val high = peak(process(treble, highSignal))
        assertTrue("Treble changed: $high", abs(high - peak(highSignal)) < peak(highSignal) * 0.05f)
    }
    @Test fun limiterProtectsAfterEqAndLinksTheChannels() {
        val p = processor(AudioUtilities(preampDb = 12f, limiter = true)); p.setBandGain(3, 12f)
        val output = process(p, signal(1000f))
        assertTrue(output.maxOf { abs(it.toInt()) } <= 29205)
        assertTrue(peak(output) > 20000)
        for (i in output.indices step 2) assertEquals(output[i], output[i + 1])
    }
    @Test fun replayGainChangesRenderedPcmAndUntaggedAlbumModeDoesNotBorrowTrackGain() {
        val p = processor(AudioUtilities(replayGain = ReplayGainMode.TRACK)); p.setReplayGain(ReplayGainData(trackDb = -6.0206f))
        assertTrue(peak(process(p, signal(1000f))) in 1480..1510)
        val album = processor(AudioUtilities(replayGain = ReplayGainMode.ALBUM)); album.setReplayGain(ReplayGainData(trackDb = -6f))
        assertTrue(peak(process(album, signal(1000f))) in 2980..3010)
    }
    @Test fun utilityChangesAreSmoothedAndFlushResetsForANewFormat() {
        val p = processor(); process(p, ShortArray(960) { 10000 })
        p.setUtilities(AudioUtilities(balance = 1f))
        val output = process(p, ShortArray(24000) { 10000 })
        assertTrue(output[0] > 9000); assertTrue(output[output.size - 2] < 10)
        p.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)); p.flush()
        assertTrue(process(p, shortArrayOf(10000))[0] > 9900)
    }
    @Test fun tagParserHandlesUnitsAndRejectsMalformedOrExtremeNumbers() {
        val tags = ReplayGainData.fromTags(mapOf("REPLAYGAIN_TRACK_GAIN" to "-6.0 dB", "replaygain_album_gain" to "NaN", "replaygain_track_peak" to "2.0"))
        assertEquals(-6f, tags.trackDb!!, 0f); assertNull(tags.albumDb)
        assertTrue(tags.gain(ReplayGainMode.TRACK) <= 0.495f)
        assertEquals(1f, tags.gain(ReplayGainMode.OFF), 0f)
        assertNull(ReplayGainData.fromTags(mapOf("replaygain_track_gain" to "900 dB")).trackDb)
    }
}
