package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.TransitionCurve
import com.lochanpthakke.empee3.core.model.TransitionSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionPlannerTest {
    private val spec = TransitionSpec(durationMs = 4_000, curve = TransitionCurve.LINEAR)

    private fun info(
        spec: TransitionSpec? = this.spec,
        nextIndex: Int = 1,
        repeat: RepeatMode = RepeatMode.OFF,
        abLoop: Boolean = false,
        outgoing: Long = 200_000,
        incoming: Long = 180_000,
    ) = BoundaryInfo(spec, nextIndex, repeat, abLoop, outgoing, incoming)

    private fun reason(info: BoundaryInfo) = (TransitionPlanner.plan(info) as TransitionDecision.None).reason

    @Test
    fun `a configured boundary crosses over at duration minus the transition`() {
        assertEquals(TransitionDecision.Planned(1, 196_000, 4_000, spec), TransitionPlanner.plan(info()))
    }

    @Test
    fun `no setting, repeat one, loops and the last track keep the normal boundary`() {
        assertEquals(NoTransitionReason.NOT_CONFIGURED, reason(info(spec = null)))
        assertEquals(NoTransitionReason.REPEAT_ONE, reason(info(repeat = RepeatMode.ONE)))
        assertEquals(NoTransitionReason.AB_LOOP, reason(info(abLoop = true)))
        assertEquals(NoTransitionReason.NO_NEXT, reason(info(nextIndex = -1)))
        // Repeat all wraps to the first entry: that boundary is crossed like any other.
        assertEquals(0, (TransitionPlanner.plan(info(nextIndex = 0, repeat = RepeatMode.ALL)) as TransitionDecision.Planned).nextIndex)
    }

    @Test
    fun `durations longer than either track are shortened, very short tracks are skipped`() {
        val shortIncoming = TransitionPlanner.plan(info(incoming = 5_000)) as TransitionDecision.Planned
        assertEquals(2_500, shortIncoming.durationMs)
        assertEquals(197_500, shortIncoming.startAtMs)
        val shortOutgoing = TransitionPlanner.plan(info(outgoing = 3_000)) as TransitionDecision.Planned
        assertEquals(1_500, shortOutgoing.durationMs)
        assertEquals(1_500, shortOutgoing.startAtMs) // never before the middle of the outgoing track
        assertEquals(NoTransitionReason.TOO_SHORT, reason(info(incoming = 400)))
        assertEquals(NoTransitionReason.UNKNOWN_DURATION, reason(info(outgoing = 0)))
    }

    @Test
    fun `cue points place the transition and fit it to what plays`() {
        val cued = TransitionPlanner.plan(info(spec = spec.copy(outgoingCutMs = 30_000, incomingStartMs = 12_000))) as TransitionDecision.Planned
        assertEquals(166_000, cued.startAtMs) // ends at 170 s instead of 200 s
        assertEquals(12_000L, cued.incomingStartMs)
        assertEquals(4_000, cued.durationMs)
        // Only 5 s of the incoming track left after its cue: the transition is shortened to half of that.
        val late = TransitionPlanner.plan(info(spec = spec.copy(incomingStartMs = 175_000))) as TransitionDecision.Planned
        assertEquals(2_500, late.durationMs)
        // A cue beyond the end of a track leaves nothing to cross into: normal boundary.
        assertEquals(NoTransitionReason.TOO_SHORT, reason(info(spec = spec.copy(incomingStartMs = 200_000))))
    }
}
