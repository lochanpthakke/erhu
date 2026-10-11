package com.lochanpthakke.empee3.feature.library.transition

import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lochanpthakke.empee3.core.model.BandLevels
import com.lochanpthakke.empee3.core.model.BandWaveformLoad
import com.lochanpthakke.empee3.core.model.PassType
import com.lochanpthakke.empee3.core.model.TransitionCurve
import com.lochanpthakke.empee3.core.model.TransitionFilter
import com.lochanpthakke.empee3.core.model.TransitionRole
import com.lochanpthakke.empee3.core.model.TransitionSpec
import kotlin.math.ln

// Drawing of the transition timeline (see TransitionTimelineCanvas).

/** The darker window behind the transition, spanning both lanes (a hairline at the boundary when there is no transition). */
internal fun DrawScope.drawWindow(
    geometry: TimelineGeometry,
    color: Color,
    hasTransition: Boolean,
) {
    val left = geometry.xOfT(0f)
    if (!hasTransition) {
        drawLine(color, Offset(left, 0f), Offset(left, size.height), PLAYHEAD_WIDTH)
        return
    }
    drawRoundRect(
        color = color.copy(alpha = WINDOW_ALPHA),
        topLeft = Offset(left, 0f),
        size = Size(geometry.xOfT(1f) - left, size.height),
        cornerRadius = CornerRadius(WINDOW_CORNER, WINDOW_CORNER),
    )
}

/** The level the track plays at, at transition time [ms] (outside the window: before = start level, after = end level). */
private fun levelAt(
    role: TransitionRole,
    spec: TransitionSpec?,
    ms: Float,
    durationMs: Int,
): Float {
    val t = ms / durationMs.coerceAtLeast(1)
    val outgoing = role == TransitionRole.OUTGOING
    return when {
        // Without a transition the tracks simply meet at the boundary.
        spec == null -> if ((t < 0f) == outgoing) 1f else 0f
        t < 0f -> if (outgoing) 1f else 0f
        t > 1f -> if (outgoing) 0f else 1f
        else -> spec.gain(role, t)
    }
}

@Suppress("LongParameterList")
internal fun DrawScope.drawWave(
    role: TransitionRole,
    wave: LaneWave?,
    spec: TransitionSpec?,
    durationMs: Int,
    geometry: TimelineGeometry,
) {
    val levels = (wave?.load as? BandWaveformLoad.Ready)?.levels ?: return drawFlatLine(role, geometry)
    val mid = geometry.laneMid(role)
    val half = geometry.laneHeight / 2
    val barWidth = (geometry.width / levels.size.coerceAtLeast(1) * BAR_OVERLAP).coerceAtLeast(1f)
    for (i in 0 until levels.size) {
        val ms = wave.startMs + i * wave.binMs
        val x = geometry.xOfMs(ms)
        if (x < 0f || x > geometry.width) continue
        val gain = levelAt(role, spec, ms, durationMs)
        drawBands(levels, i, x, mid, half, barWidth, gain)
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawBands(
    levels: BandLevels,
    i: Int,
    x: Float,
    mid: Float,
    half: Float,
    width: Float,
    gain: Float,
) {
    // Absolute RMS is small for typical music (about 0.05–0.3); scaled for visibility, never invented.
    val bands =
        listOf(levels.low[i] to AutomationColors.low, levels.mid[i] to AutomationColors.mid, levels.high[i] to AutomationColors.high)
    bands.forEach { (level, color) ->
        val h = (level * WAVE_SCALE).coerceAtMost(1f) * half
        drawLine(color.copy(alpha = DIM_ALPHA), Offset(x, mid - h), Offset(x, mid + h), width)
        if (gain > 0f) drawLine(color, Offset(x, mid - h * gain), Offset(x, mid + h * gain), width)
    }
}

private fun DrawScope.drawFlatLine(
    role: TransitionRole,
    geometry: TimelineGeometry,
) {
    val y = geometry.laneMid(role)
    drawLine(AutomationColors.high.copy(alpha = DIM_ALPHA), Offset(0f, y), Offset(geometry.width, y), 1f)
}

internal fun DrawScope.drawVolume(
    role: TransitionRole,
    spec: TransitionSpec,
    geometry: TimelineGeometry,
    pointRadius: Float,
) {
    val path = Path()
    for (i in 0..CURVE_STEPS) {
        val t = i / CURVE_STEPS.toFloat()
        val point = Offset(geometry.xOfT(t), geometry.yOfGain(role, spec.gain(role, t)))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, AutomationColors.volume, style = Stroke(width = LINE_WIDTH))
    if (pointRadius <= 0f || spec.curve != TransitionCurve.CUSTOM) return
    TransitionEditing.curveOf(spec, role).points.forEach { p ->
        val center = Offset(geometry.xOfT(p.t), geometry.yOfGain(role, p.gain))
        drawCircle(Color.White, pointRadius + 2f, center)
        drawCircle(AutomationColors.volume, pointRadius, center)
    }
}

/** Bass: full bass on the lane's centre line, cut at the lane's bottom edge. */
internal fun DrawScope.drawBass(
    role: TransitionRole,
    spec: TransitionSpec,
    geometry: TimelineGeometry,
) {
    val path = Path()
    for (i in 0..CURVE_STEPS) {
        val t = i / CURVE_STEPS.toFloat()
        val depth = spec.bassDb(role, t) / TransitionSpec.BASS_CUT_DB
        val point = Offset(geometry.xOfT(t), geometry.laneMid(role) + depth * geometry.laneHeight / 2)
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, AutomationColors.eq, style = Stroke(width = LINE_WIDTH))
}

/** Filter: open on the centre line; a high-pass rises towards the top as it thins, a low-pass falls as it darkens. */
internal fun DrawScope.drawFilter(
    role: TransitionRole,
    filter: TransitionFilter,
    geometry: TimelineGeometry,
) {
    val path = Path()
    for (i in 0..CURVE_STEPS) {
        val t = i / CURVE_STEPS.toFloat()
        val setting = filter.settingAt(role, t) ?: return
        val depth =
            when (setting.type) {
                PassType.HIGH_PASS -> -logPosition(setting.hz, TransitionFilter.HIGH_PASS_OPEN_HZ, TransitionFilter.HIGH_PASS_THIN_HZ)
                PassType.LOW_PASS -> logPosition(setting.hz, TransitionFilter.LOW_PASS_OPEN_HZ, TransitionFilter.LOW_PASS_DARK_HZ)
            }
        val point = Offset(geometry.xOfT(t), geometry.laneMid(role) + depth * geometry.laneHeight / 2)
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, AutomationColors.filter, style = Stroke(width = LINE_WIDTH))
}

/** 0 at [open], 1 at [closed], even in pitch. */
private fun logPosition(
    hz: Float,
    open: Float,
    closed: Float,
): Float = (ln(hz / open) / ln(closed / open)).coerceIn(0f, 1f)

private const val WINDOW_ALPHA = 0.9f
private const val WINDOW_CORNER = 28f
private const val DIM_ALPHA = 0.28f
private const val BAR_OVERLAP = 1.6f
private const val WAVE_SCALE = 4f
private const val LINE_WIDTH = 5f
private const val CURVE_STEPS = 96
