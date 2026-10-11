package com.lochanpthakke.empee3.core.model

/**
 * A compact amplitude envelope of a track's decoded audio: [levels] are evenly spaced over the
 * track's duration, each in 0..1 (loudest part of the track = 1). A few hundred values, never PCM.
 */
class Waveform(
    val levels: FloatArray,
) {
    val size: Int get() = levels.size

    override fun equals(other: Any?): Boolean = other is Waveform && levels.contentEquals(other.levels)

    override fun hashCode(): Int = levels.contentHashCode()
}

/** Loading state of a track's waveform. Playback never depends on it. */
sealed interface WaveformLoad {
    data object Loading : WaveformLoad

    data class Ready(
        val waveform: Waveform,
    ) : WaveformLoad

    /** No waveform for this track: missing file, no permission, unsupported codec or no audio. */
    data object Unavailable : WaveformLoad
}

/**
 * Mapping between track time and a horizontal position on a waveform. Both ends are exact:
 * position 0 is the start of the track, the full width is its end.
 */
object WaveformTimeline {
    /** Fraction 0..1 of the track at [positionMs]; 0 when the duration is unknown. */
    fun fractionOf(
        positionMs: Long,
        durationMs: Long,
    ): Float = if (durationMs <= 0L) 0f else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()

    /** Track position at [fraction] (clamped to 0..1) of the duration. */
    fun positionAt(
        fraction: Float,
        durationMs: Long,
    ): Long = if (durationMs <= 0L) 0L else Math.round(fraction.coerceIn(0f, 1f).toDouble() * durationMs)

    /** Track position under [x] on a waveform [width] pixels wide (outside the bar: the nearest end). */
    fun positionAtX(
        x: Float,
        width: Float,
        durationMs: Long,
    ): Long = if (width <= 0f) 0L else positionAt(x / width, durationMs)
}

/**
 * Levels of a short window of a track split into three frequency bands (lows below ~250 Hz, mids,
 * highs above ~4 kHz), as absolute RMS (full scale = 1) so two tracks compare honestly. Bins are
 * evenly spaced over the window.
 */
class BandLevels(
    val low: FloatArray,
    val mid: FloatArray,
    val high: FloatArray,
) {
    val size: Int get() = low.size

    override fun equals(other: Any?): Boolean =
        other is BandLevels && low.contentEquals(other.low) && mid.contentEquals(other.mid) && high.contentEquals(other.high)

    override fun hashCode(): Int = 31 * (31 * low.contentHashCode() + mid.contentHashCode()) + high.contentHashCode()
}

/** Loading state of a band waveform window. Playback never depends on it. */
sealed interface BandWaveformLoad {
    data object Loading : BandWaveformLoad

    data class Ready(
        val levels: BandLevels,
    ) : BandWaveformLoad

    data object Unavailable : BandWaveformLoad
}
