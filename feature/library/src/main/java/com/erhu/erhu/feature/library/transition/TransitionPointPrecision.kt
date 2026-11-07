package com.erhu.erhu.feature.library.transition

import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import kotlin.math.log10
import kotlin.math.pow

internal data class TransitionCurveHandle(
    val role: TransitionRole,
    val index: Int,
)

internal data class TransitionPointValues(
    val timeMs: Double,
    val gainDb: Double,
    val silence: Boolean,
)

internal data class TransitionPointEdit(
    val handle: TransitionCurveHandle,
    val values: TransitionPointValues,
)

/** Conversion only: the existing curve editor enforces ordering, fixed endpoints and undo/save. */
internal object TransitionPointPrecision {
    const val MIN_GAIN_DB = -96.0

    @Suppress("ReturnCount", "MagicNumber") // Finite point conversion: dB = 20 log10(linear gain).
    fun values(
        spec: TransitionSpec,
        handle: TransitionCurveHandle,
        durationMs: Int,
    ): TransitionPointValues? {
        val point = TransitionEditing.curveOf(spec, handle.role).points.getOrNull(handle.index) ?: return null
        if (durationMs <= 0) return null
        return TransitionPointValues(
            point.t.toDouble() * durationMs,
            if (point.gain > 0f) (20 * log10(point.gain.toDouble())).coerceIn(MIN_GAIN_DB, 0.0) else MIN_GAIN_DB,
            point.gain == 0f,
        )
    }

    @Suppress("ReturnCount", "MagicNumber")
    fun updated(
        spec: TransitionSpec,
        edit: TransitionPointEdit,
        durationMs: Int,
    ): TransitionSpec {
        val values = edit.values
        if (durationMs <= 0 || !values.timeMs.isFinite() || !values.gainDb.isFinite()) return spec
        if (edit.handle.index !in TransitionEditing.curveOf(spec, edit.handle.role).points.indices) return spec
        val gain = if (values.silence) 0f else 10.0.pow(values.gainDb.coerceIn(MIN_GAIN_DB, 0.0) / 20).toFloat()
        return TransitionEditing.movePoint(
            spec,
            edit.handle.role,
            edit.handle.index,
            (values.timeMs / durationMs).coerceIn(0.0, 1.0).toFloat(),
            gain,
        )
    }
}
