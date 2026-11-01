package com.erhu.erhu.core.sound.dsp

import com.erhu.erhu.core.sound.model.SoundSettings
import kotlin.math.exp
import kotlin.math.ln

/** What the equalizer processor reports about itself right now (see CustomEqAudioProcessor.runtimeState). */
data class EqRuntimeState(
    val enabled: Boolean,
    /** The player configured it for the current stream. False before playback or for audio it cannot process. */
    val configured: Boolean,
    val sampleRateHz: Int?,
    val channelCount: Int?,
    val allGainsZero: Boolean,
)

enum class StageStatus(
    val label: String,
) {
    /** Changing the audio now. */
    ACTIVE("Active"),

    /** In the chain but passing audio through unchanged (e.g. all gains at 0 dB). */
    BYPASSED("Bypassed"),

    /** Turned off by the user. */
    OFF("Off"),

    /** Not in use for the current audio (nothing playing yet, or a format it does not process). */
    IDLE("Not in use"),
}

/** The equalizer stage: real runtime state, the settings it applies, and what it does. */
data class EqStage(
    val status: StageStatus,
    val reason: String,
    val activeBands: List<BandDescription>,
)

data class BandDescription(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float,
    val type: FilterType,
    /** Above 45% of the sample rate the processor skips the band. */
    val skippedAtThisSampleRate: Boolean,
)

enum class ClippingRisk(
    val label: String,
) {
    NONE("None from the app"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}

/**
 * A prediction from the EQ settings, not a measurement: the most the EQ curve raises any frequency,
 * so the most it can push a full-scale signal over full scale.
 */
data class HeadroomEstimate(
    val maxBoostDb: Float,
    val atFrequencyHz: Float?,
    val risk: ClippingRisk,
    val advice: String,
)

/** Pure description of the app's own processing; it never changes it. */
object DspDiagnostics {
    private const val BAND_TO_SAMPLE_RATE_LIMIT = 0.45f
    private const val DEFAULT_SAMPLE_RATE = 48_000
    private const val SWEEP_POINTS = 240
    private const val MIN_SWEEP_HZ = 20f
    private const val MAX_SWEEP_HZ = 20_000f
    private const val LOW_RISK_DB = 3f
    private const val MODERATE_RISK_DB = 6f
    private const val EPSILON_DB = 0.05f
    private const val HZ_PER_KHZ = 1000f

    fun eqStage(
        runtime: EqRuntimeState,
        settings: SoundSettings,
    ): EqStage {
        val sampleRate = runtime.sampleRateHz ?: DEFAULT_SAMPLE_RATE
        val bands =
            settings.bands.take(settings.activeBandCount).map {
                BandDescription(it.frequencyHz, it.gainDb, it.q, it.filterType, it.frequencyHz >= sampleRate * BAND_TO_SAMPLE_RATE_LIMIT)
            }
        val (status, reason) =
            when {
                !runtime.enabled -> StageStatus.OFF to "Turned off in Audio & Equalizer"
                !runtime.configured -> StageStatus.IDLE to "Not configured for the current audio (nothing playing, or not 16-bit PCM)"
                runtime.allGainsZero -> StageStatus.BYPASSED to "All gains are 0 dB: audio passes through unchanged"
                else -> StageStatus.ACTIVE to "Filtering the audio with ${bands.size} bands"
            }
        return EqStage(status, reason, bands)
    }

    /**
     * Largest boost of the EQ curve over 20 Hz - 20 kHz, computed with the same filters the processor
     * uses (bands above its limit ignored, as it does). Zero when the EQ is not changing the audio.
     */
    fun headroom(
        stage: EqStage,
        sampleRateHz: Int?,
    ): HeadroomEstimate {
        if (stage.status != StageStatus.ACTIVE) {
            return HeadroomEstimate(0f, null, ClippingRisk.NONE, "The app is not adding gain, so its processing adds no clipping risk.")
        }
        val rate = sampleRateHz ?: DEFAULT_SAMPLE_RATE
        val filters =
            stage.activeBands.filterNot { it.skippedAtThisSampleRate }.map { band ->
                BiquadFilter().apply { configure(band.frequencyHz, rate, band.gainDb, band.q, band.type) }
            }
        var maxDb = Float.NEGATIVE_INFINITY
        var atHz = MIN_SWEEP_HZ
        val top = minOf(MAX_SWEEP_HZ, rate / 2f)
        for (i in 0 until SWEEP_POINTS) {
            val hz = exp(ln(MIN_SWEEP_HZ) + (ln(top) - ln(MIN_SWEEP_HZ)) * i / (SWEEP_POINTS - 1))
            val db = filters.sumOf { it.getFrequencyResponse(hz, rate).toDouble() }.toFloat()
            if (db > maxDb) {
                maxDb = db
                atHz = hz
            }
        }
        val boost = maxDb.coerceAtLeast(0f)
        val risk =
            when {
                boost <= EPSILON_DB -> ClippingRisk.NONE
                boost <= LOW_RISK_DB -> ClippingRisk.LOW
                boost <= MODERATE_RISK_DB -> ClippingRisk.MODERATE
                else -> ClippingRisk.HIGH
            }
        val advice =
            if (risk == ClippingRisk.NONE) {
                "The EQ only cuts, so it cannot push audio over full scale."
            } else {
                String.format(
                    java.util.Locale.ROOT,
                    "The EQ raises up to %+.1f dB around %s. Loud, already-mastered music can exceed full scale there; the EQ then " +
                        "clips those samples. The app has no preamp, so to keep headroom lower the boosted bands or cut the others by about %.1f dB.",
                    boost,
                    if (atHz >= HZ_PER_KHZ) String.format(java.util.Locale.ROOT, "%.1f kHz", atHz / HZ_PER_KHZ) else "${atHz.toInt()} Hz",
                    boost,
                )
            }
        return HeadroomEstimate(boost, atHz.takeIf { risk != ClippingRisk.NONE }, risk, advice)
    }
}
