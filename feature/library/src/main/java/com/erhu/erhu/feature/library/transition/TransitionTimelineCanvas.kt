package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.model.BandWaveformLoad
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Automation colours, also used by the section rows and the option sheet. */
@Suppress("MagicNumber") // colour literals
internal object AutomationColors {
    val volume = Color(0xFF4FB6EA)
    val eq = Color(0xFFF2C744)
    val filter = Color(0xFFE98BE6)
    val unavailable = Color(0xFF9E9E9E)

    // Waveform bands: lows, mids, highs (as on DJ software); fixed so they read the same in every theme.
    val low = Color(0xFF2F4BD0)
    val mid = Color(0xFFD38A3E)
    val high = Color(0xFFE9E0CF)
}

/**
 * Maps between the canvas and transition time: x covers [-contextMs, durationMs + contextMs];
 * the outgoing lane is on top, the incoming lane below, with [laneGap] between them.
 */
internal class TimelineGeometry(
    val width: Float,
    val height: Float,
    val durationMs: Int,
    val contextMs: Int,
    laneGap: Float,
) {
    val laneHeight = (height - laneGap) / 2f
    private val laneTops = floatArrayOf(0f, laneHeight + laneGap)
    private val spanMs = (durationMs + 2f * contextMs).coerceAtLeast(1f)

    fun laneTop(role: TransitionRole) = laneTops[role.ordinal]

    fun laneMid(role: TransitionRole) = laneTop(role) + laneHeight / 2

    fun xOfMs(ms: Float) = (ms + contextMs) / spanMs * width

    fun xOfT(t: Float) = xOfMs(t * durationMs)

    fun tOfX(x: Float) = ((x / width * spanMs) - contextMs) / durationMs.coerceAtLeast(1)

    /** Track time per pixel of horizontal movement. */
    val msPerPixel: Float get() = if (width > 0f) spanMs / width else 0f

    fun yOfGain(
        role: TransitionRole,
        gain: Float,
    ) = laneTop(role) + (1f - gain) * laneHeight

    fun gainOfY(
        role: TransitionRole,
        y: Float,
    ) = 1f - (y - laneTop(role)) / laneHeight

    fun roleAt(y: Float) = if (y < height / 2) TransitionRole.OUTGOING else TransitionRole.INCOMING
}

/** A track's band waveform window, placed on the transition time axis. */
internal data class LaneWave(
    val load: BandWaveformLoad,
    /** Transition time (ms) of the first bin. */
    val startMs: Float,
    val binMs: Float,
)

/** What a gesture on the timeline can do. */
internal class TimelineHandlers(
    val onDragStart: () -> Unit,
    val onMove: (TransitionRole, Int, Float, Float) -> Unit,
    val onAdd: (TransitionRole, Float) -> Unit,
    val onRemove: (TransitionRole, Int) -> Unit,
    /** A lane slid sideways by this many ms of track time (positive = to the right). */
    val onLaneDrag: (TransitionRole, Float) -> Unit,
    val onDragEnd: () -> Unit,
)

/** Context shown on each side of the transition window, so the window fills about two thirds of the width. */
internal fun contextFor(durationMs: Int): Int = maxOf(durationMs / CONTEXT_DIVISOR, MIN_CONTEXT_MS)

/**
 * The transition as it will play: the outgoing track (top) and the incoming one (bottom) on one
 * time axis, each as a three-band waveform (dim as recorded, bright as it will sound through its
 * volume curve), the transition window, and the automations — volume (blue), bass swap (yellow) and
 * filter sweep (pink). Sliding a lane sideways ([lanesDraggable], for a pair) moves that track's cue
 * point: where the outgoing one hands over, where the incoming one starts. With hand-edited volume
 * curves ([editablePoints]) the blue points can be dragged, a tap adds one, a long press removes one.
 */
@Composable
@Suppress("LongParameterList")
internal fun TransitionTimelineCanvas(
    spec: TransitionSpec?,
    durationMs: Int,
    outgoingWave: LaneWave?,
    incomingWave: LaneWave?,
    playheadMs: Long?,
    editablePoints: Boolean,
    lanesDraggable: Boolean,
    handlers: TimelineHandlers,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val laneGap = with(density) { LANE_GAP.toPx() }
    val pointRadius = with(density) { POINT_RADIUS.toPx() }
    val hitRadius = with(density) { HIT_RADIUS.toPx() }
    val contextMs = contextFor(durationMs)
    val currentSpec by rememberUpdatedState(spec)
    val currentEditable by rememberUpdatedState(editablePoints)
    val currentDraggable by rememberUpdatedState(lanesDraggable)
    val description =
        remember(spec, durationMs) {
            if (spec == null) {
                "No transition: the tracks play back to back."
            } else {
                "Transition of ${TransitionSpec.durationLabel(durationMs)}: outgoing track on top, incoming below. " +
                    "Use the presets and the Volume, EQ and Filter options below to change it."
            }
        }
    Canvas(
        modifier =
            modifier
                .semantics { contentDescription = description }
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val geometry = TimelineGeometry(size.width.toFloat(), size.height.toFloat(), durationMs, contextMs, laneGap)
                        // Always wait for a touch first: returning without one would make the gesture loop spin.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val shown = currentSpec ?: return@awaitEachGesture
                        val options = GestureOptions(currentEditable, currentDraggable, hitRadius)
                        timelineGesture(down, geometry, shown, options, handlers)
                    }
                },
    ) {
        val geometry = TimelineGeometry(size.width, size.height, durationMs, contextMs, laneGap)
        drawWindow(geometry, colors.surfaceContainerHighest, spec != null)
        drawWave(TransitionRole.OUTGOING, outgoingWave, spec, durationMs, geometry)
        drawWave(TransitionRole.INCOMING, incomingWave, spec, durationMs, geometry)
        if (spec != null) {
            TransitionRole.entries.forEach { role ->
                if (spec.filter.affects(role)) drawFilter(role, spec.filter, geometry)
                if (spec.bassSwap) drawBass(role, spec, geometry)
                drawVolume(role, spec, geometry, if (editablePoints) pointRadius else 0f)
            }
        }
        playheadMs?.let {
            val x = geometry.xOfMs(it.toFloat())
            if (x in 0f..size.width) drawLine(colors.onSurface, Offset(x, 0f), Offset(x, size.height), PLAYHEAD_WIDTH)
        }
    }
}

/** What the current gesture may do. */
private class GestureOptions(
    val editPoints: Boolean,
    val dragLanes: Boolean,
    val hitRadius: Float,
)

/**
 * One gesture after [down]: on a volume point (when curves are hand-edited) a drag moves it and a
 * long press removes it; a sideways swipe on a lane slides that track (its cue point); a tap on a
 * lane inside the window adds a point. A vertical swipe is left to the page, which scrolls.
 */
private suspend fun AwaitPointerEventScope.timelineGesture(
    down: PointerInputChange,
    geometry: TimelineGeometry,
    spec: TransitionSpec,
    options: GestureOptions,
    handlers: TimelineHandlers,
) {
    val hit = if (options.editPoints) hitPoint(spec, geometry, down.position, options.hitRadius) else null
    if (hit != null) return dragPoint(down, hit, geometry, handlers)
    val role = geometry.roleAt(down.position.y)
    val msPerPx = geometry.msPerPixel
    if (options.dragLanes) {
        var first = 0f
        val slop =
            awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                first = over
            }
        if (slop != null) {
            handlers.onDragStart()
            handlers.onLaneDrag(role, first * msPerPx)
            horizontalDrag(slop.id) { change ->
                handlers.onLaneDrag(role, change.positionChange().x * msPerPx)
                change.consume()
            }
            handlers.onDragEnd()
            return
        }
    } else {
        waitForUpOrCancellation()
    }
    // No drag: a tap (released here, not taken by the page) adds a point on hand-edited curves.
    val up = currentEvent.changes.firstOrNull { it.id == down.id }
    val t = geometry.tOfX(down.position.x)
    val released = up != null && !up.pressed && !up.isConsumed
    val tapped = released && (up!!.position - down.position).getDistance() < viewConfiguration.touchSlop
    val insideWindow = t > 0f && t < 1f
    if (options.editPoints && tapped && insideWindow) handlers.onAdd(role, t)
}

/** A gesture that started on a volume point: drag to move it, hold still to remove it. */
private suspend fun AwaitPointerEventScope.dragPoint(
    down: PointerInputChange,
    hit: Pair<TransitionRole, Int>,
    geometry: TimelineGeometry,
    handlers: TimelineHandlers,
) {
    down.consume()
    var released = false
    val slop =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }.also { released = it == null }
        }
    if (slop == null) {
        if (!released) handlers.onRemove(hit.first, hit.second) // held still: long press
        return
    }
    handlers.onDragStart()
    val (role, index) = hit
    handlers.onMove(role, index, geometry.tOfX(slop.position.x), geometry.gainOfY(role, slop.position.y))
    drag(slop.id) { change ->
        change.consume()
        handlers.onMove(role, index, geometry.tOfX(change.position.x), geometry.gainOfY(role, change.position.y))
    }
    handlers.onDragEnd()
}

/** The volume point under [position], as (role, index). */
private fun hitPoint(
    spec: TransitionSpec,
    geometry: TimelineGeometry,
    position: Offset,
    radius: Float,
): Pair<TransitionRole, Int>? =
    TransitionRole.entries
        .flatMap { role ->
            TransitionEditing.curveOf(spec, role).points.mapIndexed { index, p ->
                Triple(role, index, hypot(geometry.xOfT(p.t) - position.x, geometry.yOfGain(role, p.gain) - position.y))
            }
        }.filter { it.third <= radius }
        .minByOrNull { it.third }
        ?.let { it.first to it.second }

/** Rounded percentage for labels. */
internal fun percent(fraction: Float): Int = (fraction * PERCENT).roundToInt()

private val LANE_GAP = 56.dp
private val POINT_RADIUS = 7.dp
private val HIT_RADIUS = 24.dp
private const val CONTEXT_DIVISOR = 4
private const val MIN_CONTEXT_MS = 750
internal const val PLAYHEAD_WIDTH = 3f
private const val PERCENT = 100
