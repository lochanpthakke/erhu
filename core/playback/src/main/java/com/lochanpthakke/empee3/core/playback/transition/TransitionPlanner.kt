package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.TransitionSpec

/** What the planner needs to know about the current queue boundary. */
internal data class BoundaryInfo(
    /** The transition the settings give for this exact boundary (see TransitionResolver), or null. */
    val spec: TransitionSpec?,
    /** Queue index of the next entry in play order, or -1 when nothing follows. */
    val nextIndex: Int,
    val repeatMode: RepeatMode,
    val abLoopEnabled: Boolean,
    val outgoingDurationMs: Long,
    val incomingDurationMs: Long,
)

/** Why a boundary plays the normal way (no transition). */
internal enum class NoTransitionReason {
    /** The settings say no transition here (master off, Off at some level, or a default of Off). */
    NOT_CONFIGURED,

    /** Repeat one replays the same track: the existing behaviour is kept. */
    REPEAT_ONE,

    /** An A–B loop owns the end of this track. */
    AB_LOOP,

    /** Last entry and repeat is off. */
    NO_NEXT,

    /** A duration is not known yet. */
    UNKNOWN_DURATION,

    /** One of the tracks is too short for a transition. */
    TOO_SHORT,
}

internal sealed interface TransitionDecision {
    data class Planned(
        val nextIndex: Int,
        /** Position in the outgoing track where the transition starts. */
        val startAtMs: Long,
        /** Effective duration (the setting, shortened to fit both tracks). */
        val durationMs: Int,
        val spec: TransitionSpec,
        /** Where the incoming track starts (its cue point; 0 = the beginning). */
        val incomingStartMs: Long = 0L,
    ) : TransitionDecision

    data class None(
        val reason: NoTransitionReason,
    ) : TransitionDecision
}

/**
 * Decides whether and how the boundary after the current entry is crossed. Pure: the same input
 * always gives the same decision, so every track-boundary case is testable without a player.
 */
internal object TransitionPlanner {
    /** Transitions shorter than this are skipped rather than squeezed in. */
    const val MIN_EFFECTIVE_MS = 300

    @Suppress("ReturnCount")
    fun plan(info: BoundaryInfo): TransitionDecision {
        val spec = info.spec?.sanitized() ?: return none(NoTransitionReason.NOT_CONFIGURED)
        if (info.repeatMode == RepeatMode.ONE) return none(NoTransitionReason.REPEAT_ONE)
        if (info.abLoopEnabled) return none(NoTransitionReason.AB_LOOP)
        if (info.nextIndex < 0) return none(NoTransitionReason.NO_NEXT)
        if (info.outgoingDurationMs <= 0 || info.incomingDurationMs <= 0) return none(NoTransitionReason.UNKNOWN_DURATION)
        val duration = spec.effectiveDurationMs(info.outgoingDurationMs, info.incomingDurationMs)
        if (duration < MIN_EFFECTIVE_MS) return none(NoTransitionReason.TOO_SHORT)
        // The transition ends at the outgoing cue point (the track's end unless part of it is cut).
        return TransitionDecision.Planned(
            nextIndex = info.nextIndex,
            startAtMs = spec.outgoingEndMs(info.outgoingDurationMs) - duration,
            durationMs = duration,
            spec = spec,
            incomingStartMs = spec.incomingStartMs.toLong(),
        )
    }

    private fun none(reason: NoTransitionReason) = TransitionDecision.None(reason)
}
