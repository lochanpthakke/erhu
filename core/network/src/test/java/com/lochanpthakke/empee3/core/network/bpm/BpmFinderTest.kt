package com.lochanpthakke.empee3.core.network.bpm

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.network.api.AcousticBrainzApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class BpmFinderTest {
    private object SilentLogger : Logger {
        override fun v(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    private val recording = "96685213-a25c-4678-9a13-abd9ec81cf35"

    /** The live answer for that recording (Pink Floyd, "Speak to Me / Breathe"), 2026-10-15, trimmed. */
    private val speakToMe =
        """{"$recording":{"0":{"metadata":{"audio_properties":{"codec":"mp3","length":240.509384155}},""" +
            """"rhythm":{"bpm":128.347702026}}},"mbid_mapping":{}}"""

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private class FakeAcousticBrainz(
        private val answer: () -> JsonObject,
    ) : AcousticBrainzApi {
        var calls = 0

        override suspend fun lowLevel(
            recordingIds: String,
            features: String,
        ): JsonObject {
            calls++
            return answer()
        }
    }

    private val rate = 11_025

    /** 60 s of a kick and a snare on alternate beats at [bpm]. */
    private fun beat(bpm: Double): Pcm {
        val samples = FloatArray(60 * rate)
        var t = 0.0
        var n = 0
        while (t < 60) {
            val at = (t * rate).toInt()
            for (i in 0 until 1500) {
                if (at + i >= samples.size) break
                val s = i.toDouble() / rate
                val kick = sin(2 * PI * 55 * s) * exp(-s / 0.04)
                val click = if (i < 120) ((i * 7919) % 200 - 100) / 100.0 * 0.4 * exp(-s / 0.003) else 0.0
                val snare = if (n % 2 == 1 && i < 900) ((i * 104729) % 200 - 100) / 100.0 * 0.5 * exp(-s / 0.02) else 0.0
                samples[at + i] += (0.9 * kick + click + snare).toFloat()
            }
            t += 60.0 / bpm
            n++
        }
        return Pcm(samples, rate)
    }

    private val mbMatch = MetadataCandidate("mb_$recording", "Breathe", "Pink Floyd", "The Dark Side of the Moon", sourceName = "MusicBrainz")
    private val deezerMatch = MetadataCandidate("deezer_1", "Song", "Artist", "Album", sourceName = "Deezer")

    private fun find(
        match: MetadataCandidate,
        api: AcousticBrainzApi,
        pcm: PcmSource,
    ) = runBlocking { BpmFinder(api, pcm, SilentLogger).find(match, "/music/song.mp3") }

    private val noAudio = PcmSource { throw IOException("not decodable") }

    @Test
    fun acousticBrainzAnswer_isParsed() {
        assertEquals(AcousticBrainzBpm.Measured(128, 1), bpmFromAcousticBrainz(json(speakToMe), recording))
        val unknown = bpmFromAcousticBrainz(json("""{"mbid_mapping":{}}"""), recording)
        assertTrue(unknown is AcousticBrainzBpm.None)
        val agreeing = json("""{"$recording":{"0":{"rhythm":{"bpm":120.2}},"1":{"rhythm":{"bpm":119.6}},"2":{"rhythm":{"bpm":121.0}}}}""")
        assertEquals(AcousticBrainzBpm.Measured(120, 3), bpmFromAcousticBrainz(agreeing, recording))
    }

    @Test
    fun acousticBrainzAnalysesThatDisagree_giveNoValue() {
        val octave = json("""{"$recording":{"0":{"rhythm":{"bpm":86.1}},"1":{"rhythm":{"bpm":172.3}}}}""")
        val result = bpmFromAcousticBrainz(octave, recording) as AcousticBrainzBpm.None
        assertEquals("AcousticBrainz's analyses disagree (86, 172 BPM)", result.reason)
        val junk = json("""{"$recording":{"0":{"rhythm":{"bpm":0.0}}}}""")
        assertTrue(bpmFromAcousticBrainz(junk, recording) is AcousticBrainzBpm.None)
    }

    @Test
    fun aMeasuredTempo_isUsed_withoutAnalysingTheAudio() {
        var decoded = false
        val api = FakeAcousticBrainz { json(speakToMe) }
        val finder =
            BpmFinder(
                api,
                PcmSource {
                    decoded = true
                    beat(100.0)
                },
                SilentLogger,
            )
        val result = runBlocking { finder.find(mbMatch, "/music/song.mp3") }
        // An estimate too: it never replaces a BPM the file has.
        assertEquals(BpmLookup.Found(128, "AcousticBrainz", estimated = true), result)
        assertTrue("no analysis needed", !decoded)
        // Cached for the session.
        runBlocking { finder.find(mbMatch, "/music/other.mp3") }
        assertEquals(1, api.calls)
    }

    @Test
    fun withoutAMeasuredTempo_theAudioIsAnalysedOnTheDevice() {
        val result = find(mbMatch, FakeAcousticBrainz { json("""{"mbid_mapping":{}}""") }, PcmSource { beat(110.0) })
        result as BpmLookup.Found
        assertEquals(110, result.bpm)
        assertTrue(result.estimated)
        assertEquals("on-device estimate, 3 of 3 sections agree", result.source)
    }

    @Test
    fun aDeezerMatch_orANetworkFailure_stillGetsAnEstimate() {
        assertEquals(120, (find(deezerMatch, FakeAcousticBrainz { error("not called") }, PcmSource { beat(120.0) }) as BpmLookup.Found).bpm)
        val offline = FakeAcousticBrainz { throw IOException("Unable to resolve host acousticbrainz.org") }
        assertEquals(120, (find(mbMatch, offline, PcmSource { beat(120.0) }) as BpmLookup.Found).bpm)
    }

    @Test
    fun noDependableTempo_isNotFound_withTheReasons() {
        val silence = PcmSource { Pcm(FloatArray(60 * rate), rate) }
        val result = find(mbMatch, FakeAcousticBrainz { json("""{"mbid_mapping":{}}""") }, silence) as BpmLookup.NotFound
        assertEquals("AcousticBrainz has no analysis of this recording; on-device estimate: No steady beat found", result.reason)
    }

    @Test
    fun everySourceFailing_isAFailure_notAMissingValue() {
        val offline = FakeAcousticBrainz { throw IOException("Unable to resolve host acousticbrainz.org") }
        val finder = BpmFinder(offline, noAudio, SilentLogger)
        val result = runBlocking { finder.find(mbMatch, "/music/song.mp3") }
        assertTrue("$result", result is BpmLookup.Failed)
        assertTrue(runBlocking { finder.find(deezerMatch, "/music/song.mp3") } is BpmLookup.Failed)
        // A network failure is not cached: the next lookup asks again.
        runBlocking { finder.find(mbMatch, "/music/song.mp3") }
        assertEquals(2, offline.calls)
    }
}
