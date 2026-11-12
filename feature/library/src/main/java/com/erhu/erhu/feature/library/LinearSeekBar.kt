package com.erhu.erhu.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.model.AbLoopState
import com.erhu.erhu.core.model.WaveformTimeline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The touch area: the whole row, not just the line (well above the 48 dp target). */
private val TOUCH_HEIGHT = 48.dp

/** The visible line, a little thicker under the finger. */
private val TRACK_THICKNESS = 4.dp
private val TRACK_THICKNESS_SCRUBBING = 6.dp

/**
 * The head at the leading edge: a small round knob (larger under the finger) with a bright centre,
 * a slightly brighter contour and a compact glow around it.
 */
private val HEAD_RADIUS = 5.dp
private val KNOB_RADIUS = 7.dp
private val GLOW_RADIUS = 12.dp
private val CONTOUR_WIDTH = 1.5.dp

/** A faint dark rim under the head, so it still separates from bright backgrounds. */
private val SHADE_EXTRA = 1.5.dp
private const val SHADE_ALPHA = 0.22f
private const val CENTRE_LIFT = 0.7f
private const val CONTOUR_LIFT = 0.5f
private const val CONTOUR_ALPHA = 0.7f
private const val GLOW_INNER = 0.42f

/** Paused, the glow stays at this strength: still there, never pulsing. */
private const val PAUSED_GLOW = 0.5f

/** The played part's tail starts this much quieter than its head: a gentle colour run, not a stripe. */
private const val TAIL_ALPHA = 0.55f
private const val HEAD_LIFT = 0.28f
private const val GLOW_ALPHA = 0.5f
private const val LOOP_ALPHA = 0.28f
private const val LOOP_DISABLED_ALPHA = 0.12f
private val MARKER_WIDTH = 2.dp
private val MARKER_HEIGHT = 12.dp
private val BOOKMARK_RADIUS = 2.dp
private val BOOKMARK_GAP = 7.dp

/**
 * The liquid crest on the played part: its upper edge rises above the line by up to
 * [LIQUID_RISE] in two slow overlapping swells that drift while playing, so the fill reads as
 * something poured rather than a rigid bar. The lower edge stays straight, on the line.
 */
private val LIQUID_RISE = 3.dp

/** The crest's wavelengths (two, unrelated, so the contour never looks periodic). */
private val LIQUID_WAVE_LONG = 74.dp
private val LIQUID_WAVE_SHORT = 31.dp

/** The crest grows in from the start of the line over this distance. */
private val LIQUID_LEAD_IN = 22.dp

/** One sample of the contour every this many dp. */
private val LIQUID_STEP = 3.dp

/** Paused (or with reduced motion) the contour stays, lower and still. */
private const val LIQUID_SETTLED = 0.45f

/** The fill is a pastel version of the accent: lifted a little towards white. */
private const val PASTEL_LIFT = 0.18f

/** After a seek, the head holds the requested position until the player reports one this close (or the wait times out). */
private const val SEEK_SETTLE_TOLERANCE_MS = 750L
private const val SEEK_SETTLE_TIMEOUT_MS = 1_500L

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val ARC_FROM_BOTTOM = 90f
private const val HALF_TURN = 180f

/**
 * Now Playing's seek bar: one slim straight line with rounded ends. The played part runs in the
 * artwork-derived [accent], brightening toward its leading edge, which ends in a small round head
 * with a soft glow that travels with the music (quieter and still while paused). Scrubbing
 * thickens the line and grows the head under the finger; on release it settles back.
 *
 * Bookmarks show as small dots under the line and the A–B loop as a band with its two markers.
 * Tap to seek; drag to preview ([onPreview] gets the position under the finger, null when the drag
 * ends) and seek once on release. The whole 48 dp row takes touches.
 *
 * Progress is the player's own [position] (smoothed between its updates while playing, never ahead
 * of it by more than a few frames) read only while drawing: playback redraws the line without
 * recomposing anything.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun LinearSeekBar(
    position: State<Long>,
    isPlaying: Boolean,
    previewPositionMs: Long?,
    durationMs: Long,
    bookmarkPositions: List<Long>,
    loop: AbLoopState?,
    accent: Color,
    track: Color,
    bookmarkColor: Color,
    reduceMotion: Boolean,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val smooth = rememberSmoothPosition(position, isPlaying)
    val scrubbing = previewPositionMs != null
    // Full glow while playing or scrubbing, a quieter static one while paused; changes ease, nothing pulses.
    val glow by animateFloatAsState(
        targetValue = if (isPlaying || scrubbing) 1f else PAUSED_GLOW,
        animationSpec = tween(if (reduceMotion) 0 else Motion.HERO, easing = Motion.Standard),
        label = "seekGlow",
    )
    val scrub by animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "seekScrub",
    )
    val liquid by rememberLiquidStrength(isPlaying, reduceMotion)
    val drift = rememberLiquidDrift(isPlaying, reduceMotion)
    var settling by rememberSeekSettle(smooth, durationMs)
    val preview by rememberUpdatedState(previewPositionMs)
    val latestDuration by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState<(Long) -> Unit>({ target ->
        settling = target
        onSeek(target)
    })
    val latestOnPreview by rememberUpdatedState(onPreview)
    val seekDescription = stringResource(R.string.seekbar_cd_seek)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .semantics {
                    contentDescription = seekDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(position.value.toFloat(), 0f..latestDuration.coerceAtLeast(1L).toFloat())
                    setProgress { target ->
                        latestOnSeek(target.toLong().coerceIn(0L, latestDuration))
                        true
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        latestOnSeek(WaveformTimeline.positionAtX(offset.x, size.width.toFloat(), latestDuration))
                    }
                }.pointerInput(Unit) {
                    var last: Long? = null

                    fun at(x: Float) = WaveformTimeline.positionAtX(x, size.width.toFloat(), latestDuration)
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            last = at(offset.x)
                            latestOnPreview(last)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            last = at(change.position.x)
                            latestOnPreview(last)
                        },
                        onDragEnd = {
                            // One seek per drag, at the release point.
                            last?.let(latestOnSeek)
                            last = null
                            latestOnPreview(null)
                        },
                        onDragCancel = {
                            last = null
                            latestOnPreview(null)
                        },
                    )
                }.drawWithCache {
                    val thin = TRACK_THICKNESS.toPx()
                    val thick = TRACK_THICKNESS_SCRUBBING.toPx()
                    val glowRadius = GLOW_RADIUS.toPx()
                    val headRadius = HEAD_RADIUS.toPx()
                    val knobRadius = KNOB_RADIUS.toPx()
                    val contourWidth = CONTOUR_WIDTH.toPx()
                    val shadeExtra = SHADE_EXTRA.toPx()
                    val body = lerp(accent, Color.White, PASTEL_LIFT)
                    val tail = body.copy(alpha = body.alpha * TAIL_ALPHA)
                    val head = lerp(accent, Color.White, HEAD_LIFT)
                    val liquidPath = Path()
                    val crest =
                        LiquidCrest(
                            rise = LIQUID_RISE.toPx(),
                            longWave = LIQUID_WAVE_LONG.toPx(),
                            shortWave = LIQUID_WAVE_SHORT.toPx(),
                            leadIn = LIQUID_LEAD_IN.toPx(),
                        )
                    val step = LIQUID_STEP.toPx()
                    val centre = lerp(head, Color.White, CENTRE_LIFT)
                    val contour = lerp(head, Color.White, CONTOUR_LIFT).copy(alpha = CONTOUR_ALPHA)
                    onDrawBehind {
                        // Edge to edge, aligned with the time labels (as the touch mapping is); the knob may overhang the ends.
                        val left = 0f
                        val width = size.width
                        val y = size.height / 2
                        val thickness = thin + (thick - thin) * scrub
                        val duration = latestDuration
                        val fraction = WaveformTimeline.fractionOf(preview ?: settling ?: smooth.value, duration)
                        val headX = left + fraction * width
                        drawLoop(loop, duration, left, width, y, accent)
                        drawLine(track, Offset(left, y), Offset(left + width, y), thickness, StrokeCap.Round)
                        if (headX > left) {
                            liquidPath.liquidFill(crest, left, headX, y, thickness / 2, step, drift.value, liquid)
                            drawPath(liquidPath, Brush.horizontalGradient(listOf(tail, body, head), startX = left, endX = headX))
                        }
                        drawHead(
                            Offset(headX, y),
                            radius = headRadius + (knobRadius - headRadius) * scrub,
                            glow = glow,
                            glowRadius = glowRadius,
                            head = head,
                            centre = centre,
                            contour = contour,
                            contourWidth = contourWidth,
                            shadeExtra = shadeExtra,
                        )
                        val dot = BOOKMARK_RADIUS.toPx()
                        val dotY = y + BOOKMARK_GAP.toPx()
                        bookmarkPositions.forEach { ms ->
                            drawCircle(bookmarkColor, dot, Offset(left + WaveformTimeline.fractionOf(ms, duration) * width, dotY))
                        }
                    }
                },
    )
}

/** The crest's motion strength: full while playing, lower when paused or with reduced motion (eased, never pulsing). */
@Composable
private fun rememberLiquidStrength(
    isPlaying: Boolean,
    reduceMotion: Boolean,
): State<Float> =
    animateFloatAsState(
        targetValue = if (isPlaying && !reduceMotion) 1f else LIQUID_SETTLED,
        animationSpec = tween(if (reduceMotion) 0 else Motion.HERO, easing = Motion.Standard),
        label = "seekLiquid",
    )

/**
 * The crest's drift time in seconds: advances with the frame clock only while playing (and
 * motion is allowed), holds when paused. Read while drawing, so it redraws the bar only.
 */
@Composable
private fun rememberLiquidDrift(
    isPlaying: Boolean,
    reduceMotion: Boolean,
): State<Float> {
    val drift = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, reduceMotion) {
        if (!isPlaying || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                drift.floatValue += (now - last) / NANOS_PER_SECOND
                last = now
            }
        }
    }
    return drift
}

/**
 * A released seek's target, shown until the player reports a position near it (or a short
 * timeout passes), so the head never springs back to the old position in between. Cleared when
 * the track changes ([durationMs]).
 */
@Composable
private fun rememberSeekSettle(
    smooth: State<Long>,
    durationMs: Long,
): MutableState<Long?> {
    val settling = remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(settling.value) {
        val target = settling.value ?: return@LaunchedEffect
        withTimeoutOrNull(SEEK_SETTLE_TIMEOUT_MS) {
            snapshotFlow { smooth.value }.first { abs(it - target) <= SEEK_SETTLE_TOLERANCE_MS }
        }
        settling.value = null
    }
    LaunchedEffect(durationMs) { settling.value = null }
    return settling
}

/**
 * The crest of the played part above the line at x (px from the line's start), for drift time
 * (seconds) and motion strength (0..1): two overlapping swells drifting in opposite directions,
 * always above the line (between a quarter of [rise] and all of it), growing in over [leadIn]
 * from the start so the line keeps its rounded end.
 */
internal class LiquidCrest(
    private val rise: Float,
    longWave: Float,
    shortWave: Float,
    private val leadIn: Float,
) {
    private val longK = (2 * PI / longWave).toFloat()
    private val shortK = (2 * PI / shortWave).toFloat()

    fun heightAt(
        x: Float,
        time: Float,
        strength: Float,
    ): Float {
        val swell =
            LONG_SHARE * sin(x * longK - time * DRIFT_LONG) +
                (1f - LONG_SHARE) * sin(x * shortK + time * DRIFT_SHORT + SHORT_OFFSET)
        // swell is -1..1: the crest stays between BASE_SHARE and the full rise.
        val wave = BASE_SHARE + (1f - BASE_SHARE) * (swell + 1f) / 2f
        val t = (x / leadIn).coerceIn(0f, 1f)
        val envelope = t * t * (SMOOTHSTEP_A - SMOOTHSTEP_B * t)
        return rise * strength * wave * envelope
    }

    private companion object {
        const val LONG_SHARE = 0.62f
        const val BASE_SHARE = 0.25f
        const val SHORT_OFFSET = 1.3f

        /** Smoothstep (3t² − 2t³): the crest eases in from the line's start. */
        const val SMOOTHSTEP_A = 3f
        const val SMOOTHSTEP_B = 2f

        /** Drift of the swells, radians per second: slow, a breath rather than a ripple. */
        const val DRIFT_LONG = 0.9f
        const val DRIFT_SHORT = 1.6f
    }
}

/**
 * The played part as one shape: a rounded start, a straight lower edge on the line and the
 * liquid crest as its upper edge, up to [headX] (the head covers the end). Rebuilds the path in
 * place: no allocation per frame.
 */
@Suppress("LongParameterList")
private fun Path.liquidFill(
    crest: LiquidCrest,
    left: Float,
    headX: Float,
    y: Float,
    half: Float,
    step: Float,
    time: Float,
    strength: Float,
) {
    rewind()
    moveTo(left, y + half)
    // The rounded start: half a circle from the bottom, round the left, to the top.
    arcTo(Rect(left - half, y - half, left + half, y + half), ARC_FROM_BOTTOM, HALF_TURN, false)
    var x = left
    while (x < headX) {
        lineTo(x, y - half - crest.heightAt(x - left, time, strength))
        x += step
    }
    lineTo(headX, y - half - crest.heightAt(headX - left, time, strength))
    lineTo(headX, y + half)
    close()
}

/**
 * The progress head, drawn as the end of the played line: a compact glow in the head colour that
 * falls off softly, a faint dark rim (for bright backdrops), the knob running from a bright centre
 * to the played line's own end colour, and a slightly brighter contour. Plain draws, no blur.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawHead(
    at: Offset,
    radius: Float,
    glow: Float,
    glowRadius: Float,
    head: Color,
    centre: Color,
    contour: Color,
    contourWidth: Float,
    shadeExtra: Float,
) {
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to head.copy(alpha = GLOW_ALPHA * glow),
                GLOW_INNER to head.copy(alpha = GLOW_ALPHA * glow * 0.5f),
                1f to Color.Transparent,
                center = at,
                radius = glowRadius,
            ),
        radius = glowRadius,
        center = at,
    )
    drawCircle(Color.Black.copy(alpha = SHADE_ALPHA), radius + shadeExtra, at)
    drawCircle(
        brush = Brush.radialGradient(listOf(centre, head), center = at, radius = radius),
        radius = radius,
        center = at,
    )
    drawCircle(contour, radius - contourWidth / 2, at, style = Stroke(contourWidth))
}

/** The A–B region as a soft band over the line (stronger while looping) and its two markers. */
private fun DrawScope.drawLoop(
    loop: AbLoopState?,
    durationMs: Long,
    left: Float,
    width: Float,
    y: Float,
    color: Color,
) {
    if (loop == null) return
    val start = loop.startMs?.let { left + WaveformTimeline.fractionOf(it, durationMs) * width }
    val end = loop.endMs?.let { left + WaveformTimeline.fractionOf(it, durationMs) * width }
    val half = MARKER_HEIGHT.toPx() / 2
    if (start != null && end != null && end > start) {
        drawRect(
            color = color.copy(alpha = if (loop.enabled) LOOP_ALPHA else LOOP_DISABLED_ALPHA),
            topLeft = Offset(start, y - half),
            size = Size(end - start, half * 2),
        )
    }
    listOfNotNull(start, end).forEach { x ->
        drawLine(color, Offset(x, y - half), Offset(x, y + half), MARKER_WIDTH.toPx(), StrokeCap.Round)
    }
}
