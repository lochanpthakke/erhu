package com.erhu.erhu.feature.library

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.GlassLevel
import com.erhu.erhu.core.designsystem.theme.GlassTokens
import com.erhu.erhu.core.designsystem.theme.LocalGlassBackdrop
import com.erhu.erhu.core.designsystem.theme.LocalGlassTokens
import com.erhu.erhu.core.designsystem.theme.glassPanel

/*
 * The Now Playing carousel card's material. Both Glass Effect modes share the same structure and
 * details (rim, upper edge light, lower shade, the soft step between artwork and text); only the
 * body differs:
 * - glass: the app's frosted glass over the album backdrop, tinted with the card gradient — thin
 *   around the artwork so the frost shows, denser behind the text so it always reads;
 * - no glass: the card gradient, opaque.
 */

/** Glass tint around the artwork: the frost and the backdrop's colours show through. */
private const val GLASS_RING_ALPHA = 0.4f

/** Behind the text the tint is at least this dense, more if the backdrop could get too close to the text. */
private const val GLASS_TEXT_MIN_ALPHA = 0.62f

/** Without a blurred backdrop (before Android 12) the glass must hide what is behind it. */
private const val UNFROSTED_ALPHA = 0.9f

private const val ALPHA_STEP = 0.02f

/** Where the text tint has fully arrived, below the top of the text area. */
private val TEXT_TINT_FADE = 18.dp

/** The soft shade the artwork casts on the text area. */
private val SEPARATION_DEPTH = 14.dp
private const val SEPARATION_ALPHA = 0.16f

/** Restrained light along the upper edge. */
private val UPPER_LIGHT_DEPTH = 22.dp
private const val UPPER_LIGHT_ALPHA = 0.1f

/** The lower edge darkens a little, for depth. */
private const val LOWER_SHADE_START = 0.82f
private const val LOWER_SHADE_ALPHA = 0.16f

/** Rim: album-tinted, brightest along the top, fading out, slightly dark along the bottom. */
private val RIM_WIDTH = 1.dp
private const val RIM_TOP_ALPHA = 0.5f
private const val RIM_MIDDLE = 0.5f
private const val RIM_MIDDLE_ALPHA = 0.12f
private const val RIM_BOTTOM_SHADE = 0.22f

/** Under dark text (light surfaces) shading is kept faint so it never eats into the contrast. */
private const val DARK_TEXT_SHADE_SCALE = 0.35f

/**
 * Draws the card's material in [colors], clipped to [shape]; [infoTop] is where the title area
 * begins (below the artwork). Clip the content to [shape] after this.
 *
 * Three draws per card on top of the glass panel (the card is redrawn on every frame of the
 * player's open / close motion, three cards at a time): the diagonal body, ONE vertical overlay
 * that already contains every horizontal band (text tint, upper light, the step under the
 * artwork, lower shade) composited together, and the rim.
 */
@Composable
internal fun Modifier.carouselCardSurface(
    colors: LyricsColors,
    shape: Shape,
    infoTop: Dp,
): Modifier {
    val glass = LocalGlassTokens.current
    val lightText = colors.content.luminance() > HALF_LUMINANCE
    if (glass == null) {
        return clip(shape).cardLayers(shape, colors, bodyAlpha = 1f, textAlpha = 1f, infoTop = infoTop, lightText = lightText)
    }
    val frosted = LocalGlassBackdrop.current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val ring = if (frosted) GLASS_RING_ALPHA else UNFROSTED_ALPHA
    val textAlpha =
        remember(glass, colors.content, colors.cardGradient, ring, frosted) {
            glassTextAlpha(glass, colors, ring, frosted)
        }
    // The panel brings the frost, the floating shadow and the material's own light; the tint is drawn here.
    return glassPanel(shape, level = GlassLevel.Strong, outlined = false, fill = Color.White.copy(alpha = 0f))
        .cardLayers(shape, colors, bodyAlpha = ring, textAlpha = textAlpha, infoTop = infoTop, lightText = lightText)
}

/**
 * The body (the card gradient at [bodyAlpha], diagonal: the light falls from the top-start), the
 * merged vertical overlay (behind the text the body is brought up to [textAlpha]) and the rim.
 */
@Suppress("LongParameterList")
private fun Modifier.cardLayers(
    shape: Shape,
    colors: LyricsColors,
    bodyAlpha: Float,
    textAlpha: Float,
    infoTop: Dp,
    lightText: Boolean,
): Modifier =
    drawWithCache {
        val height = size.height
        val body =
            Brush.linearGradient(
                colors.cardGradient.map { it.copy(alpha = it.alpha * bodyAlpha) },
                start = Offset.Zero,
                end = Offset(size.width, height),
            )
        val shadeScale = if (lightText) 1f else DARK_TEXT_SHADE_SCALE
        val top = infoTop.toPx()
        val fade = TEXT_TINT_FADE.toPx() / 2
        val shade = Color.Black.copy(alpha = SEPARATION_ALPHA * shadeScale)
        val lower = Color.Black.copy(alpha = LOWER_SHADE_ALPHA * shadeScale)
        val light = Color.White.copy(alpha = UPPER_LIGHT_ALPHA)
        // The extra tint that brings the body behind the text up to textAlpha (none when opaque).
        val extra = if (bodyAlpha >= 1f) 0f else 1f - (1f - textAlpha) / (1f - bodyAlpha)
        val textMiddle = colors.cardGradient[1].copy(alpha = extra.coerceAtLeast(0f))
        val textEnd = colors.cardGradient[2].copy(alpha = extra.coerceAtLeast(0f))
        val overlay =
            flatten(
                height,
                // Bottom to top, in drawing order.
                VerticalBand(top - fade to textMiddle.copy(alpha = 0f), top + fade to textMiddle, height to textEnd),
                VerticalBand(0f to light, UPPER_LIGHT_DEPTH.toPx() to light.copy(alpha = 0f)),
                VerticalBand(top - 1f to shade.copy(alpha = 0f), top to shade, top + SEPARATION_DEPTH.toPx() to shade.copy(alpha = 0f)),
                VerticalBand(height * LOWER_SHADE_START to lower.copy(alpha = 0f), height to lower),
            )
        val rimBrush =
            Brush.verticalGradient(
                0f to colors.rim.copy(alpha = RIM_TOP_ALPHA),
                RIM_MIDDLE to colors.rim.copy(alpha = RIM_MIDDLE_ALPHA),
                1f to Color.Black.copy(alpha = RIM_BOTTOM_SHADE * shadeScale),
            )
        val outline = shape.createOutline(size, layoutDirection, this)
        // The stroke is centred on the outline and the card is clipped: double it.
        val rimStroke = Stroke(width = RIM_WIDTH.toPx() * 2)
        onDrawBehind {
            drawRect(body)
            drawRect(overlay)
            drawOutline(outline, rimBrush, style = rimStroke)
        }
    }

/** A horizontal band of colour that varies only with y: [stops] (y in px, ascending), linear between, held outside. */
internal class VerticalBand(
    vararg val stops: Pair<Float, Color>,
) {
    fun at(y: Float): Color {
        // The first stop at or below y; before the first stop and after the last the colour holds.
        val next = stops.indexOfFirst { y <= it.first }
        return when (next) {
            -1 -> stops.last().second
            0 -> stops.first().second
            else -> {
                val (y0, c0) = stops[next - 1]
                val (y1, c1) = stops[next]
                lerp(c0, c1, if (y1 > y0) (y - y0) / (y1 - y0) else 1f)
            }
        }
    }
}

/**
 * [bands] (in drawing order) composited into one vertical gradient: evaluated and blended at
 * every band's stop, so one draw replaces one per band. Built once per card size and colours.
 */
@Suppress("SpreadOperator") // verticalGradient takes its stops as varargs; this runs per size, not per frame.
internal fun flatten(
    height: Float,
    vararg bands: VerticalBand,
): Brush {
    val ys =
        (bands.flatMap { band -> band.stops.map { it.first } } + listOf(0f, height))
            .map { it.coerceIn(0f, height) }
            .distinct()
            .sorted()
    val stops =
        ys
            .map { y -> y / height to bands.fold(Color.Transparent) { below, band -> band.at(y).compositeOver(below) } }
            .toTypedArray()
    return Brush.verticalGradient(*stops)
}

private const val HALF_LUMINANCE = 0.5f

/**
 * How dense the glass tint must be behind the text so [LyricsColors.content] keeps
 * [LyricsPalette.TEXT_CONTRAST] on every stop of the card gradient, even over the brightest (for
 * light text) or darkest (for dark text) backdrop the glass could show: the artwork backdrop's
 * scrim and the frost's tone applied to white / black.
 */
private fun glassTextAlpha(
    glass: GlassTokens,
    colors: LyricsColors,
    ring: Float,
    frosted: Boolean,
): Float {
    val lightText = colors.content.luminance() > HALF_LUMINANCE
    val extreme = if (lightText) Color.White else Color.Black
    val worst =
        if (frosted) {
            val seen = Color(glass.artworkScrim).compositeOver(extreme)
            val scale = 1f - glass.frostDim - glass.frostLift

            fun tone(channel: Float) = (channel * scale + glass.frostLift).coerceIn(0f, 1f)
            Color(tone(seen.red), tone(seen.green), tone(seen.blue))
        } else {
            extreme
        }
    val content = colors.content.toArgb()
    var alpha = maxOf(ring, GLASS_TEXT_MIN_ALPHA)

    fun readable(a: Float) =
        colors.cardGradient.all { stop ->
            contrast(content, stop.copy(alpha = a).compositeOver(worst).toArgb()) >= LyricsPalette.TEXT_CONTRAST
        }
    while (alpha < 1f && !readable(alpha)) alpha += ALPHA_STEP
    return alpha.coerceAtMost(1f)
}
