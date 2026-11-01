package com.erhu.erhu.feature.library.transition

import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.CurvePoint
import com.erhu.erhu.core.model.EqMove
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionResolver
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeCurve
import com.erhu.erhu.core.model.VolumeShape

/** One track on the timeline: the song and which occurrence of it this is. */
data class StudioTrack(
    val song: Song,
    val occurrence: TrackOccurrence,
)

/** The transition between two adjacent timeline tracks, with its own setting and what will play. */
data class StudioPair(
    val key: TransitionPairKey,
    val from: StudioTrack,
    val to: StudioTrack,
    val choice: TransitionChoice,
    /** What plays at this boundary when the collection is played in this order (null: no transition). */
    val resolved: TransitionSpec?,
)

/**
 * Pairs between adjacent tracks, in timeline order. A pair is keyed by its two occurrences (not by
 * song ids or list positions), so reordering, adding or removing tracks only changes which pairs
 * are adjacent; a setting never moves to a different pair, and the two copies of a duplicated song
 * keep separate settings.
 */
fun studioPairs(
    collection: CollectionRef,
    tracks: List<StudioTrack>,
    config: TransitionConfig,
): List<StudioPair> =
    tracks.zipWithNext { from, to ->
        val key = TransitionPairKey(collection, from.occurrence, to.occurrence)
        val resolved =
            TransitionResolver.resolve(config, QueueOrigin(collection, from.occurrence), QueueOrigin(collection, to.occurrence)).spec
        StudioPair(key, from, to, config.pair(key), resolved)
    }

/** Short label for a pair chip: Auto (inherits), Off, or the preset name. */
fun pairLabel(choice: TransitionChoice): String =
    when (choice) {
        TransitionChoice.Inherit -> "Auto"
        TransitionChoice.Off -> "Off"
        is TransitionChoice.Custom -> choice.spec.preset.label
    }

fun specSummary(spec: TransitionSpec?): String =
    if (spec == null) "No crossfade" else "${spec.preset.label} · ${TransitionSpec.durationLabel(spec.durationMs)}"

/**
 * Edits of a [TransitionSpec] made in the editor. Every function returns a valid spec: times stay
 * inside the transition and in order, gains stay within 0..1, fixed end levels stay fixed, and
 * editing one track's curve never changes the other's.
 */
object TransitionEditing {
    /** Part of a track that always plays, whatever the cue points. */
    const val MIN_PLAYED_MS = 1_000L

    fun applyPreset(
        spec: TransitionSpec,
        preset: TransitionPreset,
    ): TransitionSpec = TransitionSpec.preset(preset, spec).sanitized()

    fun setDuration(
        spec: TransitionSpec,
        durationMs: Int,
    ): TransitionSpec = spec.copy(durationMs = TransitionSpec.clampDuration(durationMs))

    fun setCurve(
        spec: TransitionSpec,
        curve: TransitionCurve,
    ): TransitionSpec =
        when (curve) {
            TransitionCurve.CUSTOM -> TransitionSpec.preset(TransitionPreset.CUSTOM, spec)
            else ->
                spec.copy(
                    curve = curve,
                    outgoingCurve = null,
                    incomingCurve = null,
                    preset = if (spec.preset == TransitionPreset.BASS_SWAP) TransitionPreset.BASS_SWAP else TransitionPreset.FADE,
                )
        }.sanitized()

    /** A named volume shape; marks the transition Custom unless it is still exactly its preset. */
    fun setVolumeShape(
        spec: TransitionSpec,
        shape: VolumeShape,
    ): TransitionSpec = relabel(shape.applyTo(spec))

    fun setEqMove(
        spec: TransitionSpec,
        move: EqMove,
    ): TransitionSpec = relabel(move.applyTo(spec))

    fun setFilter(
        spec: TransitionSpec,
        filter: TransitionFilter,
    ): TransitionSpec = relabel(spec.copy(filter = filter))

    /** The preset whose settings [spec] still matches exactly (duration aside), or Custom. */
    fun relabel(spec: TransitionSpec): TransitionSpec {
        val matching =
            TransitionPreset.entries.firstOrNull {
                it != TransitionPreset.CUSTOM &&
                    TransitionSpec.preset(it, spec).copy(preset = spec.preset) == spec
            }
        return spec.copy(preset = matching ?: TransitionPreset.CUSTOM)
    }

    fun setBassSwap(
        spec: TransitionSpec,
        enabled: Boolean,
    ): TransitionSpec {
        val preset =
            when {
                enabled && spec.curve != TransitionCurve.CUSTOM -> TransitionPreset.BASS_SWAP
                !enabled && spec.preset == TransitionPreset.BASS_SWAP -> TransitionPreset.FADE
                else -> spec.preset
            }
        return spec.copy(bassSwap = enabled, preset = preset)
    }

    fun setBassSwapAt(
        spec: TransitionSpec,
        at: Float,
    ): TransitionSpec =
        relabel(spec.copy(bassSwap = true, bassSwapAt = at.coerceIn(TransitionSpec.MIN_BASS_SWAP_AT, TransitionSpec.MAX_BASS_SWAP_AT)))

    /**
     * Moves a cue point by a horizontal drag of [deltaMs] (positive = the waveform moved right):
     * the outgoing track hands over earlier (more of its end is skipped), the incoming one starts
     * earlier. At least a second of each track always plays.
     */
    fun shiftCue(
        spec: TransitionSpec,
        role: TransitionRole,
        deltaMs: Float,
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
    ): TransitionSpec =
        when (role) {
            TransitionRole.OUTGOING ->
                spec.copy(outgoingCutMs = (spec.outgoingCutMs + deltaMs).toLong().coerceIn(0L, maxCue(outgoingDurationMs)).toInt())
            TransitionRole.INCOMING ->
                spec.copy(incomingStartMs = (spec.incomingStartMs - deltaMs).toLong().coerceIn(0L, maxCue(incomingDurationMs)).toInt())
        }

    private fun maxCue(trackMs: Long): Long = (trackMs - MIN_PLAYED_MS).coerceIn(0L, TransitionSpec.MAX_CUE_MS.toLong())

    /** The editable curve of [role]: the custom one, or the built-in curve sampled into points. */
    fun curveOf(
        spec: TransitionSpec,
        role: TransitionRole,
    ): VolumeCurve {
        val custom = if (role == TransitionRole.OUTGOING) spec.outgoingCurve else spec.incomingCurve
        return if (spec.curve == TransitionCurve.CUSTOM && custom != null) custom else VolumeCurve.sampled(spec.curve, role)
    }

    /**
     * Moves point [index] of [role]'s curve towards ([t], [gain]); the result is clamped to what is
     * allowed. A built-in curve becomes a custom one (the other track keeps its shape as points).
     */
    fun movePoint(
        spec: TransitionSpec,
        role: TransitionRole,
        index: Int,
        t: Float,
        gain: Float,
    ): TransitionSpec {
        val points = curveOf(spec, role).points
        if (index !in points.indices) return spec
        val last = points.lastIndex
        val newT =
            when (index) {
                0 -> 0f
                last -> 1f
                else -> t.coerceIn(points[index - 1].t + VolumeCurve.MIN_GAP, points[index + 1].t - VolumeCurve.MIN_GAP)
            }
        val newGain = fixedGain(role, index, last) ?: gain.coerceIn(0f, 1f)
        val updated = points.toMutableList().also { it[index] = CurvePoint(newT, newGain) }
        return withCurve(spec, role, VolumeCurve(updated))
    }

    /** Adds a point on [role]'s curve at [t] (on the curve's current level); no-op at the limit or too close to another point. */
    fun addPoint(
        spec: TransitionSpec,
        role: TransitionRole,
        t: Float,
    ): TransitionSpec {
        val curve = curveOf(spec, role)
        val points = curve.points
        val fits = points.size < VolumeCurve.MAX_POINTS && t > 0f && t < 1f
        if (!fits || points.any { kotlin.math.abs(it.t - t) < VolumeCurve.MIN_GAP }) return spec
        val updated = (points + CurvePoint(t, curve.gainAt(t))).sortedBy { it.t }
        return withCurve(spec, role, VolumeCurve(updated))
    }

    /** Removes an interior point; the end points always stay. */
    fun removePoint(
        spec: TransitionSpec,
        role: TransitionRole,
        index: Int,
    ): TransitionSpec {
        val points = curveOf(spec, role).points
        if (index <= 0 || index >= points.lastIndex) return spec
        return withCurve(spec, role, VolumeCurve(points.filterIndexed { i, _ -> i != index }))
    }

    /** Gain a point must keep, or null when it can move: the outgoing track starts full and ends silent, the incoming one ends full. */
    fun fixedGain(
        role: TransitionRole,
        index: Int,
        last: Int,
    ): Float? =
        when {
            role == TransitionRole.OUTGOING && index == 0 -> 1f
            role == TransitionRole.OUTGOING && index == last -> 0f
            role == TransitionRole.INCOMING && index == last -> 1f
            else -> null
        }

    private fun withCurve(
        spec: TransitionSpec,
        role: TransitionRole,
        curve: VolumeCurve,
    ): TransitionSpec {
        val other = if (role == TransitionRole.OUTGOING) TransitionRole.INCOMING else TransitionRole.OUTGOING
        val otherCurve = curveOf(spec, other)
        val out = if (role == TransitionRole.OUTGOING) curve else otherCurve
        val inc = if (role == TransitionRole.INCOMING) curve else otherCurve
        if (!out.isValid(TransitionRole.OUTGOING) || !inc.isValid(TransitionRole.INCOMING)) return spec
        return spec.copy(preset = TransitionPreset.CUSTOM, curve = TransitionCurve.CUSTOM, outgoingCurve = out, incomingCurve = inc)
    }
}
