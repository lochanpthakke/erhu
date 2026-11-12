package com.erhu.erhu.feature.library

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.model.AbLoopState
import com.erhu.erhu.core.model.Waveform
import com.erhu.erhu.core.model.WaveformTimeline

/** Touch height of the waveform: well above the 48 dp minimum target. */
private val WAVEFORM_HEIGHT = 56.dp
private val BAR_WIDTH = 3.dp
private val BAR_GAP = 2.dp
private val MIN_BAR_HEIGHT = 2.dp
private val MARKER_WIDTH = 2.dp
private val BOOKMARK_RADIUS = 2.5.dp

/** Bar level shown while the waveform is loading or unavailable: a quiet flat line, still seekable. */
private const val PLACEHOLDER_LEVEL = 0.12f
private const val UNPLAYED_ALPHA = 0.35f
private const val LOOP_FILL_ALPHA = 0.16f
private const val LOOP_FILL_DISABLED_ALPHA = 0.07f

/**
 * Interactive waveform seek bar: the track's real amplitude envelope, played part in the accent,
 * bookmark ticks along the bottom and the A–B loop region. Tap to seek; drag to preview ([onPreview]
 * receives the position under the finger, null when the drag ends) and seek once on release.
 *
 * Only [position] changes four times a second, and it is read while drawing, so playback ticks
 * redraw the bar without recomposing anything.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun WaveformSeekBar(
    waveform: Waveform?,
    position: State<Long>,
    previewPositionMs: Long?,
    durationMs: Long,
    bookmarkPositions: List<Long>,
    loop: AbLoopState?,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val unplayed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNPLAYED_ALPHA)
    val markerColor = MaterialTheme.colorScheme.secondary
    val bookmarkColor = MaterialTheme.colorScheme.onSurface
    val preview by rememberUpdatedState(previewPositionMs)
    val latestDuration by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnPreview by rememberUpdatedState(onPreview)
    val seekDescription = stringResource(R.string.seekbar_cd_seek)
    val waveformStateDescription =
        stringResource(if (waveform == null) R.string.seekbar_state_waveform_unavailable else R.string.seekbar_state_waveform)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(WAVEFORM_HEIGHT)
                .semantics {
                    contentDescription = seekDescription
                    stateDescription = waveformStateDescription
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
                    val barWidth = BAR_WIDTH.toPx()
                    val step = barWidth + BAR_GAP.toPx()
                    val barCount = ((size.width + BAR_GAP.toPx()) / step).toInt().coerceAtLeast(1)
                    val levels = barLevels(waveform, barCount)
                    val minBar = MIN_BAR_HEIGHT.toPx()
                    val radius = CornerRadius(barWidth / 2, barWidth / 2)
                    onDrawBehind {
                        val duration = latestDuration
                        val playedX = WaveformTimeline.fractionOf(preview ?: position.value, duration) * size.width
                        drawLoop(loop, duration, markerColor)
                        for (i in 0 until barCount) {
                            val x = i * step
                            val height = (levels[i] * size.height).coerceAtLeast(minBar)
                            drawRoundRect(
                                color = if (x + barWidth / 2 <= playedX) played else unplayed,
                                topLeft = Offset(x, (size.height - height) / 2),
                                size = Size(barWidth, height),
                                cornerRadius = radius,
                            )
                        }
                        val dot = BOOKMARK_RADIUS.toPx()
                        bookmarkPositions.forEach { ms ->
                            drawCircle(
                                bookmarkColor,
                                dot,
                                Offset(WaveformTimeline.fractionOf(ms, duration) * size.width, size.height - dot),
                            )
                        }
                    }
                },
    )
}

/** The A–B region (stronger while looping) and its marker lines. */
private fun DrawScope.drawLoop(
    loop: AbLoopState?,
    durationMs: Long,
    color: androidx.compose.ui.graphics.Color,
) {
    if (loop == null) return
    val start = loop.startMs?.let { WaveformTimeline.fractionOf(it, durationMs) * size.width }
    val end = loop.endMs?.let { WaveformTimeline.fractionOf(it, durationMs) * size.width }
    if (start != null && end != null && end > start) {
        drawRect(
            color = color.copy(alpha = if (loop.enabled) LOOP_FILL_ALPHA else LOOP_FILL_DISABLED_ALPHA),
            topLeft = Offset(start, 0f),
            size = Size(end - start, size.height),
        )
    }
    val width = MARKER_WIDTH.toPx()
    listOfNotNull(start, end).forEach { x ->
        drawRect(color = color, topLeft = Offset(x - width / 2, 0f), size = Size(width, size.height))
    }
}

/** [count] bar heights 0..1 from the waveform (the loudest level of each slice), or a flat placeholder. */
private fun barLevels(
    waveform: Waveform?,
    count: Int,
): FloatArray {
    val levels = waveform?.levels?.takeIf { it.isNotEmpty() } ?: return FloatArray(count) { PLACEHOLDER_LEVEL }
    return FloatArray(count) { bar ->
        val from = bar * levels.size / count
        val to = ((bar + 1) * levels.size / count).coerceAtLeast(from + 1).coerceAtMost(levels.size)
        var max = 0f
        for (i in from until to) if (levels[i] > max) max = levels[i]
        max
    }
}
