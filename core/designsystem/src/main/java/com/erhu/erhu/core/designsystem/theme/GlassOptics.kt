package com.erhu.erhu.core.designsystem.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposeRenderEffect

/*
 * The optics of the glass material: what happens to the content seen THROUGH a
 * surface, as one RenderEffect chain on the surface's own small backdrop layer.
 *
 *   content ─▶ light blur ─▶ tone (vibrancy + legibility) ─▶ edge optics (Android 13+)
 *
 * The centre of a surface stays clear: the blur is a light softening, not a
 * frost, so artwork underneath stays recognisable, and legibility comes from
 * compressing the content's brightness range (towards dark in dark themes,
 * towards light in light themes) instead of covering it with paint.
 *
 * The edge optics come last, so they act on the softened, toned image: their
 * colour fringe stays soft, and their edge light is not dimmed by the tone.
 */

/**
 * Edge optics of a curved glass surface, all driven by one normalised edge mask:
 * m = 0 at the inner edge of the optical rim, rising smoothly (smoothstep) to 1 at
 * the outline, measured on the surface's real rounded shape (a rounded-box signed
 * distance). The centre of the surface is untouched.
 *
 * - refraction (the primary effect): the content is sampled from further IN along
 *   the surface normal (the gradient of the distance field), by strength * m^2.5:
 *   almost nothing near the inner edge of the rim, accelerating towards the outline,
 *   so the content is drawn out and magnified towards the edge like through the
 *   thick, curved rim of a lens. The curved ends bend more than the straight sides
 *   (a smooth boost where both axes curve). It never samples beyond the outline:
 *   the effect's input exists only for the visible area;
 * - dispersion: red and blue are sampled either side of the REFRACTED point along
 *   the same normal, scaled by the same mask (m^3), so the spectral colours live
 *   inside the bent content instead of forming a border;
 * - thickness: a soft light along the rim (stronger where it faces a top-start
 *   light) and a slight dark falloff on the side facing away from it.
 *
 * Pixels outside the rim return the content untouched: no extra work in the centre.
 */
private const val EDGE_OPTICS_SHADER =
    """
    uniform shader content;
    uniform float2 origin;
    uniform float2 size;
    uniform float radius;
    uniform float band;
    uniform float strength;
    uniform float capBoost;
    uniform float dispersion;
    uniform float highlight;
    uniform float shade;
    uniform float2 light;

    float box(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 coord) {
        float2 b = size * 0.5;
        float2 p = coord - origin - b;
        float r = min(radius, min(b.x, b.y));
        float d = box(p, b, r);
        if (d > 0.0 || d < -band) return content.eval(coord);

        // Surface normal: gradient of the distance field (follows the curve everywhere).
        float2 n = float2(
            box(p + float2(1.0, 0.0), b, r) - box(p - float2(1.0, 0.0), b, r),
            box(p + float2(0.0, 1.0), b, r) - box(p - float2(0.0, 1.0), b, r));
        n = n / max(length(n), 0.0001);

        // Normalised edge mask on the real rounded shape (smooth, no steps).
        float m = 1.0 - smoothstep(0.0, band, -d);
        float m2 = m * m;
        float m3 = m2 * m;
        float bend = m2 * sqrt(m);

        // Curved ends bend more than straight sides; blended, never a step.
        float2 q = abs(p) - b + r;
        float cap = 1.0 + capBoost * smoothstep(-0.2 * r, 0.35 * r, min(q.x, q.y));

        // Refraction, sampled from INSIDE the surface only.
        float2 at = coord - n * strength * cap * bend;
        half4 c = content.eval(at);
        float2 spread = n * dispersion * m3;
        c.r = content.eval(at + spread).r;
        c.b = content.eval(at - spread).b;

        // Thickness: light on the rim facing the light, a slight falloff opposite.
        float facing = dot(n, light);
        float glow = highlight * m3 * (0.45 + 0.55 * max(facing, 0.0));
        c.rgb = c.rgb + (half3(1.0) - c.rgb) * half(glow);
        c.rgb = c.rgb * half(1.0 - shade * m3 * max(-facing, 0.0));
        return c;
    }
    """

/** Edge optics of one surface, in px of its backdrop layer. */
internal class GlassLens(
    val origin: Float,
    val size: Size,
    val cornerRadius: Float,
    /** Width of the edge zone the optics act in. */
    val band: Float,
    /** How far the content is shifted at the very edge (refraction). */
    val strength: Float,
    /** Extra refraction where the outline curves (pill ends, corners): 0.5 = 50% more. */
    val capBoost: Float,
    /** Red / blue separation at the very edge (chromatic aberration). */
    val dispersion: Float,
    /** Strength of the soft edge light (0..1 of the way to white). */
    val highlight: Float,
    /** Dark falloff on the rim facing away from the light (0..1 of the way to black). */
    val shade: Float,
)

/** Corner radius of a surface's outline, for the optics (pills: half the height). */
internal fun Outline.cornerRadius(): Float =
    when (this) {
        is Outline.Rounded -> roundRect.topLeftCornerRadius.x
        is Outline.Rectangle -> 0f
        is Outline.Generic -> minOf(bounds.width, bounds.height) / 2f
    }

/**
 * The optics chain for one surface. [lens] null (bars), or Android 12 (edge optics need
 * AGSL, Android 13+): blur and tone only.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun glassOptics(
    blurPx: Float,
    saturation: Float,
    dim: Float,
    lift: Float,
    lens: GlassLens?,
): androidx.compose.ui.graphics.RenderEffect {
    val toned = blurAndTone(blurPx, saturation, dim, lift)
    val optics =
        if (lens != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RenderEffect.createChainEffect(edgeOptics(lens), toned)
        } else {
            toned
        }
    return optics.asComposeRenderEffect()
}

/**
 * The light blur and the tone of what is seen through glass, shared by every optics
 * chain. [dim] / [lift] compress the content's brightness: rgb * (1 - dim - lift) + lift * 255.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun blurAndTone(
    blurPx: Float,
    saturation: Float,
    dim: Float,
    lift: Float,
): RenderEffect {
    val scale = 1f - dim - lift
    val offset = lift * CHANNEL_MAX
    val tone =
        ColorMatrix().apply {
            setSaturation(saturation)
            postConcat(ColorMatrix(brightnessMatrix(scale, offset)))
        }
    val toneEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(tone))
    // createChainEffect(outer, inner): inner runs first.
    return if (blurPx > 0f) {
        RenderEffect.createChainEffect(toneEffect, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
    } else {
        toneEffect
    }
}

/** rgb * [scale] + [offset], alpha unchanged. */
private fun brightnessMatrix(
    scale: Float,
    offset: Float,
): FloatArray =
    FloatArray(MATRIX_SIZE).apply {
        this[0] = scale
        this[4] = offset
        this[6] = scale
        this[9] = offset
        this[12] = scale
        this[14] = offset
        this[18] = 1f
    }

private const val MATRIX_SIZE = 20

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun edgeOptics(lens: GlassLens): RenderEffect {
    val shader =
        RuntimeShader(EDGE_OPTICS_SHADER).apply {
            setFloatUniform("origin", lens.origin, lens.origin)
            setFloatUniform("size", lens.size.width, lens.size.height)
            setFloatUniform("radius", lens.cornerRadius)
            setFloatUniform("band", lens.band)
            setFloatUniform("strength", lens.strength)
            setFloatUniform("capBoost", lens.capBoost)
            setFloatUniform("dispersion", lens.dispersion)
            setFloatUniform("highlight", lens.highlight)
            setFloatUniform("shade", lens.shade)
            setFloatUniform("light", EDGE_LIGHT_X, EDGE_LIGHT_Y)
        }
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

private const val CHANNEL_MAX = 255f

/** Direction the edge light comes from: top-start (unit vector, y down). */
internal const val EDGE_LIGHT_X = -0.447f
internal const val EDGE_LIGHT_Y = -0.894f
