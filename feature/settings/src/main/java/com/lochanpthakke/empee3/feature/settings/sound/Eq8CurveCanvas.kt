package com.lochanpthakke.empee3.feature.settings.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import kotlin.math.log10
import kotlin.math.pow

private const val MIN_FREQ = 20.0f
private const val MAX_FREQ = 20000.0f
private const val MAX_GAIN_DB = 12.0f
private const val MIN_GAIN_DB = -12.0f
private const val SAMPLE_POINTS = 120

@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
fun Eq8CurveCanvas(
    bandGains: FloatArray,
    customEqAudioProcessor: CustomEqAudioProcessor,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

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
        remember(bandGains) {
            customEqAudioProcessor.getCombinedFrequencyResponse(freqPoints)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(Radius.mediumShape)
                .background(Color(0xFF121212)),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val width = size.width
            val height = size.height

            // Helper: log frequency to X coordinate
            fun freqToX(f: Float): Float {
                val logMin = log10(MIN_FREQ)
                val logMax = log10(MAX_FREQ)
                val logF = log10(f.coerceIn(MIN_FREQ, MAX_FREQ))
                return ((logF - logMin) / (logMax - logMin)) * width
            }

            // Helper: dB gain to Y coordinate
            fun gainToY(db: Float): Float {
                val clamped = db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
                val norm = (clamped - MIN_GAIN_DB) / (MAX_GAIN_DB - MIN_GAIN_DB)
                return height * (1.0f - norm)
            }

            // Draw horizontal dB grid lines (+12, +6, 0, -6, -12)
            val dbSteps = listOf(12f, 6f, 0f, -6f, -12f)
            for (db in dbSteps) {
                val y = gainToY(db)
                drawLine(
                    color = if (db == 0f) gridColor.copy(alpha = 0.8f) else gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f,
                )
            }

            // Draw band center frequency vertical grid lines
            for (freq in CustomEqAudioProcessor.BAND_FREQUENCIES) {
                val x = freqToX(freq)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                )
            }

            // Draw EQ Response Curve
            val curvePath = Path()
            for (i in freqPoints.indices) {
                val x = freqToX(freqPoints[i])
                val y = gainToY(responseDb[i])
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

            // Draw 8 band nodes on the curve
            for (b in 0 until CustomEqAudioProcessor.BAND_COUNT) {
                val freq = CustomEqAudioProcessor.BAND_FREQUENCIES[b]
                val gain = bandGains.getOrElse(b) { 0f }
                val nodeX = freqToX(freq)
                val nodeY = gainToY(gain)

                // Outer halo
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = 12.dp.toPx(),
                    center = Offset(nodeX, nodeY),
                )
                // Center node
                drawCircle(
                    color = primaryColor,
                    radius = 8.dp.toPx(),
                    center = Offset(nodeX, nodeY),
                )

                // Node number text
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${b + 1}",
                    style = TextStyle(color = Color.Black, fontSize = 10.sp),
                    topLeft = Offset(nodeX - 3.dp.toPx(), nodeY - 6.dp.toPx()),
                )
            }
        }
    }
}
