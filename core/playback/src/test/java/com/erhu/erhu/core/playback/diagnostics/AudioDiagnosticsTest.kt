package com.erhu.erhu.core.playback.diagnostics

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import com.erhu.erhu.core.sound.dsp.EqStage
import com.erhu.erhu.core.sound.dsp.StageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class AudioDiagnosticsTest {
    @Test
    fun `media formats keep unknown values unknown`() {
        val flac =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.AUDIO_FLAC)
                .setSampleRate(96_000)
                .build()
        val summary = MediaFormats.summarize(flac)
        assertEquals(96_000, summary.sampleRateHz)
        assertNull(summary.channelCount) // not reported: unknown, not 0
        assertNull(summary.averageBitrate)
        assertNull(summary.pcmEncoding)
        assertEquals("FLAC", MediaFormats.codecName(summary.sampleMimeType))

        val raw =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_24BIT)
                .setChannelCount(2)
                .setAverageBitrate(2_304_000)
                .build()
        assertEquals("PCM 24-bit", MediaFormats.summarize(raw).pcmEncoding)
        assertEquals("2 (Stereo)", MediaFormats.channelsText(2))
        assertEquals("44.1 kHz", MediaFormats.sampleRateText(44_100))
        assertEquals("2304 kbps", MediaFormats.bitrateText(2_304_000))
        assertNull(MediaFormats.channelLayout(3))
        assertTrue(MediaFormats.isLossless("FLAC") && !MediaFormats.isLossless("MP3"))
    }

    private fun flacHeader(
        sampleRate: Int,
        channels: Int,
        bits: Int,
        totalSamples: Long,
    ): ByteArray {
        val info = ByteArray(34)
        info[10] = (sampleRate ushr 12).toByte()
        info[11] = (sampleRate ushr 4).toByte()
        info[12] = (((sampleRate and 0xF) shl 4) or ((channels - 1) shl 1) or ((bits - 1) ushr 4)).toByte()
        info[13] = ((((bits - 1) and 0xF) shl 4) or ((totalSamples ushr 32).toInt() and 0xF)).toByte()
        for (i in 0 until 4) info[14 + i] = (totalSamples ushr (24 - 8 * i)).toByte()
        return "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + info
    }

    @Test
    fun `flac streaminfo gives the stored format, after an id3 tag too`() {
        val header = SourceHeaders.parse(flacHeader(96_000, 2, 24, 23_000_000))!!
        assertEquals("FLAC", header.codec)
        assertEquals(96_000, header.sampleRateHz)
        assertEquals(2, header.channels)
        assertEquals(24, header.bitsPerSample)
        assertEquals(23_000_000L, header.totalSamples)

        val id3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 10) + ByteArray(10)
        assertEquals(16, SourceHeaders.parse(id3 + flacHeader(44_100, 2, 16, 1_000))!!.bitsPerSample)
    }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())

    private fun le32(v: Int) = le16(v) + le16(v ushr 16)

    @Test
    fun `wav fmt chunk, including extensible valid bits`() {
        fun wav(
            format: Int,
            bits: Int,
            validBits: Int? = null,
        ): ByteArray {
            val fmt = le16(format) + le16(2) + le32(48_000) + le32(48_000 * 2 * bits / 8) + le16(2 * bits / 8) + le16(bits)
            val ext = validBits?.let { le16(22) + le16(it) + ByteArray(20) } ?: ByteArray(0)
            val body = fmt + ext
            return "RIFF".toByteArray() + le32(100) + "WAVE".toByteArray() + "LIST".toByteArray() + le32(2) + ByteArray(2) +
                "fmt ".toByteArray() + le32(body.size) + body
        }
        assertEquals(24, SourceHeaders.parse(wav(1, 24))!!.bitsPerSample)
        assertEquals("PCM float", SourceHeaders.parse(wav(3, 32))!!.codec)
        assertEquals(20, SourceHeaders.parse(wav(0xFFFE, 24, validBits = 20))!!.bitsPerSample)
    }

    @Test
    fun `mp3 frame header and its xing or info tag`() {
        fun frame(tag: String?): ByteArray {
            // MPEG-1 Layer III, 192 kbps, 44.1 kHz, joint stereo; side info 32 bytes.
            val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0xB0.toByte(), 0x40)
            val tagBytes = tag?.toByteArray() ?: ByteArray(4)
            return header + ByteArray(32) + tagBytes + ByteArray(200)
        }
        val vbr = SourceHeaders.parse(frame("Xing"))!!
        assertEquals("MP3", vbr.codec)
        assertEquals(44_100, vbr.sampleRateHz)
        assertEquals(2, vbr.channels)
        assertEquals(192, vbr.firstFrameBitrateKbps)
        assertEquals("VBR", vbr.bitrateMode)
        assertNull(vbr.bitsPerSample) // lossy: no stored bit depth
        assertEquals("CBR", SourceHeaders.parse(frame("Info"))!!.bitrateMode)
        assertNull(SourceHeaders.parse(frame(null))!!.bitrateMode) // not stated: not guessed
        assertNull(SourceHeaders.parse(ByteArray(64) { 0x41 }))
        assertNull(SourceHeaders.parse(ByteArray(0)))
    }

    @Test
    fun `audio track config maps to what was configured`() {
        val config = AudioSink.AudioTrackConfig(C.ENCODING_PCM_16BIT, 48_000, 0x0C, false, false, 38_400)
        val summary = PlayerDiagnosticsTracker.summarize(config)
        assertEquals("PCM 16-bit", summary.encoding)
        assertEquals(48_000, summary.sampleRateHz)
        assertEquals(2, summary.channelCount)
        assertFalse(summary.offload)
        val offload = PlayerDiagnosticsTracker.summarize(AudioSink.AudioTrackConfig(C.ENCODING_MP3, 44_100, 0x0C, false, true, 0))
        assertTrue(offload.offload)
        assertNull(offload.bufferBytes)
    }

    /** A Player that answers every query with an empty default. */
    private fun fakePlayer(): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> Player.STATE_IDLE
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 1f
                PlaybackParameters::class.java -> PlaybackParameters.DEFAULT
                Timeline::class.java -> Timeline.EMPTY
                else -> null
            }
        } as Player

    private val eventTime = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)

    @Test
    fun `track and decoder changes update the state, and stop removes nothing it did not add`() {
        val tracker = PlayerDiagnosticsTracker(fakePlayer())
        tracker.start() // not an ExoPlayer: no listener attached, nothing breaks
        val listener = tracker.analyticsListener
        listener.onAudioInputFormatChanged(eventTime, Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG).setSampleRate(44_100).build(), null)
        listener.onAudioDecoderInitialized(eventTime, "c2.android.mp3.decoder", 1L, 2L)
        listener.onAudioTrackInitialized(eventTime, AudioSink.AudioTrackConfig(C.ENCODING_PCM_16BIT, 44_100, 0x0C, false, false, 1))
        var state = tracker.state.value
        assertEquals(MimeTypes.AUDIO_MPEG, state.inputFormat?.sampleMimeType)
        assertEquals("c2.android.mp3.decoder", state.decoderName)
        assertEquals(44_100, state.audioTrack?.sampleRateHz)

        // Next track: another format; the output track is released and re-created.
        listener.onAudioInputFormatChanged(eventTime, Format.Builder().setSampleMimeType(MimeTypes.AUDIO_FLAC).setSampleRate(96_000).build(), null)
        listener.onAudioTrackReleased(eventTime, AudioSink.AudioTrackConfig(C.ENCODING_PCM_16BIT, 44_100, 0x0C, false, false, 1))
        state = tracker.state.value
        assertEquals(96_000, state.inputFormat?.sampleRateHz)
        assertNull(state.audioTrack)
        listener.onAudioUnderrun(eventTime, 0, 0, 0)
        listener.onPlaybackParametersChanged(eventTime, PlaybackParameters(1.5f))
        assertEquals(1, tracker.state.value.underruns)
        assertEquals(1.5f, tracker.state.value.speed)
        tracker.stop()
        tracker.stop() // idempotent
    }

    @Test
    fun `output device types and the estimated route`() {
        assertEquals(OutputKind.BLUETOOTH, OutputDevices.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(OutputKind.USB, OutputDevices.kindOf(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(OutputKind.WIRED, OutputDevices.kindOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(OutputKind.SPEAKER, OutputDevices.kindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(OutputKind.BLUETOOTH, OutputDevices.kindOf(26)) // LE Audio headset (newer API constant)
        assertEquals(OutputKind.OTHER, OutputDevices.kindOf(9_999))

        fun device(kind: OutputKind) = OutputDevice(kind, kind.label, null, emptyList(), emptyList(), emptyList())
        val speakerOnly = listOf(device(OutputKind.SPEAKER), device(OutputKind.EARPIECE))
        assertEquals(OutputKind.SPEAKER, OutputDevices.likelyRoute(speakerOnly).single().kind)
        // Headphones plugged in, then Bluetooth connected: the route follows.
        assertEquals(OutputKind.WIRED, OutputDevices.likelyRoute(speakerOnly + device(OutputKind.WIRED)).single().kind)
        assertEquals(OutputKind.BLUETOOTH, OutputDevices.likelyRoute(speakerOnly + device(OutputKind.WIRED) + device(OutputKind.BLUETOOTH)).single().kind)
        assertTrue(OutputDevices.likelyRoute(emptyList()).isEmpty())
    }

    @Test
    fun `the report is local, sanitized and says what is unknown`() {
        val snapshot =
            DiagnosticsSnapshot(
                source =
                    SourceDiagnostics(
                        title = "Song\u0000\nTitle",
                        artist = "A",
                        album = "B",
                        container = Fact("FLAC", Provenance.LIBRARY),
                        codec = Fact("FLAC", Provenance.FILE_HEADER),
                        sampleRate = Fact("96 kHz", Provenance.FILE_HEADER),
                        channels = null,
                        bitDepth = Fact("24-bit", Provenance.FILE_HEADER),
                        bitDepthNote = null,
                        bitrate = null,
                        bitrateMode = null,
                        duration = null,
                        fileSize = null,
                    ),
                player = PlayerDiagnostics(),
                decoder = null,
                eq = EqStage(StageStatus.OFF, "Turned off", emptyList()),
                headroom = null,
                peak = null,
                output =
                    OutputDiagnostics(
                        route = listOf(OutputDevice(OutputKind.BLUETOOTH, "Bluetooth (A2DP)", "Alex's AirPods", listOf(48_000), listOf(2), emptyList())),
                        routeSource = Provenance.ESTIMATE,
                        connected = emptyList(),
                        spatializer = null,
                    ),
            )
        val report = DiagnosticsReport.build(snapshot)
        assertTrue(report.contains("Title: Song  Title"))
        assertTrue(report.contains("Bit depth: 24-bit (File header"))
        assertTrue(report.contains("Channels: unavailable"))
        assertTrue(report.contains("Route: Bluetooth (A2DP) (estimated)"))
        assertTrue(report.contains("sample-peak limiter"))
        assertTrue(report.contains("Audio & Equalizer"))
        assertFalse(report.contains("AirPods")) // device names can be personal
        assertFalse(report.contains("content://") || report.contains("/storage/"))
        assertFalse(report.contains("\u0000"))
        assertEquals(120, DiagnosticsReport.clean("x".repeat(500)).length)
    }
}
