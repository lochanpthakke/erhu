// One reader and one writer per level, kept together so the two directions stay in step.
@file:Suppress("TooManyFunctions")

package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.datastore.proto.CollectionTransitionProto
import com.erhu.erhu.core.datastore.proto.GlobalTransitionProto
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.datastore.proto.TransitionSpecProto
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.CurvePoint
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeCurve
import kotlinx.coroutines.flow.map

// Mapping between transition settings and the settings proto (see TransitionSettingsPersistence).

internal const val TRANSITION_SCHEMA_VERSION = 2
private const val MODE_INHERIT = "INHERIT"
private const val MODE_OFF = "OFF"
private const val MODE_CUSTOM = "CUSTOM"

/** Everything saved. Unknown keys are skipped; unknown or broken values fall back to safe defaults. */
fun PlaybackStateProto.toTransitionConfig(): TransitionConfig =
    TransitionConfig(
        global = if (hasGlobalTransitions()) globalTransitions.toDomain() else GlobalTransitionSettings(),
        collections =
            collectionTransitionsMap.entries
                .mapNotNull { (key, proto) -> CollectionRef.fromKey(key)?.let { it to proto.toChoice() } }
                .filter { it.second != TransitionChoice.Inherit }
                .toMap(),
        pairs =
            pairTransitionsMap.entries
                .mapNotNull { (key, proto) -> TransitionPairKey.fromKey(key)?.let { it to proto.toChoice() } }
                .filter { it.second != TransitionChoice.Inherit }
                .toMap(),
    )

private fun GlobalTransitionProto.toDomain(): GlobalTransitionSettings =
    GlobalTransitionSettings(
        masterEnabled = if (hasMasterEnabled()) masterEnabled else true,
        default = if (defaultMode == MODE_CUSTOM) TransitionChoice.Custom(defaultSpec.toDomain()) else TransitionChoice.Off,
    )

private fun CollectionTransitionProto.toChoice(): TransitionChoice =
    when {
        mode == MODE_OFF -> TransitionChoice.Off
        mode == MODE_CUSTOM -> TransitionChoice.Custom(spec.toDomain())
        mode == MODE_INHERIT -> TransitionChoice.Inherit
        // Version 1 (before modes): "enabled" meant a crossfade with these values; off meant "nothing set".
        enabled ->
            TransitionChoice.Custom(
                TransitionSpec(durationMs = durationMs, curve = enumOr(curve, TransitionCurve.EQUAL_POWER)).sanitized(),
            )
        else -> TransitionChoice.Inherit
    }

internal fun TransitionSpecProto.toDomain(): TransitionSpec {
    val defaults = TransitionSpec()
    return TransitionSpec(
        durationMs = durationMs.takeIf { it > 0 } ?: defaults.durationMs,
        preset = enumOr(preset, defaults.preset),
        curve = enumOr(curve, defaults.curve),
        outgoingCurve = curveOf(outTList, outGainList),
        incomingCurve = curveOf(inTList, inGainList),
        bassSwap = bassSwap,
        bassSwapAt = bassSwapAt.takeIf { it > 0f } ?: defaults.bassSwapAt,
        filter = enumOr(filter, TransitionFilter.NONE),
        outgoingCutMs = outgoingCutMs,
        incomingStartMs = incomingStartMs,
    ).sanitized()
}

private fun curveOf(
    times: List<Float>,
    gains: List<Float>,
): VolumeCurve? = if (times.isEmpty() || times.size != gains.size) null else VolumeCurve(times.zip(gains) { t, g -> CurvePoint(t, g) })

internal fun TransitionSpec.toProto(): TransitionSpecProto {
    val clean = sanitized()
    val builder =
        TransitionSpecProto
            .newBuilder()
            .setDurationMs(clean.durationMs)
            .setPreset(clean.preset.name)
            .setCurve(clean.curve.name)
            .setBassSwap(clean.bassSwap)
            .setBassSwapAt(clean.bassSwapAt)
            .setFilter(clean.filter.name)
            .setOutgoingCutMs(clean.outgoingCutMs)
            .setIncomingStartMs(clean.incomingStartMs)
    clean.outgoingCurve?.points?.let { points ->
        builder.addAllOutT(points.map { it.t }).addAllOutGain(points.map { it.gain })
    }
    clean.incomingCurve?.points?.let { points ->
        builder.addAllInT(points.map { it.t }).addAllInGain(points.map { it.gain })
    }
    return builder.build()
}

private fun TransitionChoice.toProto(): CollectionTransitionProto {
    val builder = CollectionTransitionProto.newBuilder().setSchemaVersion(TRANSITION_SCHEMA_VERSION)
    return when (this) {
        TransitionChoice.Inherit -> builder.setMode(MODE_INHERIT)
        TransitionChoice.Off -> builder.setMode(MODE_OFF)
        is TransitionChoice.Custom -> builder.setMode(MODE_CUSTOM).setSpec(spec.toProto())
    }.build()
}

fun GlobalTransitionSettings.applyTo(state: PlaybackStateProto): PlaybackStateProto {
    val builder = GlobalTransitionProto.newBuilder().setMasterEnabled(masterEnabled)
    val spec = (default as? TransitionChoice.Custom)?.spec
    if (spec != null) builder.setDefaultMode(MODE_CUSTOM).setDefaultSpec(spec.toProto()) else builder.setDefaultMode(MODE_OFF)
    return state.toBuilder().setGlobalTransitions(builder.build()).build()
}

fun TransitionChoice.applyToCollection(
    collection: CollectionRef,
    state: PlaybackStateProto,
): PlaybackStateProto =
    state
        .toBuilder()
        .apply {
            if (this@applyToCollection ==
                TransitionChoice.Inherit
            ) {
                removeCollectionTransitions(collection.key)
            } else {
                putCollectionTransitions(collection.key, toProto())
            }
        }.build()

fun TransitionChoice.applyToPair(
    pair: TransitionPairKey,
    state: PlaybackStateProto,
): PlaybackStateProto =
    state
        .toBuilder()
        .apply {
            if (this@applyToPair ==
                TransitionChoice.Inherit
            ) {
                removePairTransitions(pair.key)
            } else {
                putPairTransitions(pair.key, toProto())
            }
        }.build()

private inline fun <reified E : Enum<E>> enumOr(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default
