package com.lochanpthakke.empee3.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ArtworkColors(
    val primary: Color,
    val topColor: Color,
    val bottomColor: Color,
)

object DominantColorExtractor {
    private const val SAMPLE_SIZE = 48

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    fun extractColors(bitmap: Bitmap?): ArtworkColors {
        val fallback = Color(0xFF8A2BE2)
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return ArtworkColors(fallback, fallback, Color(0xFF4B0082))
        }

        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, false)
            val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
            scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

            val primary = findDominantInPixels(pixels, 0, pixels.size) ?: fallback
            val topColor = findDominantInPixels(pixels, 0, pixels.size / 3) ?: primary
            val bottomColor = findDominantInPixels(pixels, (pixels.size * 2) / 3, pixels.size) ?: primary

            ArtworkColors(primary, topColor, bottomColor)
        } catch (_: Exception) {
            ArtworkColors(fallback, fallback, Color(0xFF4B0082))
        }
    }

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    private fun findDominantInPixels(
        pixels: IntArray,
        startIndex: Int,
        endIndex: Int,
    ): Color? {
        var maxScore = -1f
        var bestColor = 0

        val safeEnd = endIndex.coerceAtMost(pixels.size)
        val safeStart = startIndex.coerceIn(0, safeEnd)

        for (i in safeStart until safeEnd) {
            val pixel = pixels[i]
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 128) continue

            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2f
            val s =
                if (maxC == minC) {
                    0f
                } else if (l <= 0.5f) {
                    (maxC - minC) / (maxC + minC)
                } else {
                    (maxC - minC) / (2f - maxC - minC)
                }

            if (s < 0.12f || l < 0.10f || l > 0.90f) continue

            val score = s * (1f - abs(l - 0.5f))
            if (score > maxScore) {
                maxScore = score
                bestColor = pixel
            }
        }

        return if (maxScore > 0f) Color(bestColor) else null
    }

    @Suppress("MagicNumber", "LongMethod")
    fun createThemeFromAccent(
        accentColor: Color,
        isGradient: Boolean = false,
    ): ThemeManifest {
        val argb = accentColor.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        val bgR = (0x0F * 0.85f + r * 0.15f).toInt().coerceIn(0, 255)
        val bgG = (0x10 * 0.85f + g * 0.15f).toInt().coerceIn(0, 255)
        val bgB = (0x14 * 0.85f + b * 0.15f).toInt().coerceIn(0, 255)
        val backgroundColor = (0xFF000000L or (bgR.toLong() shl 16) or (bgG.toLong() shl 8) or bgB.toLong())

        val sR = (0x1C * 0.80f + r * 0.20f).toInt().coerceIn(0, 255)
        val sG = (0x20 * 0.80f + g * 0.20f).toInt().coerceIn(0, 255)
        val sB = (0x28 * 0.80f + b * 0.20f).toInt().coerceIn(0, 255)
        val surfaceColor = (0xFF000000L or (sR.toLong() shl 16) or (sG.toLong() shl 8) or sB.toLong())

        val cR = (0x2A * 0.70f + r * 0.30f).toInt().coerceIn(0, 255)
        val cG = (0x32 * 0.70f + g * 0.30f).toInt().coerceIn(0, 255)
        val cB = (0x42 * 0.70f + b * 0.30f).toInt().coerceIn(0, 255)
        val containerColor = (0xFF000000L or (cR.toLong() shl 16) or (cG.toLong() shl 8) or cB.toLong())

        val primaryLong = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

        val themeId = if (isGradient) BuiltInThemes.GRADIENT_UI_ID else BuiltInThemes.DOMINANT_COLOR_ID
        val themeName = if (isGradient) "Gradient UI" else "Dominant Color"

        return ThemeManifest(
            id = themeId,
            name = themeName,
            author = "empee3",
            isDark = true,
            colors =
                EmPee3Colors(
                    primary = primaryLong,
                    onPrimary = 0xFFFFFFFFL,
                    primaryContainer = containerColor,
                    onPrimaryContainer = 0xFFF8F9FAL,
                    secondary = primaryLong,
                    onSecondary = 0xFFFFFFFFL,
                    background = backgroundColor,
                    onBackground = 0xFFF8F9FAL,
                    surface = surfaceColor,
                    onSurface = 0xFFF8F9FAL,
                    surfaceVariant = containerColor,
                    onSurfaceVariant = 0xFFA0AEC0L,
                    outline = containerColor,
                    bevelLight = 0xFF4A5568L,
                    bevelDark = 0xFF0A0C0EL,
                    lcdBackground = surfaceColor,
                    lcdText = 0xFFF8F9FAL,
                    lcdGlow = primaryLong,
                    cardBackground = surfaceColor,
                    glassScrim = 0x99101216L,
                ),
            shapes = BuiltInThemes.DEFAULT_DARK.shapes,
            widgetConfig = BuiltInThemes.DEFAULT_DARK.widgetConfig,
        )
    }
}
