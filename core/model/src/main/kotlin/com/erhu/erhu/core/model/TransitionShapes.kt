package com.erhu.erhu.core.model

import kotlin.math.pow

/** Filter type of a filter sweep. */
enum class PassType { HIGH_PASS, LOW_PASS }

/** A filter at one moment of a transition: [type] at cutoff [hz]. */
data class FilterSetting(
    val type: PassType,
    val hz: Float,
)

/**
 * Filter sweeps a transition can play, each on the track(s) it names. A high-pass "in" thins the
 * incoming track at first and opens it up by the middle; a high-pass "out" thins the outgoing track
 * through the second half; a low-pass "out" darkens it. Executed in the transition's own audio stage.
 */
enum class TransitionFilter(
    val label: String,
) {
    NONE("None"),
    HIGH_PASS_IN("High-pass in"),
    HIGH_PASS_OUT("High-pass out"),
    HIGH_PASS_IN_OUT("High-pass in, high-pass out"),
    HIGH_PASS_IN_LOW_PASS_OUT("High-pass in, low-pass out"),
    LOW_PASS_OUT("Low-pass out"),
    ;

    private val incomingHighPass get() = this == HIGH_PASS_IN || this == HIGH_PASS_IN_OUT || this == HIGH_PASS_IN_LOW_PASS_OUT
    private val outgoingHighPass get() = this == HIGH_PASS_OUT || this == HIGH_PASS_IN_OUT
    private val outgoingLowPass get() = this == LOW_PASS_OUT || this == HIGH_PASS_IN_LOW_PASS_OUT

    /** Whether [role]'s track is filtered at all. */
    fun affects(role: TransitionRole): Boolean =
        if (role == TransitionRole.INCOMING) incomingHighPass else outgoingHighPass || outgoingLowPass

    /**
     * The filter on [role]'s track at progress [t], or null when that track is not filtered. While a
     * track is filtered the filter stays in (at a transparent cutoff outside its sweep), so it never
     * switches on or off in the middle of the audio.
     */
    fun settingAt(
        role: TransitionRole,
        t: Float,
    ): FilterSetting? {
        val x = t.coerceIn(0f, 1f)
        return when {
            role == TransitionRole.INCOMING && incomingHighPass ->
                FilterSetting(PassType.HIGH_PASS, sweep(HIGH_PASS_THIN_HZ, HIGH_PASS_OPEN_HZ, x / HALF))
            role == TransitionRole.OUTGOING && outgoingHighPass ->
                FilterSetting(PassType.HIGH_PASS, sweep(HIGH_PASS_OPEN_HZ, HIGH_PASS_THIN_HZ, (x - HALF) / HALF))
            role == TransitionRole.OUTGOING && outgoingLowPass ->
                FilterSetting(PassType.LOW_PASS, sweep(LOW_PASS_OPEN_HZ, LOW_PASS_DARK_HZ, (x - HALF) / HALF))
            else -> null
        }
    }

    companion object {
        /** High-pass cutoffs: 20 Hz is practically open, 1.5 kHz leaves only the top of the mix. */
        const val HIGH_PASS_OPEN_HZ = 20f
        const val HIGH_PASS_THIN_HZ = 1_500f

        /** Low-pass cutoffs (the processor caps the open value below the Nyquist frequency). */
        const val LOW_PASS_OPEN_HZ = 18_000f
        const val LOW_PASS_DARK_HZ = 350f
        private const val HALF = 0.5f

        /** Exponential (even in pitch) from [from] to [to] as [u] goes 0..1; held at the ends. */
        fun sweep(
            from: Float,
            to: Float,
            u: Float,
        ): Float = from * (to / from).pow(u.coerceIn(0f, 1f))
    }
}

/**
 * Named volume shapes offered in the editor. Each produces the two curves the engine plays;
 * the end levels always respect the hand-over rules (see [VolumeCurve.isValid]).
 */
enum class VolumeShape(
    val label: String,
) {
    CROSSFADE("Crossfade"),
    LINEAR("Linear crossfade"),
    OVERLAP("Overlap"),
    FADE_OUT_THEN_IN("Fade out, then in"),
    CUT_IN_FADE_OUT("Cut in, fade out"),
    FADE_IN_CUT_OUT("Fade in, cut out"),
    CENTER_CUT("Center cut"),
    ;

    /** [spec] with this shape's volume curves; everything else is kept. */
    @Suppress("MagicNumber") // the shapes' breakpoints
    fun applyTo(spec: TransitionSpec): TransitionSpec =
        when (this) {
            CROSSFADE -> spec.copy(curve = TransitionCurve.EQUAL_POWER, outgoingCurve = null, incomingCurve = null)
            LINEAR -> spec.copy(curve = TransitionCurve.LINEAR, outgoingCurve = null, incomingCurve = null)
            OVERLAP -> custom(spec, out = listOf(0f to 1f, 0.97f to 1f, 1f to 0f), inc = listOf(0f to 0f, 0.03f to 1f, 1f to 1f))
            FADE_OUT_THEN_IN -> custom(spec, out = listOf(0f to 1f, 0.5f to 0f, 1f to 0f), inc = listOf(0f to 0f, 0.5f to 0f, 1f to 1f))
            CUT_IN_FADE_OUT -> custom(spec, out = listOf(0f to 1f, 1f to 0f), inc = listOf(0f to 1f, 1f to 1f))
            FADE_IN_CUT_OUT -> custom(spec, out = listOf(0f to 1f, 0.97f to 1f, 1f to 0f), inc = listOf(0f to 0f, 1f to 1f))
            CENTER_CUT ->
                custom(
                    spec,
                    out = listOf(0f to 1f, 0.48f to 1f, 0.52f to 0f, 1f to 0f),
                    inc =
                        listOf(
                            0f to 0f,
                            0.48f to 0f,
                            0.52f to 1f,
                            1f to 1f,
                        ),
                )
        }

    companion object {
        /** The named shape [spec]'s volume curves match, or null for hand-edited curves. */
        fun of(spec: TransitionSpec): VolumeShape? =
            entries.firstOrNull { shape ->
                val shaped = shape.applyTo(spec)
                shaped.curve == spec.curve && shaped.outgoingCurve == spec.outgoingCurve && shaped.incomingCurve == spec.incomingCurve
            }

        private fun custom(
            spec: TransitionSpec,
            out: List<Pair<Float, Float>>,
            inc: List<Pair<Float, Float>>,
        ) = spec.copy(
            curve = TransitionCurve.CUSTOM,
            outgoingCurve = VolumeCurve(out.map { (t, g) -> CurvePoint(t, g) }),
            incomingCurve = VolumeCurve(inc.map { (t, g) -> CurvePoint(t, g) }),
        )
    }
}

/** EQ moves offered in the editor: the bass swap and where it happens. */
@Suppress("MagicNumber") // the swap points
enum class EqMove(
    val label: String,
    /** Swap point, or null for no bass swap. */
    val swapAt: Float?,
) {
    NONE("None", null),
    START_BASS_SWAP("Start bass swap", 0.15f),
    CENTER_BASS_SWAP("Center bass swap", 0.5f),
    END_BASS_SWAP("End bass swap", 0.85f),
    ;

    fun applyTo(spec: TransitionSpec): TransitionSpec =
        if (swapAt ==
            null
        ) {
            spec.copy(bassSwap = false)
        } else {
            spec.copy(bassSwap = true, bassSwapAt = swapAt)
        }

    companion object {
        /** The named move [spec] matches, or null for a custom swap point. */
        fun of(spec: TransitionSpec): EqMove? = if (!spec.bassSwap) NONE else entries.firstOrNull { it.swapAt == spec.bassSwapAt }
    }
}
