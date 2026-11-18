package com.erhu.erhu.feature.settings.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.sound.dsp.BandSpec
import com.erhu.erhu.core.sound.dsp.combinedResponseDb
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

private const val MIN_FREQ = 20.0f
private const val MAX_FREQ = 20000.0f
private const val MAX_GAIN_DB = 12.0f
private const val MIN_GAIN_DB = -12.0f
private const val SAMPLE_POINTS = 160

/**
 * The EQ's frequency response (log 20 Hz - 20 kHz, -12..+12 dB) with one draggable point per band.
 *
 * [bands] are exactly what the processor applies (see [combinedResponseDb]), so the curve is what
 * is heard. A touch that starts inside the graph belongs to it until the finger lifts: every move
 * is consumed, so the screen around it never scrolls while a point is dragged. A touch near a point
 * drags it (frequency only when [frequencyEditable] is false, and gain only for gain filters); any
 * other touch selects the point nearest in frequency.
 */
@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")
fun Eq8CurveCanvas(
    bands: List<BandSpec>,
    selectedBandIndex: Int,
    sampleRate: Int,
    onNodeDrag: (bandIndex: Int, freqHz: Float, gainDb: Float) -> Unit,
    onNodeSelect: (bandIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    frequencyEditable: Boolean = true,
    enabled: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    // A faint inset well inside the settings panel (the panel already provides the surface).
    val containerBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionHaloColor = MaterialTheme.colorScheme.primaryContainer
    val fillColor = primaryColor.copy(alpha = 0.12f)

    val freqPoints =
        remember {
            FloatArray(SAMPLE_POINTS) { i ->
                val logMin = log10(MIN_FREQ)
                val logMax = log10(MAX_FREQ)
                10.0f.pow(logMin + (i.toFloat() / (SAMPLE_POINTS - 1)) * (logMax - logMin))
            }
        }
    val responseDb = remember(bands, sampleRate) { combinedResponseDb(bands, freqPoints, sampleRate) }

    // Read inside the gesture handler, which is not restarted when the bands change: restarting it
    // mid-drag (as when it was keyed to the bands) dropped the gesture and let the screen scroll.
    val currentBands by rememberUpdatedState(bands)
    val currentEnabled by rememberUpdatedState(enabled)
    val dragTo by rememberUpdatedState(onNodeDrag)
    val select by rememberUpdatedState(onNodeSelect)
    val moveFrequency by rememberUpdatedState(frequencyEditable)
    var draggedIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(Radius.mediumShape)
                .background(containerBgColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Owned from the first touch: neither this nor any later move reaches the scrolling screen.
                        down.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val touchRadius = 48.dp.toPx()
                        val slop = viewConfiguration.touchSlop
                        val nodes = currentBands
                        val distance = { b: Int -> hypot(down.position.x - freqToX(nodes[b].frequencyHz, w), down.position.y - nodeY(nodes[b], h)) }
                        // A touch on a point grabs it; anywhere else on the curve, dragging pulls the band nearest in frequency.
                        val near = nodes.indices.minByOrNull(distance)?.takeIf { distance(it) <= touchRadius }
                        val target = near ?: nodes.indices.minByOrNull { abs(freqToX(nodes[it].frequencyHz, w) - down.position.x) }
                        if (target != null && currentEnabled) {
                            draggedIndex = target
                            select(target)
                        }
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // Other fingers on the graph are swallowed too, and never take over the drag.
                            // (Consumed after reading the movement: a consumed change reports no movement.)
                            if (change == null || !change.pressed) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                            // A grabbed point follows at once; pulling the curve elsewhere starts past the touch slop, so a tap never nudges a band.
                            if (!moved && (near != null || (change.position - down.position).getDistance() > slop)) moved = change.position != down.position
                            event.changes.forEach { it.consume() }
                            val index = draggedIndex
                            if (index in nodes.indices && moved) {
                                val band = currentBands.getOrNull(index) ?: break
                                val freq = if (moveFrequency) xToFreq(change.position.x, w) else band.frequencyHz
                                val gain = if (band.type.hasGain) yToGain(change.position.y, h) else band.gainDb
                                dragTo(index, freq, gain)
                            }
                        }
                        draggedIndex = -1
                    }
                },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val width = size.width
            val height = size.height

            for (db in listOf(12f, 6f, 0f, -6f, -12f)) {
                val y = gainToY(db, height)
                drawLine(
                    color = if (db == 0f) gridColor.copy(alpha = 0.8f) else gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f,
                )
            }
            val gridFreqs = listOf(50f to "50", 100f to "100", 500f to "500", 1000f to "1k", 5000f to "5k", 10000f to "10k")
            for ((freq, label) in gridFreqs) {
                val x = freqToX(freq, width)
                drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = TextStyle(color = labelColor, fontSize = 9.sp),
                    topLeft = Offset(x + 2.dp.toPx(), height - 14.dp.toPx()),
                )
            }

            val curve = Path()
            for (i in freqPoints.indices) {
                val x = freqToX(freqPoints[i], width)
                val y = gainToY(responseDb[i], height)
                if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
            }
            val area =
                Path().apply {
                    addPath(curve)
                    lineTo(width, gainToY(0f, height))
                    lineTo(0f, gainToY(0f, height))
                    close()
                }
            val curveAlpha = if (enabled) 1f else 0.4f
            drawPath(area, color = fillColor.copy(alpha = fillColor.alpha * curveAlpha))
            drawPath(curve, color = primaryColor.copy(alpha = curveAlpha), style = Stroke(width = 3.dp.toPx()))

            bands.forEachIndexed { b, band ->
                val center = Offset(freqToX(band.frequencyHz, width), nodeY(band, height))
                val alpha = if (band.enabled && enabled) 1f else 0.35f
                val color = primaryColor.copy(alpha = alpha)
                val label = "${b + 1}"
                val labelSize = textMeasurer.measure(label, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)).size
                val labelAt = Offset(center.x - labelSize.width / 2f, center.y - labelSize.height / 2f)
                if (b == selectedBandIndex) {
                    drawCircle(color = selectionHaloColor.copy(alpha = alpha), radius = 16.dp.toPx(), center = center)
                    drawCircle(color = color, radius = 10.dp.toPx(), center = center)
                    drawText(textMeasurer, label, labelAt, TextStyle(color = onPrimaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                } else {
                    drawCircle(color = color, radius = 9.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                    drawText(textMeasurer, label, labelAt, TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** Gain filters sit at their gain; pass filters and notches, which have none, on the 0 dB line. */
private fun nodeY(
    band: BandSpec,
    height: Float,
): Float = gainToY(if (band.type.hasGain) band.gainDb else 0f, height)

private fun freqToX(
    f: Float,
    width: Float,
): Float {
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val logF = log10(f.coerceIn(MIN_FREQ, MAX_FREQ))
    return ((logF - logMin) / (logMax - logMin)) * width
}

private fun xToFreq(
    x: Float,
    width: Float,
): Float {
    if (width <= 0f) return 1000f
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val norm = (x / width).coerceIn(0f, 1f)
    return 10.0f.pow(logMin + norm * (logMax - logMin)).coerceIn(MIN_FREQ, MAX_FREQ)
}

private fun gainToY(
    db: Float,
    height: Float,
): Float {
    val norm = (db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) - MIN_GAIN_DB) / (MAX_GAIN_DB - MIN_GAIN_DB)
    return height * (1.0f - norm)
}

private fun yToGain(
    y: Float,
    height: Float,
): Float {
    if (height <= 0f) return 0f
    val norm = (1.0f - (y / height)).coerceIn(0f, 1f)
    return (MIN_GAIN_DB + norm * (MAX_GAIN_DB - MIN_GAIN_DB)).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
}
