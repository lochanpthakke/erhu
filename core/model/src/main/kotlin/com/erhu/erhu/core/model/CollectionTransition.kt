package com.erhu.erhu.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The kinds of collection that can carry their own transition settings. */
enum class CollectionKind(
    internal val prefix: String,
) {
    ALBUM("album"),
    PLAYLIST("playlist"),
}

/**
 * A stable reference to an album or playlist: its database id, never a title, artwork or list
 * position. Album ids are derived from the album's grouping identity, so they survive rescans;
 * playlist ids are the playlist's own primary key.
 */
data class CollectionRef(
    val kind: CollectionKind,
    val id: Long,
) {
    /** Storage form, e.g. "album:123" (used in settings and on queue entries). */
    val key: String get() = "${kind.prefix}:$id"

    companion object {
        /** Parses [key]; null for anything that is not exactly a known kind and a numeric id. */
        fun fromKey(key: String?): CollectionRef? {
            val parts = key?.split(':')?.takeIf { it.size == 2 } ?: return null
            val kind = CollectionKind.entries.firstOrNull { it.prefix == parts[0] }
            val id = parts[1].toLongOrNull()
            return if (kind != null && id != null) CollectionRef(kind, id) else null
        }

        fun album(id: Long) = CollectionRef(CollectionKind.ALBUM, id)

        fun playlist(id: Long) = CollectionRef(CollectionKind.PLAYLIST, id)
    }
}

/**
 * One occurrence of a song in a collection. Album tracks are unique, so the song id is enough
 * ([slot] null). A playlist can hold a song more than once: [slot] is the entry's playlist
 * position, which never changes once the entry exists (removals leave gaps, additions append).
 * The song id is kept too, so a reused position never takes over another song's settings.
 */
data class TrackOccurrence(
    val songId: Long,
    val slot: Int? = null,
) {
    val key: String get() = if (slot == null) "s$songId" else "s$songId@$slot"

    companion object {
        @Suppress("ReturnCount") // guard clauses: each malformed part rejects the key
        fun fromKey(key: String?): TrackOccurrence? {
            val body = key?.takeIf { it.startsWith("s") }?.drop(1) ?: return null
            val songId = body.substringBefore('@').toLongOrNull() ?: return null
            if ('@' !in body) return TrackOccurrence(songId)
            return body.substringAfter('@').toIntOrNull()?.let { TrackOccurrence(songId, it) }
        }
    }
}

/** Where a queue entry came from: the collection it was started from and which occurrence it is. */
data class QueueOrigin(
    val collection: CollectionRef,
    val occurrence: TrackOccurrence,
) {
    /** Storage form on queue entries: "playlist:5#s12@3". */
    val key: String get() = "${collection.key}#${occurrence.key}"

    companion object {
        /** Also accepts the older form without an occurrence ("album:7"), with [songId] filled in. */
        @Suppress("ReturnCount") // guard clauses: each malformed part rejects the key
        fun fromKey(
            key: String?,
            songId: Long,
        ): QueueOrigin? {
            if (key.isNullOrEmpty()) return null
            val collection = CollectionRef.fromKey(key.substringBefore('#')) ?: return null
            val occurrence = if ('#' in key) TrackOccurrence.fromKey(key.substringAfter('#')) else TrackOccurrence(songId)
            return occurrence?.let { QueueOrigin(collection, it) }
        }
    }
}

/** One specific transition: from one occurrence to the next, inside one collection. Order matters. */
data class TransitionPairKey(
    val collection: CollectionRef,
    val from: TrackOccurrence,
    val to: TrackOccurrence,
) {
    val key: String get() = "${collection.key}|${from.key}>${to.key}"

    companion object {
        @Suppress("ReturnCount") // guard clauses: each malformed part rejects the key
        fun fromKey(key: String?): TransitionPairKey? {
            val collection = CollectionRef.fromKey(key?.substringBefore('|')) ?: return null
            val pair = key?.substringAfter('|', "")?.split('>')?.takeIf { it.size == 2 } ?: return null
            val from = TrackOccurrence.fromKey(pair[0]) ?: return null
            val to = TrackOccurrence.fromKey(pair[1]) ?: return null
            return TransitionPairKey(collection, from, to)
        }
    }
}

/** Volume curve shape. Equal power keeps the summed power of two unrelated signals steady; it does not make any pair of songs equally loud. */
enum class TransitionCurve(
    val label: String,
) {
    LINEAR("Linear"),
    EQUAL_POWER("Equal power"),
    CUSTOM("Custom"),
}

/** Named starting points. Each one is exactly what the audio engine plays; none is decorative. */
enum class TransitionPreset(
    val label: String,
) {
    /** Both tracks cross over along the chosen curve. */
    FADE("Fade"),

    /** The outgoing track holds full level, then leaves late; the incoming one rises early. */
    RISE("Rise"),

    /** A crossfade where the bass is exchanged mid-way (low-shelf cut on one track at a time). */
    BASS_SWAP("Bass Swap"),

    /** Both tracks at full level through the window, bass swapped in the centre, high-pass in and out. */
    BLEND("Blend"),

    /** A crossfade while the outgoing track is darkened by a low-pass sweep. */
    MELT("Melt"),

    /** A hard cut in the centre of the window. */
    SLAM("Slam"),

    /** Hand-edited volume curves. */
    CUSTOM("Custom"),
}

/** A point of a volume curve: [t] is the progress through the transition (0..1), [gain] the level (0..1). */
data class CurvePoint(
    val t: Float,
    val gain: Float,
)

/** Which track a curve shapes. */
enum class TransitionRole { OUTGOING, INCOMING }

/**
 * A piecewise-linear volume curve over the transition. Valid curves start at t = 0, end at t = 1,
 * have strictly increasing times, gains within 0..1, and the fixed end levels of their role (the
 * outgoing track starts at full level and ends silent; the incoming one ends at full level), so a
 * transition never jumps in level where it hands over or finishes.
 */
data class VolumeCurve(
    val points: List<CurvePoint>,
) {
    fun gainAt(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val after = points.indexOfFirst { it.t >= x }
        if (after <= 0) return points.first().gain
        val a = points[after - 1]
        val b = points[after]
        return a.gain + (b.gain - a.gain) * (x - a.t) / (b.t - a.t)
    }

    @Suppress("ReturnCount") // one guard per rule
    fun isValid(role: TransitionRole): Boolean {
        if (points.size !in MIN_POINTS..MAX_POINTS) return false
        if (points.first().t != 0f || points.last().t != 1f) return false
        if (points.any { !it.t.isFinite() || !it.gain.isFinite() || it.gain !in 0f..1f }) return false
        // Small tolerance: a gap of exactly MIN_GAP can come out a hair smaller in floating point.
        if (points.zipWithNext().any { (a, b) -> b.t - a.t < MIN_GAP - GAP_TOLERANCE }) return false
        return when (role) {
            TransitionRole.OUTGOING -> points.first().gain == 1f && points.last().gain == 0f
            TransitionRole.INCOMING -> points.last().gain == 1f
        }
    }

    companion object {
        const val MIN_POINTS = 2
        const val MAX_POINTS = 8

        /** Smallest time step between neighbouring points (1 % of the transition). */
        const val MIN_GAP = 0.01f
        private const val GAP_TOLERANCE = 1e-4f

        fun of(vararg points: Pair<Float, Float>) = VolumeCurve(points.map { (t, g) -> CurvePoint(t, g) })

        /** Points that follow [curve] for [role], for drawing and as the start of a custom edit. */
        fun sampled(
            curve: TransitionCurve,
            role: TransitionRole,
            count: Int = 5,
        ): VolumeCurve =
            VolumeCurve(
                List(count) { i ->
                    val t = i / (count - 1f)
                    CurvePoint(t, CurveMath.gain(curve, role, t))
                },
            )
    }
}

/** Gain formulas of the built-in curves. */
object CurveMath {
    @Suppress("ReturnCount") // exact end levels first
    fun gain(
        curve: TransitionCurve,
        role: TransitionRole,
        t: Float,
    ): Float {
        val x = t.coerceIn(0f, 1f)
        // Exact end levels (cos(pi/2) is not exactly 0 in floating point).
        if (x == 0f) return if (role == TransitionRole.INCOMING) 0f else 1f
        if (x == 1f) return if (role == TransitionRole.INCOMING) 1f else 0f
        val rising =
            when (curve) {
                TransitionCurve.EQUAL_POWER -> sin(x * PI / 2).toFloat()
                else -> x
            }
        return when (role) {
            TransitionRole.INCOMING -> rising
            TransitionRole.OUTGOING -> if (curve == TransitionCurve.EQUAL_POWER) cos(x * PI / 2).toFloat() else 1f - x
        }
    }
}

/**
 * One transition's full description, in the form the playback engine executes: duration, the
 * volume curve of each track, and whether the bass is swapped (at [bassSwapAt] of the way through).
 */
data class TransitionSpec(
    val durationMs: Int = DEFAULT_DURATION_MS,
    val preset: TransitionPreset = TransitionPreset.FADE,
    val curve: TransitionCurve = TransitionCurve.EQUAL_POWER,
    /** Used when [curve] is CUSTOM. */
    val outgoingCurve: VolumeCurve? = null,
    val incomingCurve: VolumeCurve? = null,
    val bassSwap: Boolean = false,
    val bassSwapAt: Float = DEFAULT_BASS_SWAP_AT,
    /** Filter sweep on one or both tracks. */
    val filter: TransitionFilter = TransitionFilter.NONE,
    /**
     * Cue points (set per pair, by sliding the waveforms): how much of the outgoing track's end is
     * skipped (the transition ends there instead of at the track's end), and where the incoming
     * track starts. 0 and 0 = the transition ends at the outgoing track's end and the next starts at 0.
     */
    val outgoingCutMs: Int = 0,
    val incomingStartMs: Int = 0,
) {
    /**
     * The duration that fits these two tracks with these cue points: never more than half of what
     * plays of either track, so a transition never takes over a whole short track.
     */
    fun effectiveDurationMs(
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
    ): Int {
        val outgoingPlayed = (outgoingDurationMs - outgoingCutMs).coerceAtLeast(0L)
        val incomingPlayed = (incomingDurationMs - incomingStartMs).coerceAtLeast(0L)
        return minOf(durationMs.toLong(), outgoingPlayed / 2, incomingPlayed / 2).toInt()
    }

    /** Where in the outgoing track the transition ends (its end, minus the cut). */
    fun outgoingEndMs(outgoingDurationMs: Long): Long = (outgoingDurationMs - outgoingCutMs).coerceAtLeast(0L)

    fun gain(
        role: TransitionRole,
        t: Float,
    ): Float {
        val custom = if (role == TransitionRole.OUTGOING) outgoingCurve else incomingCurve
        return if (curve == TransitionCurve.CUSTOM && custom != null) custom.gainAt(t) else CurveMath.gain(curve, role, t)
    }

    /**
     * Low-shelf gain in dB for [role] at [t], or 0 without a bass swap. The outgoing track keeps its
     * bass until the swap point and loses it over a short ramp; the incoming track gains it there.
     */
    fun bassDb(
        role: TransitionRole,
        t: Float,
    ): Float {
        if (!bassSwap) return 0f
        val ramp = ((t - (bassSwapAt - BASS_RAMP / 2)) / BASS_RAMP).coerceIn(0f, 1f)
        return when (role) {
            TransitionRole.OUTGOING -> BASS_CUT_DB * ramp
            TransitionRole.INCOMING -> BASS_CUT_DB * (1f - ramp)
        }
    }

    /** A valid version of this spec: duration clamped and rounded, broken custom curves replaced by the preset's curves. */
    fun sanitized(): TransitionSpec {
        val swap = bassSwapAt.takeIf { it.isFinite() }?.coerceIn(MIN_BASS_SWAP_AT, MAX_BASS_SWAP_AT) ?: DEFAULT_BASS_SWAP_AT
        val clean =
            copy(
                durationMs = clampDuration(durationMs),
                bassSwapAt = swap,
                outgoingCutMs = outgoingCutMs.coerceIn(0, MAX_CUE_MS),
                incomingStartMs = incomingStartMs.coerceIn(0, MAX_CUE_MS),
            )
        val customValid =
            curve == TransitionCurve.CUSTOM &&
                outgoingCurve?.isValid(TransitionRole.OUTGOING) == true &&
                incomingCurve?.isValid(TransitionRole.INCOMING) == true
        return when {
            customValid -> clean
            // Built-in curves need no points; broken custom curves fall back to equal power.
            curve == TransitionCurve.CUSTOM -> clean.copy(curve = TransitionCurve.EQUAL_POWER, outgoingCurve = null, incomingCurve = null)
            else -> clean.copy(outgoingCurve = null, incomingCurve = null)
        }
    }

    companion object {
        /** Duration presets: 0.5, 1, 2, 4, 6 and 8 seconds. Custom durations are allowed within [MIN_DURATION_MS]..[MAX_DURATION_MS]. */
        val DURATION_PRESETS_MS: List<Int> = listOf(500, 1_000, 2_000, 4_000, 6_000, 8_000)
        const val DEFAULT_DURATION_MS = 2_000
        const val MIN_DURATION_MS = 500
        const val MAX_DURATION_MS = 12_000

        /** Cue points are kept within an hour; the planner fits them to the actual tracks. */
        const val MAX_CUE_MS = 3_600_000
        const val DURATION_STEP_MS = 100

        const val BASS_CUT_DB = -24f
        const val BASS_SHELF_HZ = 200f

        /** The bass changes hands over this share of the transition, centred on the swap point. */
        const val BASS_RAMP = 0.1f
        const val DEFAULT_BASS_SWAP_AT = 0.5f
        const val MIN_BASS_SWAP_AT = 0.1f
        const val MAX_BASS_SWAP_AT = 0.9f

        fun clampDuration(ms: Int): Int =
            (Math.round(ms.toFloat() / DURATION_STEP_MS) * DURATION_STEP_MS).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

        /** "0.5 s", "2 s", "2.4 s". */
        fun durationLabel(ms: Int): String = if (ms % MS_PER_S == 0) "${ms / MS_PER_S} s" else "${ms.toDouble() / MS_PER_S} s"

        /** The spec a preset starts from, keeping [base]'s duration. Every preset uses only what the engine plays. */
        @Suppress("MagicNumber") // the Rise preset's curve shape
        fun preset(
            preset: TransitionPreset,
            base: TransitionSpec = TransitionSpec(),
        ): TransitionSpec {
            // Cue points belong to the pair, not to the preset: they are kept.
            val plain =
                TransitionSpec(
                    durationMs = base.durationMs,
                    preset = preset,
                    outgoingCutMs = base.outgoingCutMs,
                    incomingStartMs = base.incomingStartMs,
                )
            return when (preset) {
                TransitionPreset.FADE ->
                    plain.copy(
                        curve =
                            base.curve.takeIf { it != TransitionCurve.CUSTOM } ?: TransitionCurve.EQUAL_POWER,
                    )
                TransitionPreset.RISE ->
                    plain.copy(
                        curve = TransitionCurve.CUSTOM,
                        outgoingCurve = VolumeCurve.of(0f to 1f, 0.6f to 0.9f, 1f to 0f),
                        incomingCurve = VolumeCurve.of(0f to 0f, 0.4f to 0.8f, 1f to 1f),
                        filter = TransitionFilter.HIGH_PASS_IN,
                    )
                TransitionPreset.BASS_SWAP -> EqMove.CENTER_BASS_SWAP.applyTo(plain)
                TransitionPreset.BLEND ->
                    VolumeShape.OVERLAP.applyTo(EqMove.CENTER_BASS_SWAP.applyTo(plain)).copy(filter = TransitionFilter.HIGH_PASS_IN_OUT)
                TransitionPreset.MELT -> plain.copy(filter = TransitionFilter.LOW_PASS_OUT)
                TransitionPreset.SLAM -> VolumeShape.CENTER_CUT.applyTo(plain)
                TransitionPreset.CUSTOM ->
                    base.copy(
                        preset = preset,
                        curve = TransitionCurve.CUSTOM,
                        outgoingCurve = base.outgoingCurve ?: VolumeCurve.sampled(base.curve.builtIn(), TransitionRole.OUTGOING),
                        incomingCurve = base.incomingCurve ?: VolumeCurve.sampled(base.curve.builtIn(), TransitionRole.INCOMING),
                    )
            }
        }

        private fun TransitionCurve.builtIn() = if (this == TransitionCurve.CUSTOM) TransitionCurve.EQUAL_POWER else this

        private const val MS_PER_S = 1_000
    }
}

/** A level's setting: follow the level above, no transition, or this transition. */
sealed interface TransitionChoice {
    data object Inherit : TransitionChoice

    data object Off : TransitionChoice

    data class Custom(
        val spec: TransitionSpec,
    ) : TransitionChoice
}

/**
 * The app-wide part. [masterEnabled] off disables every automatic transition. [default] applies
 * to ordinary queues and to collections and pairs that inherit; it is Off or Custom, never Inherit.
 * The defaults reproduce the earlier behaviour: nothing crossfades until a collection is set up.
 */
data class GlobalTransitionSettings(
    val masterEnabled: Boolean = true,
    val default: TransitionChoice = TransitionChoice.Off,
)

/** Everything the resolver needs. Collections and pairs missing from the maps inherit. */
data class TransitionConfig(
    val global: GlobalTransitionSettings = GlobalTransitionSettings(),
    val collections: Map<CollectionRef, TransitionChoice> = emptyMap(),
    val pairs: Map<TransitionPairKey, TransitionChoice> = emptyMap(),
    /** Pauses a collection without deleting its default or exact-pair edits. */
    val disabledCollections: Set<CollectionRef> = emptySet(),
) {
    fun collection(ref: CollectionRef): TransitionChoice = collections[ref] ?: TransitionChoice.Inherit

    fun pair(key: TransitionPairKey): TransitionChoice = pairs[key] ?: TransitionChoice.Inherit

    /** Independent of the global safety switch; reflects this collection's saved configuration. */
    fun collectionEnabled(ref: CollectionRef): Boolean =
        ref !in disabledCollections &&
            (
                collection(ref) is TransitionChoice.Custom ||
                    (collection(ref) == TransitionChoice.Inherit && global.default is TransitionChoice.Custom) ||
                    pairs.any { (key, choice) -> key.collection == ref && choice is TransitionChoice.Custom }
            )

    fun withCollectionEnabled(
        ref: CollectionRef,
        enabled: Boolean,
    ): TransitionConfig {
        if (!enabled) return copy(disabledCollections = disabledCollections + ref)
        val resumed = copy(disabledCollections = disabledCollections - ref)
        // A previously unconfigured collection starts with the existing Fade preset.
        return if (resumed.collectionEnabled(ref)) {
            resumed
        } else {
            resumed.copy(collections = collections + (ref to TransitionChoice.Custom(TransitionSpec())))
        }
    }
}

/** Which level decided a boundary. */
enum class TransitionSource { MASTER_OFF, PAIR, COLLECTION, GLOBAL_DEFAULT }

data class ResolvedTransition(
    /** The transition to play, or null for the normal (gapless) boundary. */
    val spec: TransitionSpec?,
    val source: TransitionSource,
)

/**
 * Precedence: the master switch, then the exact pair, then the collection, then the global
 * default. A level set to Off stops there (it never falls through to a more general "on").
 * Only a boundary between two entries started from the same collection uses that collection's
 * pair and collection settings; every other boundary (ordinary queue, two collections, entries
 * added one by one) uses the global default.
 */
object TransitionResolver {
    @Suppress("ReturnCount") // one return per precedence level
    fun resolve(
        config: TransitionConfig,
        outgoing: QueueOrigin?,
        incoming: QueueOrigin?,
    ): ResolvedTransition {
        if (!config.global.masterEnabled) return ResolvedTransition(null, TransitionSource.MASTER_OFF)
        val collection = outgoing?.collection?.takeIf { it == incoming?.collection }
        if (collection != null && incoming != null) {
            if (collection in config.disabledCollections) return ResolvedTransition(null, TransitionSource.COLLECTION)
            val pair = config.pair(TransitionPairKey(collection, outgoing.occurrence, incoming.occurrence))
            if (pair != TransitionChoice.Inherit) return ResolvedTransition(pair.specOrNull(), TransitionSource.PAIR)
            val own = config.collection(collection)
            if (own != TransitionChoice.Inherit) return ResolvedTransition(own.specOrNull(), TransitionSource.COLLECTION)
        }
        return ResolvedTransition(config.global.default.specOrNull(), TransitionSource.GLOBAL_DEFAULT)
    }

    /** What a collection's pairs inherit: its own choice, or the global default. */
    fun collectionDefault(
        config: TransitionConfig,
        collection: CollectionRef,
    ): TransitionSpec? {
        if (!config.global.masterEnabled || collection in config.disabledCollections) return null
        val own = config.collection(collection)
        return if (own == TransitionChoice.Inherit) config.global.default.specOrNull() else own.specOrNull()
    }

    private fun TransitionChoice.specOrNull(): TransitionSpec? = (this as? TransitionChoice.Custom)?.spec
}
