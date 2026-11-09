/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Licensed under the GNU General Public License v3.0.
 * See the LICENSE file in the project root.
 */

package com.erhu.erhu.core.sound.dsp

import com.erhu.erhu.core.model.AudioUtilities
import com.erhu.erhu.core.model.ReplayGainMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Embedded ReplayGain only: missing album tags never silently switch to track normalization. */
data class ReplayGainData(
    val trackDb: Float? = null,
    val albumDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
) {
    fun gain(mode: ReplayGainMode): Float {
        val db =
            when (mode) {
                ReplayGainMode.OFF -> null
                ReplayGainMode.TRACK -> trackDb
                ReplayGainMode.ALBUM -> albumDb
            }
        if (db == null) return 1f
        val peak = if (mode == ReplayGainMode.ALBUM) albumPeak else trackPeak
        val gain = 10.0.pow(db / 20.0).toFloat()
        return if (peak != null && peak > 0f) min(gain, 0.99f / peak) else gain
    }

    companion object {
        fun fromTags(tags: Map<String, String>): ReplayGainData {
            val values = tags.mapKeys { it.key.lowercase(java.util.Locale.ROOT) }

            fun number(
                name: String,
                min: Float,
                max: Float,
            ): Float? =
                values[name]
                    ?.trim()
                    ?.replace(Regex("(?i)\\s*dB$"), "")
                    ?.toFloatOrNull()
                    ?.takeIf { it.isFinite() && it in min..max }
            return ReplayGainData(
                number("replaygain_track_gain", -30f, 20f),
                number("replaygain_album_gain", -30f, 20f),
                number("replaygain_track_peak", 0.000001f, 100f),
                number("replaygain_album_peak", 0.000001f, 100f),
            )
        }
    }
}

/** Runs inside the existing EQ's float frame, before its one final PCM conversion. No per-frame allocation. */
internal class AudioUtilityDsp {
    @Volatile var settings = AudioUtilities()

    @Volatile var replayGain = ReplayGainData()
    private var rate = 44100
    private var bass = emptyArray<BiquadFilter>()
    private var bassDb = Float.NaN
    private var amplitude = 1f
    private var mono = 0f
    private var balance = 0f
    private var targetGain = 1f
    private var smooth = 0.001f
    private var release = 0.999f
    private var limitGain = 1f
    private var initialized = false
    private var blockSettings = AudioUtilities()

    fun flush(
        sampleRate: Int,
        channels: Int,
    ) {
        rate = sampleRate
        bass = Array(channels) { BiquadFilter() }
        bassDb = Float.NaN
        initialized = false
        limitGain = 1f
        smooth = (1.0 - exp(-1.0 / (0.01 * rate))).toFloat()
        release = exp(-1.0 / (0.08 * rate)).toFloat()
    }

    fun beginBlock(): Boolean {
        val s = settings
        blockSettings = s
        targetGain = 10.0.pow(s.preampDb / 20.0).toFloat() * replayGain.gain(s.replayGain)
        if (s.bassDb != bassDb) {
            bassDb = s.bassDb
            bass.forEach { it.configure(120f.coerceAtMost(rate * 0.2f), rate, bassDb, 0.707f, FilterType.LOW_SHELF) }
        }
        if (!initialized) {
            amplitude = targetGain
            mono = if (s.mono) 1f else 0f
            balance = s.balance
            initialized = true
        }
        return s.mono ||
            mono > 0.00001f ||
            s.balance != 0f ||
            abs(balance) > 0.00001f ||
            s.preampDb != 0f ||
            abs(amplitude - 1f) > 0.00001f ||
            s.bassDb != 0f ||
            s.limiter ||
            targetGain != 1f
    }

    fun beforeEq(frame: FloatArray) {
        val s = blockSettings
        amplitude += (targetGain - amplitude) * smooth
        mono += ((if (s.mono) 1f else 0f) - mono) * smooth
        balance += (s.balance - balance) * smooth
        var mean = 0f
        for (sample in frame) mean += sample / frame.size
        for (ch in frame.indices) {
            val sample = (frame[ch] + (mean - frame[ch]) * mono) * amplitude
            frame[ch] = if (bassDb != 0f) bass[ch].processSample(sample) else sample
        }
    }

    fun afterEq(frame: FloatArray) {
        if (frame.size >= 2) {
            frame[0] *= 1f - balance.coerceAtLeast(0f)
            frame[1] *= 1f + balance.coerceAtMost(0f)
        }
        if (!blockSettings.limiter) {
            limitGain = 1f
            return
        }
        var peak = 0f
        for (sample in frame) peak = maxOf(peak, abs(sample))
        val required = if (peak > 0.89125f) 0.89125f / peak else 1f // -1 dBFS sample-peak ceiling.
        limitGain = if (required < limitGain) required else required + (limitGain - required) * release
        for (ch in frame.indices) frame[ch] *= limitGain
    }
}
