/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Future Android releases from 1.0.18: proprietary terms in LICENSE.
 * Historical GPL grants are preserved in LICENSES/GPL-3.0-historical.txt.
 */

package com.erhu.erhu.core.model

/** Processing is opt-in. Playback parameters use Media3's pitch-preserving time stretcher. */
data class AudioUtilities(
    val mono: Boolean = false,
    val balance: Float = 0f,
    val preampDb: Float = 0f,
    val bassDb: Float = 0f,
    val limiter: Boolean = false,
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val skipSilence: Boolean = false,
    val sleepDeadlineMs: Long = 0,
    val sleepAtTrackEnd: Boolean = false,
) {
    fun sanitized(): AudioUtilities =
        copy(
            balance = balance.safe(0f, -1f, 1f),
            preampDb = preampDb.safe(0f, -24f, 12f),
            bassDb = bassDb.safe(0f, 0f, 12f),
            speed = speed.safe(1f, 0.5f, 2f),
            pitch = pitch.safe(1f, 0.5f, 2f),
            sleepDeadlineMs = sleepDeadlineMs.coerceAtLeast(0),
        )
}

enum class ReplayGainMode { OFF, TRACK, ALBUM }

private fun Float.safe(
    fallback: Float,
    min: Float,
    max: Float,
) = if (isFinite()) coerceIn(min, max) else fallback
