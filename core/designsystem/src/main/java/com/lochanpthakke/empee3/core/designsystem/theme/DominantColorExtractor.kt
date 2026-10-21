package com.lochanpthakke.empee3.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ArtworkColors(
    val primary: Color,
    val topColor: Color,
    val bottomColor: Color,
    val accentColor: Color = primary,
)

object DominantColorExtractor {
    private const val SAMPLE_SIZE = 64
    private val DEFAULT_SLATE = Color(0xFF1E2228)

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    fun extractColors(bitmap: Bitmap?): ArtworkColors {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return ArtworkColors(DEFAULT_SLATE, DEFAULT_SLATE, DEFAULT_SLATE, Color(0xFFE53935))
        }

        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
            val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
            scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

            val primary = findDominantInPixels(pixels, 0, pixels.size) ?: averageColor(pixels) ?: DEFAULT_SLATE
            val topColor = findDominantInPixels(pixels, 0, pixels.size / 3) ?: primary
            val bottomColor = findDominantInPixels(pixels, (pixels.size * 2) / 3, pixels.size) ?: primary
            val accentColor = findVibrantAccentInPixels(pixels, primary) ?: primary

            ArtworkColors(primary = primary, topColor = topColor, bottomColor = bottomColor, accentColor = accentColor)
        } catch (_: Exception) {
            ArtworkColors(DEFAULT_SLATE, DEFAULT_SLATE, DEFAULT_SLATE, Color(0xFFE53935))
        }
    }

    private fun averageColor(pixels: IntArray): Color? {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (pixel in pixels) {
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 128) continue
            rSum += (pixel shr 16) and 0xFF
            gSum += (pixel shr 8) and 0xFF
            bSum += pixel and 0xFF
            count++
        }
        if (count == 0) return null
        return Color((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    private fun findDominantInPixels(
        pixels: IntArray,
        startIndex: Int,
        endIndex: Int,
    ): Color? {
        val safeEnd = endIndex.coerceAtMost(pixels.size)
        val safeStart = startIndex.coerceIn(0, safeEnd)
        if (safeStart >= safeEnd) return null

        val frequencyMap = mutableMapOf<Int, Float>()
        val colorSumMap = mutableMapOf<Int, LongArray>()
        val hsl = FloatArray(3)

        var chromaticBucketCount = 0

        for (i in safeStart until safeEnd) {
            val pixel = pixels[i]
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            rgbToHsl(r, g, b, hsl)
            val s = hsl[1]
            val l = hsl[2]

            // Filter out pitch black, blown-out white, or completely achromatic gray/snow
            // Includes S >= 0.08f and L in 0.05f..0.82f for warm earth tones/portraits
            if (l < 0.05f || l > 0.82f || s < 0.08f) continue

            chromaticBucketCount++

            // Quantize into 16x16x16 color bins
            val rBin = r shr 4
            val gBin = g shr 4
            val bBin = b shr 4
            val bucketKey = (rBin shl 8) or (gBin shl 4) or bBin

            val weight = 1f + (s * 2f)
            frequencyMap[bucketKey] = (frequencyMap[bucketKey] ?: 0f) + weight

            val sums = colorSumMap.getOrPut(bucketKey) { LongArray(4) }
            sums[0] += r.toLong()
            sums[1] += g.toLong()
            sums[2] += b.toLong()
            sums[3] += 1L
        }

        // Fallback if no chromatic pixels passed filter (e.g. monochrome/grayscale cover)
        if (chromaticBucketCount == 0 || frequencyMap.isEmpty()) {
            return unweightedDominantPixelAverage(pixels, safeStart, safeEnd)
        }

        var maxWeight = -1f
        var bestBucketKey = -1

        for ((bucketKey, weight) in frequencyMap) {
            if (weight > maxWeight) {
                maxWeight = weight
                bestBucketKey = bucketKey
            }
        }

        if (bestBucketKey == -1 || maxWeight <= 0f) return null

        val sums = colorSumMap[bestBucketKey] ?: return null
        val count = sums[3]
        if (count <= 0) return null

        val avgR = (sums[0] / count).toInt().coerceIn(0, 255)
        val avgG = (sums[1] / count).toInt().coerceIn(0, 255)
        val avgB = (sums[2] / count).toInt().coerceIn(0, 255)

        return Color(avgR, avgG, avgB)
    }

    private fun unweightedDominantPixelAverage(
        pixels: IntArray,
        startIndex: Int,
        endIndex: Int,
    ): Color? {
        val frequencyMap = mutableMapOf<Int, Int>()
        val colorSumMap = mutableMapOf<Int, LongArray>()

        for (i in startIndex until endIndex) {
            val pixel = pixels[i]
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val rBin = r shr 4
            val gBin = g shr 4
            val bBin = b shr 4
            val bucketKey = (rBin shl 8) or (gBin shl 4) or bBin

            frequencyMap[bucketKey] = (frequencyMap[bucketKey] ?: 0) + 1
            val sums = colorSumMap.getOrPut(bucketKey) { LongArray(4) }
            sums[0] += r.toLong()
            sums[1] += g.toLong()
            sums[2] += b.toLong()
            sums[3] += 1L
        }

        if (frequencyMap.isEmpty()) return null

        val bestBucket = frequencyMap.maxByOrNull { it.value }?.key ?: return null
        val sums = colorSumMap[bestBucket] ?: return null
        val count = sums[3]
        if (count <= 0) return null

        val avgR = (sums[0] / count).toInt().coerceIn(0, 255)
        val avgG = (sums[1] / count).toInt().coerceIn(0, 255)
        val avgB = (sums[2] / count).toInt().coerceIn(0, 255)

        return Color(avgR, avgG, avgB)
    }

    @Suppress("MagicNumber")
    private fun findVibrantAccentInPixels(
        pixels: IntArray,
        mainColor: Color,
    ): Color? {
        val mainArgb = mainColor.toArgb()
        val mainR = (mainArgb shr 16) and 0xFF
        val mainG = (mainArgb shr 8) and 0xFF
        val mainB = mainArgb and 0xFF

        val mainHsl = rgbToHsl(mainR, mainG, mainB)
        val mainH = mainHsl[0]
        val mainL = mainHsl[2]

        // Pass 1: Strict Vibrancy Filter
        val strictPass = scoreAccentBuckets(pixels, mainH, mainL, minS = 0.40f, minL = 0.25f, maxL = 0.80f, isRelaxed = false)
        if (strictPass != null) return strictPass

        // Pass 2: Relaxed Fallback Pass
        return scoreAccentBuckets(pixels, mainH, mainL, minS = 0.20f, minL = 0.15f, maxL = 0.88f, isRelaxed = true)
    }

    @Suppress("MagicNumber", "CyclomaticComplexMethod", "LongMethod")
    private fun scoreAccentBuckets(
        pixels: IntArray,
        mainH: Float,
        mainL: Float,
        minS: Float,
        minL: Float,
        maxL: Float,
        isRelaxed: Boolean,
    ): Color? {
        val hueBuckets = mutableMapOf<Int, MutableList<Int>>()
        val hsl = FloatArray(3)
        var totalValidPixels = 0

        for (pixel in pixels) {
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 128) continue

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            rgbToHsl(r, g, b, hsl)
            val s = hsl[1]
            val l = hsl[2]

            if (s < minS || l < minL || l > maxL) continue

            totalValidPixels++
            val hueBin = (hsl[0] / 10f).toInt().coerceIn(0, 35)
            hueBuckets.getOrPut(hueBin) { mutableListOf() }.add(pixel)
        }

        if (totalValidPixels == 0 || hueBuckets.isEmpty()) return null

        val isDarkImage = mainL < 0.22f

        // Population thresholds:
        // Dark moody image ($ome $exy$ongs 4 U): allow small high-chroma clusters (>= 0.15% or 0.35%)
        // Bright/Medium image (GloRilla portrait): require >= 2.0% population to reject 1-5 px noise flecks
        val minPopulationFraction =
            when {
                isDarkImage && !isRelaxed -> 0.0015f
                isDarkImage -> 0.0035f
                !isRelaxed -> 0.0200f
                else -> 0.0100f
            }
        val minClusterSize = (totalValidPixels * minPopulationFraction).roundToInt().coerceAtLeast(3)

        var maxScore = -1f
        var bestColor: Color? = null

        for ((_, cluster) in hueBuckets) {
            if (cluster.size < minClusterSize) continue

            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            for (p in cluster) {
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
            }
            val avgR = (rSum / cluster.size).toInt()
            val avgG = (gSum / cluster.size).toInt()
            val avgB = (bSum / cluster.size).toInt()

            rgbToHsl(avgR, avgG, avgB, hsl)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            val rawHueDiff = abs(h - mainH)
            val hueDiff = 180f - abs(rawHueDiff - 180f)
            val normalizedHueDiff = hueDiff / 180f

            val lightnessScore = 1f - (abs(l - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val populationWeight = (cluster.size.toFloat() / totalValidPixels).coerceIn(0f, 0.5f) * 2f

            val score =
                if (isDarkImage) {
                    (s * 2.2f) + (normalizedHueDiff * 2.8f) + (lightnessScore * 1.0f) + (populationWeight * 0.5f)
                } else {
                    (s * 1.5f) + (populationWeight * 2.5f) + (lightnessScore * 1.2f) + (normalizedHueDiff * 0.8f)
                }

            if (score > maxScore) {
                maxScore = score
                bestColor = Color(avgR, avgG, avgB)
            }
        }

        return bestColor
    }

    fun createThemeFromAccent(
        accentColor: Color,
        isGradient: Boolean = false,
    ): ThemeManifest = createThemeFromColors(mainColor = accentColor, accentColor = accentColor, isGradient = isGradient)

    @Suppress("MagicNumber", "LongMethod")
    fun createThemeFromColors(
        mainColor: Color,
        accentColor: Color,
        isGradient: Boolean = false,
    ): ThemeManifest {
        val mainArgb = mainColor.toArgb()
        val mR = (mainArgb shr 16) and 0xFF
        val mG = (mainArgb shr 8) and 0xFF
        val mB = mainArgb and 0xFF

        val mainHsl = rgbToHsl(mR, mG, mB)
        val mainH = mainHsl[0]
        val mainS = mainHsl[1]

        val backgroundColor: Color
        val surfaceColor: Color
        val containerColor: Color

        if (mainS > 0.08f) {
            if (mainH in 20f..50f) {
                // Warm earth-tones (taupe, bronze, gold)
                val bgS = (mainS * 0.90f).coerceIn(0.22f, 0.50f)
                backgroundColor = hslToColor(mainH, bgS, 0.09f)
                surfaceColor = hslToColor(mainH, bgS * 0.85f, 0.13f)
                containerColor = hslToColor(mainH, bgS * 0.75f, 0.19f)
            } else {
                val bgS = (mainS * 0.85f).coerceIn(0.25f, 0.65f)
                backgroundColor = hslToColor(mainH, bgS, 0.07f)
                surfaceColor = hslToColor(mainH, bgS * 0.75f, 0.11f)
                containerColor = hslToColor(mainH, bgS * 0.65f, 0.17f)
            }
        } else {
            backgroundColor = Color(0xFF0F1014)
            surfaceColor = Color(0xFF1B1D22)
            containerColor = Color(0xFF282B32)
        }

        // Post-process accent readability
        val accentArgb = accentColor.toArgb()
        val aR = (accentArgb shr 16) and 0xFF
        val aG = (accentArgb shr 8) and 0xFF
        val aB = accentArgb and 0xFF
        val accentHsl = rgbToHsl(aR, aG, aB)

        val boostedAccentS = accentHsl[1].coerceAtLeast(0.65f)
        val boostedAccentL = accentHsl[2].coerceIn(0.50f, 0.68f)
        val finalAccent = hslToColor(accentHsl[0], boostedAccentS, boostedAccentL)

        val finalAccentArgb = finalAccent.toArgb()
        val finalR = (finalAccentArgb shr 16) and 0xFF
        val finalG = (finalAccentArgb shr 8) and 0xFF
        val finalB = finalAccentArgb and 0xFF

        val accentLong = (0xFF000000L or (finalAccentArgb.toLong() and 0xFFFFFFFFL))
        val bgLong = (0xFF000000L or (backgroundColor.toArgb().toLong() and 0xFFFFFFFFL))
        val surfaceLong = (0xFF000000L or (surfaceColor.toArgb().toLong() and 0xFFFFFFFFL))
        val containerLong = (0xFF000000L or (containerColor.toArgb().toLong() and 0xFFFFFFFFL))

        val accentLuminance = (0.299f * (finalR / 255f) + 0.587f * (finalG / 255f) + 0.114f * (finalB / 255f))
        val onPrimaryLong = if (accentLuminance > 0.60f) 0xFF0A0C0EL else 0xFFFFFFFFL

        val themeId = if (isGradient) BuiltInThemes.GRADIENT_UI_ID else BuiltInThemes.DOMINANT_COLOR_ID
        val themeName = if (isGradient) "Gradient UI" else "Dominant Color"

        return ThemeManifest(
            id = themeId,
            name = themeName,
            author = "empee3",
            isDark = true,
            colors =
                EmPee3Colors(
                    primary = accentLong,
                    onPrimary = onPrimaryLong,
                    primaryContainer = containerLong,
                    onPrimaryContainer = 0xFFF8F9FAL,
                    secondary = accentLong,
                    onSecondary = 0xFFFFFFFFL,
                    background = bgLong,
                    onBackground = 0xFFF8F9FAL,
                    surface = surfaceLong,
                    onSurface = 0xFFF8F9FAL,
                    surfaceVariant = containerLong,
                    onSurfaceVariant = 0xFFA0AEC0L,
                    outline = containerLong,
                    bevelLight = 0xFF4A5568L,
                    bevelDark = 0xFF0A0C0EL,
                    lcdBackground = surfaceLong,
                    lcdText = 0xFFF8F9FAL,
                    lcdGlow = accentLong,
                    cardBackground = surfaceLong,
                    glassScrim = 0x99101216L,
                ),
            shapes = BuiltInThemes.DEFAULT_DARK.shapes,
            widgetConfig = BuiltInThemes.DEFAULT_DARK.widgetConfig,
        )
    }

    private fun rgbToHsl(
        r: Int,
        g: Int,
        b: Int,
        outHsl: FloatArray = FloatArray(3),
    ): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC

        val l = (maxC + minC) / 2f
        val s =
            if (delta == 0f) {
                0f
            } else if (l <= 0.5f) {
                delta / (maxC + minC)
            } else {
                delta / (2f - maxC - minC)
            }

        var h =
            if (delta == 0f) {
                0f
            } else if (maxC == rf) {
                ((gf - bf) / delta) % 6f
            } else if (maxC == gf) {
                ((bf - rf) / delta) + 2f
            } else {
                ((rf - gf) / delta) + 4f
            }
        h *= 60f
        if (h < 0f) h += 360f

        outHsl[0] = h
        outHsl[1] = s
        outHsl[2] = l
        return outHsl
    }

    private fun hslToColor(
        h: Float,
        s: Float,
        l: Float,
    ): Color {
        val normH = (h % 360f + 360f) % 360f
        val normS = s.coerceIn(0f, 1f)
        val normL = l.coerceIn(0f, 1f)

        val c = (1f - abs(2f * normL - 1f)) * normS
        val x = c * (1f - abs((normH / 60f) % 2f - 1f))
        val m = normL - c / 2f

        val (r1, g1, b1) =
            when {
                normH < 60f -> Triple(c, x, 0f)
                normH < 120f -> Triple(x, c, 0f)
                normH < 180f -> Triple(0f, c, x)
                normH < 240f -> Triple(0f, x, c)
                normH < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)

        return Color(r, g, b)
    }
}
