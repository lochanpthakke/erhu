package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.model.AbLoopState
import com.lochanpthakke.empee3.core.model.WaveformTimeline

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
    val preview by rememberUpdatedState(previewPositionMs)
    val latestDuration by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnPreview by rememberUpdatedState(onPreview)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .semantics {
                    contentDescription = "Seek"
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
                    val tail = accent.copy(alpha = accent.alpha * TAIL_ALPHA)
                    val head = lerp(accent, Color.White, HEAD_LIFT)
                    val centre = lerp(head, Color.White, CENTRE_LIFT)
                    val contour = lerp(head, Color.White, CONTOUR_LIFT).copy(alpha = CONTOUR_ALPHA)
                    onDrawBehind {
                        // Edge to edge, aligned with the time labels (as the touch mapping is); the knob may overhang the ends.
                        val left = 0f
                        val width = size.width
                        val y = size.height / 2
                        val thickness = thin + (thick - thin) * scrub
                        val duration = latestDuration
                        val fraction = WaveformTimeline.fractionOf(preview ?: smooth.value, duration)
                        val headX = left + fraction * width
                        drawLoop(loop, duration, left, width, y, accent)
                        drawLine(track, Offset(left, y), Offset(left + width, y), thickness, StrokeCap.Round)
                        if (headX > left) {
                            drawLine(
                                brush = Brush.horizontalGradient(listOf(tail, accent, head), startX = left, endX = headX),
                                start = Offset(left, y),
                                end = Offset(headX, y),
                                strokeWidth = thickness,
                                cap = StrokeCap.Round,
                            )
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
