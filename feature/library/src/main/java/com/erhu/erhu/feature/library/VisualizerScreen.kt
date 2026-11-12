package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.PlaybackState
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.VisualizerMode
import com.erhu.erhu.core.sound.visualizer.VisualizerEngine
import com.erhu.erhu.core.sound.visualizer.VisualizerStatus
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val FULL_RATE_FPS = 60
private const val REDUCED_RATE_FPS = 24
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val SCRIM_ALPHA = 0.94f
private const val UNPLAYED_ALPHA = 0.18f

/**
 * The visualizer: a presentation layer over Now Playing that draws the music being played. It owns
 * no playback state; its controls call the same playback commands as Now Playing. Analysis runs
 * only while this is on screen and the app is visible: the audio tap is held between start and
 * stop of the lifecycle, and frames come from the display's frame clock (capped), which stops in
 * the background. Each frame only redraws the canvas; nothing recomposes per frame.
 */
@Composable
@Suppress("LongMethod")
internal fun VisualizerScreen(
    playbackState: PlaybackState,
    viewModel: VisualizerViewModel,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val engine = remember { VisualizerEngine(viewModel.audioTap) }
    val frame = remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf(VisualizerStatus.WAITING) }
    var visible by remember { mutableStateOf(false) }

    LifecycleStartEffect(viewModel.audioTap) {
        viewModel.audioTap.acquire()
        visible = true
        onStopOrDispose {
            visible = false
            viewModel.audioTap.release()
            engine.reset()
        }
    }
    LaunchedEffect(settings) { engine.apply(settings) }
    LaunchedEffect(visible, settings.reduceMotion) {
        if (!visible) return@LaunchedEffect
        val interval = NANOS_PER_SECOND / if (settings.reduceMotion) REDUCED_RATE_FPS else FULL_RATE_FPS
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - last >= interval) {
                    last = now
                    engine.update(now, viewModel.clock())
                    frame.longValue = now
                    if (engine.status != status) status = engine.status
                }
            }
        }
    }

    val song = playbackState.currentSong
    val isPlaying = playbackState.status == com.erhu.erhu.core.model.PlaybackStatus.PLAYING
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA))
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.visualizer_cd_close))
                }
                Text(
                    stringResource(R.string.player_menu_visualizer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            ModeChips(settings.mode, viewModel::setMode)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (settings.mode == VisualizerMode.CIRCULAR_SPECTRUM && song != null) {
                    // Sits inside the ring of spokes (same proportions as drawCircle).
                    val artwork = minOf(maxWidth, maxHeight) * (CIRCLE_INNER * 2 * ARTWORK_FILL)
                    SongArtwork(song = song, modifier = Modifier.size(artwork).clip(CircleShape))
                }
                VisualizerCanvas(mode = settings.mode, engine = engine, frame = frame, modifier = Modifier.fillMaxSize())
                StatusMessage(status, song)
            }
            TrackInfo(song)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.cd_previous_track), modifier = Modifier.size(IconSize.large))
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp), shape = CircleShape) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(IconSize.hero),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.cd_next_track), modifier = Modifier.size(IconSize.large))
                }
            }
        }
    }
}

@Composable
private fun ModeChips(
    mode: VisualizerMode,
    onSelect: (VisualizerMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.sm)) {
        listOf(
            VisualizerMode.SPECTRUM_BARS to R.string.visualizer_mode_bars,
            VisualizerMode.CIRCULAR_SPECTRUM to R.string.visualizer_mode_circle,
            VisualizerMode.OSCILLOSCOPE to R.string.visualizer_mode_waveform,
        ).forEach { (value, labelRes) ->
            FilterChip(selected = mode == value, onClick = { onSelect(value) }, label = { Text(stringResource(labelRes)) })
        }
    }
}

@Composable
private fun StatusMessage(
    status: VisualizerStatus,
    song: Song?,
) {
    val textRes =
        when {
            song == null -> R.string.visualizer_empty
            status == VisualizerStatus.UNAVAILABLE -> R.string.visualizer_unavailable
            status == VisualizerStatus.WAITING -> R.string.visualizer_waiting
            status == VisualizerStatus.PAUSED -> R.string.visualizer_paused
            else -> null
        } ?: return
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(Spacing.xl),
    )
}

@Composable
private fun TrackInfo(song: Song?) {
    if (song == null) return
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Draws the engine's current values. Reading [frame] in the draw phase makes each analysed frame
 * redraw only this canvas. All geometry buffers are reused.
 */
@Composable
private fun VisualizerCanvas(
    mode: VisualizerMode,
    engine: VisualizerEngine,
    frame: MutableLongState,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.onSurface.copy(alpha = UNPLAYED_ALPHA)
    val path = remember { Path() }
    val description =
        stringResource(
            when (mode) {
                VisualizerMode.SPECTRUM_BARS -> R.string.visualizer_cd_bars
                VisualizerMode.CIRCULAR_SPECTRUM -> R.string.visualizer_cd_circle
                VisualizerMode.OSCILLOSCOPE -> R.string.visualizer_cd_waveform
            },
        )
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        frame.longValue // redraw on every analysed frame
        when (mode) {
            VisualizerMode.SPECTRUM_BARS -> drawBars(engine.spectrum, accent, soft)
            VisualizerMode.CIRCULAR_SPECTRUM -> drawCircle(engine.spectrum, accent, soft)
            VisualizerMode.OSCILLOSCOPE -> drawScope(engine.waveform, path, accent, soft)
        }
    }
}

private fun DrawScope.drawBars(
    levels: FloatArray,
    accent: Color,
    soft: Color,
) {
    val count = levels.size
    val slot = size.width / count
    val barWidth = slot * BAR_FILL
    val baseline = size.height * BARS_BASELINE
    val maxHeight = size.height * BARS_HEIGHT
    val minHeight = MIN_BAR.dp.toPx()
    val radius = CornerRadius(barWidth / 2, barWidth / 2)
    for (i in 0 until count) {
        val height = (levels[i] * maxHeight).coerceAtLeast(minHeight)
        val x = i * slot + (slot - barWidth) / 2
        drawRoundRect(
            color = if (levels[i] > 0.02f) accent.copy(alpha = BAR_MIN_ALPHA + (1 - BAR_MIN_ALPHA) * levels[i]) else soft,
            topLeft = Offset(x, baseline - height),
            size = Size(barWidth, height),
            cornerRadius = radius,
        )
    }
}

private fun DrawScope.drawCircle(
    levels: FloatArray,
    accent: Color,
    soft: Color,
) {
    val center = Offset(size.width / 2, size.height / 2)
    val inner = min(size.width, size.height) * CIRCLE_INNER
    val reach = min(size.width, size.height) * CIRCLE_REACH
    val stroke = (2 * PI * inner / (levels.size * 2) * SPOKE_FILL).toFloat()
    drawCircle(color = soft, radius = inner, center = center, style = Stroke(width = 1.dp.toPx()))
    // Mirrored halves: bass at the top, treble at the bottom on both sides, so the shape stays balanced.
    val spokes = levels.size * 2
    for (s in 0 until spokes) {
        val band = if (s < levels.size) s else spokes - 1 - s
        val level = levels[band]
        val angle = (2 * PI * s / spokes - PI / 2).toFloat()
        val dx = cos(angle)
        val dy = sin(angle)
        val start = Offset(center.x + dx * (inner + stroke), center.y + dy * (inner + stroke))
        val length = (level * reach).coerceAtLeast(MIN_BAR.dp.toPx())
        drawLine(
            color = if (level > 0.02f) accent.copy(alpha = BAR_MIN_ALPHA + (1 - BAR_MIN_ALPHA) * level) else soft,
            start = start,
            end = Offset(start.x + dx * length, start.y + dy * length),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawScope(
    samples: FloatArray,
    path: Path,
    accent: Color,
    soft: Color,
) {
    val mid = size.height / 2
    val amplitude = size.height * SCOPE_HEIGHT
    drawLine(soft, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
    path.reset()
    val step = size.width / (samples.size - 1)
    for (i in samples.indices) {
        val x = i * step
        val y = mid - samples[i] * amplitude
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, accent, style = Stroke(width = SCOPE_STROKE.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private const val BAR_FILL = 0.62f
private const val BARS_BASELINE = 0.78f
private const val BARS_HEIGHT = 0.62f
private const val MIN_BAR = 2
private const val BAR_MIN_ALPHA = 0.55f
private const val CIRCLE_INNER = 0.24f
private const val CIRCLE_REACH = 0.22f
private const val SPOKE_FILL = 0.55f
private const val ARTWORK_FILL = 0.86f
private const val SCOPE_HEIGHT = 0.32f
private const val SCOPE_STROKE = 2
