// Colour-space formulas (HSL sextants, sRGB transfer, WCAG weights): fixed constants, not tunables.
@file:Suppress("MagicNumber")

package com.lochanpthakke.empee3.feature.library

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtworkLoader
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Now Playing's surface colours, taken from the album artwork: a calm [surface] in the artwork's
 * own hue (muted, so text reads well), a [gradient] around it for opaque surfaces (a lighter tone
 * turned one way, the surface, a deeper tone turned the other way: tonal depth, not the dominant
 * colour everywhere), and text colours chosen for contrast against every stop of it — light text
 * on dark surfaces, dark text on genuinely light ones. ARGB ints.
 */
@Immutable
data class LyricsPalette(
    val surface: Int,
    /** Top to bottom; [surface] in the middle. */
    val gradient: List<Int>,
    /** Headings and the active line. */
    val content: Int,
    /** Inactive lines: secondary, still readable (at least 3:1). */
    val secondary: Int,
    /** A restrained accent in the artwork's hue (the timed word), at least 4.5:1. */
    val accent: Int,
) {
    companion object {
        /** No artwork (or not yet loaded): empee3's own deep burgundy. */
        val FALLBACK: LyricsPalette by lazy { from(hue = 345f, saturation = 0.38f, artworkLightness = 0.3f) }

        /** Below this weighted saturation the artwork reads as black and white: a neutral surface. */
        private const val NEUTRAL_SATURATION = 0.08f

        /** Above this average lightness (and neutral) the artwork is genuinely light: a light surface. */
        private const val LIGHT_ARTWORK = 0.8f

        /** The palette of artwork with these [pixels] (ARGB, a small sample is plenty). */
        fun fromPixels(pixels: IntArray): LyricsPalette {
            // The artwork's strongest colour: pixels vote for their hue with their colourfulness
            // (saturation that is visible, i.e. not near black or white), the winning hue band and
            // its neighbours set the hue. Averaging every hue instead lets competing colours cancel
            // out into grey. Lightness is the plain average.
            val votes = DoubleArray(HUE_BINS)
            val xs = DoubleArray(HUE_BINS)
            val ys = DoubleArray(HUE_BINS)
            var colourfulness = 0.0
            var lightness = 0.0
            var counted = 0
            for (pixel in pixels) {
                if ((pixel ushr 24) < OPAQUE) continue
                val hsl = hsl(pixel)
                val chroma = hsl[1] * (1f - abs(hsl[2] * 2f - 1f))
                val bin = (hsl[0] / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1)
                val angle = Math.toRadians(hsl[0].toDouble())
                votes[bin] += chroma
                xs[bin] += cos(angle) * chroma
                ys[bin] += sin(angle) * chroma
                colourfulness += chroma
                lightness += hsl[2]
                counted++
            }
            if (counted == 0) return FALLBACK
            val best = votes.indices.maxBy { votes[it] }
            val band = listOf(best - 1, best, best + 1).map { Math.floorMod(it, HUE_BINS) }
            val x = band.sumOf { xs[it] }
            val y = band.sumOf { ys[it] }
            val bandVotes = band.sumOf { votes[it] }
            val hue = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
            // Neutral when the artwork as a whole has hardly any colour; otherwise as colourful as its strongest colour.
            val overall = colourfulness / counted
            val saturation = if (overall < NEUTRAL_COLOURFULNESS) 0f else (bandVotes / bandPixels(pixels, band)).toFloat()
            return from(hue, saturation, (lightness / counted).toFloat())
        }

        /** How many opaque pixels fall in the hue [band] (for the band's average colourfulness). */
        private fun bandPixels(
            pixels: IntArray,
            band: List<Int>,
        ): Double {
            val n =
                pixels.count { pixel ->
                    val hsl = hsl(pixel)
                    (pixel ushr 24) >= OPAQUE && hsl[1] > 0f && (hsl[0] / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1) in band
                }
            return n.coerceAtLeast(1).toDouble()
        }

        private const val HUE_BINS = 24
        private const val NEUTRAL_COLOURFULNESS = 0.05

        internal fun from(
            hue: Float,
            saturation: Float,
            artworkLightness: Float,
        ): LyricsPalette {
            val neutral = saturation < NEUTRAL_SATURATION
            val surfaceSaturation =
                if (neutral) 0f else (saturation * SATURATION_GAIN).coerceIn(MIN_SURFACE_SATURATION, MAX_SURFACE_SATURATION)
            val surfaceLightness =
                when {
                    neutral && artworkLightness > LIGHT_ARTWORK -> LIGHT_SURFACE
                    neutral -> NEUTRAL_SURFACE + NEUTRAL_LIFT * artworkLightness
                    else -> (DARK_SURFACE + DARK_LIFT * artworkLightness).coerceAtMost(MAX_DARK_SURFACE)
                }
            val content = readableOn(hslToArgb(hue, surfaceSaturation, surfaceLightness))
            val lightText = content == LIGHT_TEXT
            // Text must read on every part of the gradient: a stop too close to the text steps away from it.
            val surface = readableStop(hue, surfaceSaturation, surfaceLightness, content, lightText)
            val gradient =
                listOf(
                    readableStop(
                        hue - HUE_TURN_UP,
                        surfaceSaturation * UPPER_SATURATION,
                        (surfaceLightness + LIGHTER).coerceAtMost(MAX_STOP),
                        content,
                        lightText,
                    ),
                    surface,
                    readableStop(
                        hue + HUE_TURN_DOWN,
                        surfaceSaturation * LOWER_SATURATION,
                        (surfaceLightness - DEEPER).coerceAtLeast(MIN_STOP),
                        content,
                        lightText,
                    ),
                )
            val weakest = gradient.minBy { contrast(content, it) }
            val dark = relativeLuminance(weakest) < HALF
            val secondary = blendUntil(content, weakest, alpha = SECONDARY_ALPHA, minimumContrast = SECONDARY_CONTRAST)
            val accent =
                if (neutral) {
                    content
                } else {
                    accentOn(weakest, hue, dark)
                }
            return LyricsPalette(surface, gradient, content, secondary, accent)
        }

        private const val OPAQUE = 128

        /** The gradient: a lighter tone turned against the hue above, a deeper one turned with it below. */
        private const val HUE_TURN_UP = 12f
        private const val HUE_TURN_DOWN = 26f
        private const val UPPER_SATURATION = 0.85f
        private const val LOWER_SATURATION = 0.75f
        private const val LIGHTER = 0.06f
        private const val DEEPER = 0.09f
        private const val MAX_STOP = 0.94f
        private const val MIN_STOP = 0.07f
        private const val HALF = 0.18
        private const val LIGHT_SURFACE = 0.86f
        private const val NEUTRAL_SURFACE = 0.16f
        private const val NEUTRAL_LIFT = 0.08f
        private const val DARK_SURFACE = 0.2f
        private const val DARK_LIFT = 0.12f
        private const val MAX_DARK_SURFACE = 0.32f
        private const val SATURATION_GAIN = 1.6f
        private const val MIN_SURFACE_SATURATION = 0.3f
        private const val MAX_SURFACE_SATURATION = 0.55f
        private const val SECONDARY_ALPHA = 0.66f
        private const val SECONDARY_CONTRAST = 3.0
        const val TEXT_CONTRAST = 4.5
        private const val LIGHT_TEXT = 0xFFF7F3F2.toInt()
        private const val DARK_TEXT = 0xFF17141A.toInt()

        /** Light or dark text, whichever contrasts more with [surface]. */
        fun readableOn(surface: Int): Int = if (contrast(LIGHT_TEXT, surface) >= contrast(DARK_TEXT, surface)) LIGHT_TEXT else DARK_TEXT

        /** The colour (h, s, l), darkened under light text / lightened under dark text until [content] reads on it. */
        private fun readableStop(
            hue: Float,
            saturation: Float,
            lightness: Float,
            content: Int,
            lightText: Boolean,
        ): Int {
            var l = lightness
            var stop = hslToArgb(hue, saturation, l)
            while (contrast(content, stop) < TEXT_CONTRAST && l in MIN_STOP..MAX_STOP) {
                l += if (lightText) -STOP_STEP else STOP_STEP
                stop = hslToArgb(hue, saturation, l.coerceIn(0f, 1f))
            }
            return stop
        }

        private const val STOP_STEP = 0.02f

        /** A vivid tint of [hue] that still reads at [TEXT_CONTRAST] on [surface]; the plain text colour if none does. */
        private fun accentOn(
            surface: Int,
            hue: Float,
            dark: Boolean,
        ): Int {
            var l = if (dark) ACCENT_LIGHT_START else ACCENT_DARK_START
            repeat(ACCENT_STEPS) {
                val candidate = hslToArgb(hue, ACCENT_SATURATION, l)
                if (contrast(candidate, surface) >= TEXT_CONTRAST) return candidate
                l += if (dark) ACCENT_STEP else -ACCENT_STEP
            }
            return readableOn(surface)
        }

        private const val ACCENT_LIGHT_START = 0.74f
        private const val ACCENT_DARK_START = 0.32f
        private const val ACCENT_STEP = 0.04f
        private const val ACCENT_STEPS = 6
        private const val ACCENT_SATURATION = 0.7f

        /** [color] faded towards [surface] by [alpha], stepped back towards [color] until it reaches [minimumContrast]. */
        private fun blendUntil(
            color: Int,
            surface: Int,
            alpha: Float,
            minimumContrast: Double,
        ): Int {
            var a = alpha
            while (a < 1f) {
                val blended = blend(color, surface, a)
                if (contrast(blended, surface) >= minimumContrast) return blended
                a += BLEND_STEP
            }
            return color
        }

        private const val BLEND_STEP = 0.05f
    }
}

/** WCAG contrast ratio of two opaque colours (1..21). */
fun contrast(
    a: Int,
    b: Int,
): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + CONTRAST_OFFSET) / (min(la, lb) + CONTRAST_OFFSET)
}

private const val CONTRAST_OFFSET = 0.05

/** WCAG relative luminance of an ARGB colour. */
internal fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and BYTE) / BYTE_F
        return if (c <= SRGB_KNEE) c / SRGB_LINEAR else ((c + SRGB_A) / (1 + SRGB_A)).pow(SRGB_GAMMA)
    }
    return R_WEIGHT * channel(RED_SHIFT) + G_WEIGHT * channel(GREEN_SHIFT) + B_WEIGHT * channel(0)
}

private const val BYTE = 0xFF
private const val BYTE_F = 255.0
private const val SRGB_KNEE = 0.03928
private const val SRGB_LINEAR = 12.92
private const val SRGB_A = 0.055
private const val SRGB_GAMMA = 2.4
private const val R_WEIGHT = 0.2126
private const val G_WEIGHT = 0.7152
private const val B_WEIGHT = 0.0722
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

private fun blend(
    top: Int,
    bottom: Int,
    alpha: Float,
): Int {
    fun mix(shift: Int) = ((((top shr shift) and BYTE) * alpha + ((bottom shr shift) and BYTE) * (1 - alpha)).toInt() and BYTE) shl shift
    return (BYTE shl ALPHA_SHIFT) or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(0)
}

private const val ALPHA_SHIFT = 24

/** Hue (degrees), saturation and lightness (0..1) of an ARGB colour. */
private fun hsl(argb: Int): FloatArray {
    val r = ((argb shr RED_SHIFT) and BYTE) / BYTE_F.toFloat()
    val g = ((argb shr GREEN_SHIFT) and BYTE) / BYTE_F.toFloat()
    val b = (argb and BYTE) / BYTE_F.toFloat()
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    if (hi == lo) return floatArrayOf(0f, 0f, l)
    val d = hi - lo
    val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
    val h =
        when (hi) {
            r -> ((g - b) / d + (if (g < b) SIX else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + FOUR)
        } * SIXTY
    return floatArrayOf(h, s, l)
}

private const val SIX = 6f
private const val FOUR = 4f
private const val SIXTY = 60f

internal fun hslToArgb(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Int {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = ((hue % 360f) + 360f) % 360f / SIXTY
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) =
        when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < FOUR -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    val m = lightness - c / 2f

    fun byte(v: Float) = ((v + m).coerceIn(0f, 1f) * BYTE_F).toInt()
    return (BYTE shl ALPHA_SHIFT) or (byte(r) shl RED_SHIFT) or (byte(g) shl GREEN_SHIFT) or byte(b)
}

/** Derived palettes by artwork identity: each artwork is sampled once. */
private object LyricsPaletteCache {
    private val palettes = LruCache<String, LyricsPalette>(PALETTE_CACHE_ENTRIES)

    fun get(key: String): LyricsPalette? = palettes.get(key)

    fun put(
        key: String,
        palette: LyricsPalette,
    ) {
        palettes.put(key, palette)
    }
}

private const val PALETTE_CACHE_ENTRIES = 64
private const val SAMPLE_PX = 32

/** The animated colours of the lyrics surface for the playing song. */
@Immutable
data class LyricsColors(
    val surface: Color,
    /** The opaque gradient (top to bottom) for surfaces when the Glass Effect is off. */
    val gradient: List<Color>,
    val content: Color,
    val secondary: Color,
    val accent: Color,
)

/**
 * The lyrics palette of [song]'s artwork, cached by artwork identity and sampled small off the main
 * thread (once per artwork, never per recomposition or position update). While the next song's
 * artwork loads the previous palette stays; when it arrives the colours cross-fade. A late result
 * for a song no longer playing is dropped (the load is keyed to the song).
 */
@Composable
fun rememberLyricsColors(song: Song?): LyricsColors {
    val context = LocalContext.current
    val key = song?.let { it.artworkUri ?: it.uri }
    val palette by produceState(initialValue = remember { key?.let(LyricsPaletteCache::get) ?: LyricsPalette.FALLBACK }, key) {
        if (key == null) {
            value = LyricsPalette.FALLBACK
            return@produceState
        }
        LyricsPaletteCache.get(key)?.let {
            value = it
            return@produceState
        }
        val derived =
            withContext(Dispatchers.Default) {
                val bitmap = ArtworkLoader.load(context, song.uri, song.artworkUri, SAMPLE_PX)
                bitmap?.let { paletteOf(it) } ?: LyricsPalette.FALLBACK
            }
        LyricsPaletteCache.put(key, derived)
        value = derived
    }
    val spec = tween<Color>(Motion.HERO, easing = Motion.Standard)
    val surface by animateColorAsState(Color(palette.surface), spec, label = "lyricsSurface")
    val top by animateColorAsState(Color(palette.gradient[0]), spec, label = "gradientTop")
    val middle by animateColorAsState(Color(palette.gradient[1]), spec, label = "gradientMiddle")
    val bottom by animateColorAsState(Color(palette.gradient[2]), spec, label = "gradientBottom")
    val content by animateColorAsState(Color(palette.content), spec, label = "lyricsContent")
    val secondary by animateColorAsState(Color(palette.secondary), spec, label = "lyricsSecondary")
    val accent by animateColorAsState(Color(palette.accent), spec, label = "lyricsAccent")
    return LyricsColors(surface, listOf(top, middle, bottom), content, secondary, accent)
}

private fun paletteOf(bitmap: Bitmap): LyricsPalette {
    val large = bitmap.width > SAMPLE_PX || bitmap.height > SAMPLE_PX
    val sample = if (large) Bitmap.createScaledBitmap(bitmap, SAMPLE_PX, SAMPLE_PX, true) else bitmap
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    return LyricsPalette.fromPixels(pixels)
}

/**
 * A Now Playing surface (carousel card, lyrics panel) in the artwork's colours, following the
 * app's Glass Effect setting:
 * - on: the app's own glass ([glassPanel], frosted over the album backdrop, with its edge light and
 *   rim), tinted with the artwork colour densely enough that the text on it always reads;
 * - off: an opaque gradient of the artwork palette, no blur or reflections.
 * Clip the content to [shape] after this.
 */
@Composable
internal fun Modifier.nowPlayingSurface(
    colors: LyricsColors,
    shape: Shape,
): Modifier =
    if (LocalGlassTokens.current != null) {
        val tint = if (colors.surface.luminance() > LIGHT_SURFACE_LUMINANCE) LIGHT_GLASS_TINT else DARK_GLASS_TINT
        glassPanel(shape, level = GlassLevel.Strong, fill = colors.surface.copy(alpha = tint))
    } else {
        clip(shape).background(Brush.verticalGradient(colors.gradient))
    }

/** Glass tint over the frost: dense enough that the backdrop never overpowers the text. */
private const val DARK_GLASS_TINT = 0.74f
private const val LIGHT_GLASS_TINT = 0.86f
private const val LIGHT_SURFACE_LUMINANCE = 0.4f
