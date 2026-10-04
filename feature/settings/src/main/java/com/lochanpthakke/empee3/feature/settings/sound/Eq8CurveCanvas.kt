package com.lochanpthakke.empee3.feature.settings.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.model.BandConfig
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

private const val MIN_FREQ = 20.0f
private const val MAX_FREQ = 20000.0f
private const val MAX_GAIN_DB = 15.0f
private const val MIN_GAIN_DB = -15.0f
private const val SAMPLE_POINTS = 120

@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun Eq8CurveCanvas(
    bands: List<BandConfig>,
    activeBandCount: Int,
    selectedBandIndex: Int,
    customEqAudioProcessor: CustomEqAudioProcessor,
    onNodeDrag: (bandIndex: Int, freqHz: Float, gainDb: Float) -> Unit,
    onNodeSelect: (bandIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val containerBgColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val selectionHaloColor = MaterialTheme.colorScheme.primaryContainer

    val freqPoints =
        remember {
            FloatArray(SAMPLE_POINTS) { i ->
                val logMin = log10(MIN_FREQ)
                val logMax = log10(MAX_FREQ)
                val logFreq = logMin + (i.toFloat() / (SAMPLE_POINTS - 1)) * (logMax - logMin)
                10.0f.pow(logFreq)
            }
        }

    val responseDb =
        remember(bands, activeBandCount) {
            customEqAudioProcessor.getCombinedFrequencyResponse(freqPoints)
        }

    var draggedIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(Radius.mediumShape)
                .background(containerBgColor)
                .pointerInput(bands, activeBandCount) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            var closestIdx = -1
                            var minDistance = 48.dp.toPx()

                            for (b in 0 until activeBandCount) {
                                val nodeX = freqToX(bands.getOrNull(b)?.frequencyHz ?: CustomEqAudioProcessor.BAND_FREQUENCIES[b], w)
                                val nodeY = gainToY(bands.getOrNull(b)?.gainDb ?: 0f, h)
                                val dist = hypot(offset.x - nodeX, offset.y - nodeY)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestIdx = b
                                }
                            }

                            if (closestIdx != -1) {
                                onNodeSelect(closestIdx)
                            }
                        },
                    )
                }
                .pointerInput(bands, activeBandCount) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            var closestIdx = -1
                            var minDistance = 48.dp.toPx()

                            for (b in 0 until activeBandCount) {
                                val nodeX = freqToX(bands.getOrNull(b)?.frequencyHz ?: CustomEqAudioProcessor.BAND_FREQUENCIES[b], w)
                                val nodeY = gainToY(bands.getOrNull(b)?.gainDb ?: 0f, h)
                                val dist = hypot(offset.x - nodeX, offset.y - nodeY)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestIdx = b
                                }
                            }

                            if (closestIdx != -1) {
                                draggedIndex = closestIdx
                                onNodeSelect(closestIdx)
                            }
                        },
                        onDragEnd = { draggedIndex = -1 },
                        onDragCancel = { draggedIndex = -1 },
                        onDrag = { change, _ ->
                            if (draggedIndex in 0 until activeBandCount) {
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val newFreq = xToFreq(change.position.x, w)
                                val newGain = yToGain(change.position.y, h)
                                onNodeDrag(draggedIndex, newFreq, newGain)
                            }
                        },
                    )
                },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val width = size.width
            val height = size.height

            // Helper: horizontal dB grid lines (+12, +6, 0, -6, -12)
            val dbSteps = listOf(12f, 6f, 0f, -6f, -12f)
            for (db in dbSteps) {
                val y = gainToY(db, height)
                drawLine(
                    color = if (db == 0f) gridColor.copy(alpha = 0.8f) else gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f,
                )
            }

            // Helper: vertical frequency grid lines
            val gridFreqs = listOf(60f, 250f, 1000f, 4000f, 12000f)
            for (freq in gridFreqs) {
                val x = freqToX(freq, width)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                )
            }

            // Draw EQ Combined Response Curve
            val curvePath = Path()
            for (i in freqPoints.indices) {
                val x = freqToX(freqPoints[i], width)
                val y = gainToY(responseDb[i], height)
                if (i == 0) {
                    curvePath.moveTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                }
            }

            drawPath(
                path = curvePath,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx()),
            )

            // Draw active band nodes on the curve
            for (b in 0 until activeBandCount) {
                val band = bands.getOrNull(b)
                val freq = band?.frequencyHz ?: CustomEqAudioProcessor.BAND_FREQUENCIES[b]
                val gain = band?.gainDb ?: 0f
                val isSelected = b == selectedBandIndex

                val nodeX = freqToX(freq, width)
                val nodeY = gainToY(gain, height)

                if (isSelected) {
                    // Selected Node: filled ring + halo
                    drawCircle(
                        color = selectionHaloColor,
                        radius = 16.dp.toPx(),
                        center = Offset(nodeX, nodeY),
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 10.dp.toPx(),
                        center = Offset(nodeX, nodeY),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${b + 1}",
                        style = TextStyle(color = onPrimaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(nodeX - 3.dp.toPx(), nodeY - 6.dp.toPx()),
                    )
                } else {
                    // Unselected Node: hollow ring with number
                    drawCircle(
                        color = primaryColor,
                        radius = 9.dp.toPx(),
                        center = Offset(nodeX, nodeY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${b + 1}",
                        style = TextStyle(color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(nodeX - 3.dp.toPx(), nodeY - 6.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun freqToX(f: Float, width: Float): Float {
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val logF = log10(f.coerceIn(MIN_FREQ, MAX_FREQ))
    return ((logF - logMin) / (logMax - logMin)) * width
}

private fun xToFreq(x: Float, width: Float): Float {
    if (width <= 0f) return 1000f
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val norm = (x / width).coerceIn(0f, 1f)
    val logF = logMin + norm * (logMax - logMin)
    return 10.0f.pow(logF).coerceIn(MIN_FREQ, MAX_FREQ)
}

private fun gainToY(db: Float, height: Float): Float {
    val clamped = db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    val norm = (clamped - MIN_GAIN_DB) / (MAX_GAIN_DB - MIN_GAIN_DB)
    return height * (1.0f - norm)
}

private fun yToGain(y: Float, height: Float): Float {
    if (height <= 0f) return 0f
    val norm = (1.0f - (y / height)).coerceIn(0f, 1f)
    return (MIN_GAIN_DB + norm * (MAX_GAIN_DB - MIN_GAIN_DB)).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
}
