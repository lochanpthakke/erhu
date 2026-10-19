package com.lochanpthakke.empee3.core.designsystem.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.lochanpthakke.empee3.core.common.perf.traced
import kotlin.math.ceil

/*
 * Environment refraction (Android 13+), the glass material of the floating surfaces. The surface's backdrop is
 * drawn WITHOUT the shape clip, with a margin around it, so content from around
 * the glass is actually rendered. The shader then draws the glass itself: the
 * rounded shape (anti-aliased) and, inside it, a thick lens that pulls content
 * from OUTSIDE the outline into its rim.
 *
 *   content ─▶ light blur ─▶ tone ─▶ environment lens (shape + refraction + dispersion + rim)
 */

/**
 * - shape: signed distance to the real rounded outline; outside it the pixel is
 *   transparent, the outline itself is anti-aliased over one pixel;
 * - optical zone: a mask m rising smoothly (smoothstep) from 0 at its inner edge to
 *   1 at the outline, over [band] (about a third of the surface's height);
 * - refraction: samples OUTWARD along the surface normal (the gradient of the
 *   distance field) by strength * m^1.8 (with more on the curved ends), so the
 *   content around the glass is pulled into its rim and compressed towards the edge,
 *   like through thick curved glass; the centre is untouched;
 * - dispersion: red and blue sampled either side of the refracted point along the
 *   same normal, growing with the optical depth, so the spectral colours come from
 *   the content itself;
 * - rim: a broad soft highlight along the outline, brighter where it faces a
 *   top-start light, and a slight dark falloff on the opposite side.
 *
 * Every sample is clamped to the rendered layer, and composited over the app's
 * environment colour (what is really behind the content), so nothing reads as empty.
 */
private const val ENVIRONMENT_SHADER =
    """
    uniform shader content;
    uniform float2 origin;
    uniform float2 size;
    uniform float2 layer;
    uniform float4 radii;
    uniform float band;
    uniform float strength;
    uniform float capBoost;
    uniform float dispersion;
    uniform float highlight;
    uniform float rimWidth;
    uniform float shade;
    uniform float2 light;
    uniform float4 environment;

    float box(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    // Corner radius of the quadrant p lies in (y down): top-left, top-right, bottom-right, bottom-left.
    float cornerOf(float2 p, float2 b) {
        float r = p.x < 0.0 ? (p.y < 0.0 ? radii.x : radii.w) : (p.y < 0.0 ? radii.y : radii.z);
        return min(r, min(b.x, b.y));
    }

    float shape(float2 p, float2 b) {
        return box(p, b, cornerOf(p, b));
    }

    half4 world(float2 at) {
        half4 c = content.eval(clamp(at, float2(0.5), layer - float2(0.5)));
        return c + half4(environment) * (1.0 - c.a);
    }

    half4 main(float2 coord) {
        float2 b = size * 0.5;
        float2 p = coord - origin - b;
        float r = cornerOf(p, b);
        float d = box(p, b, r);
        float coverage = clamp(0.5 - d, 0.0, 1.0);
        if (coverage <= 0.0) return half4(0.0);
        if (d < -band) return world(coord) * half(coverage);

        float2 n = float2(
            shape(p + float2(1.0, 0.0), b) - shape(p - float2(1.0, 0.0), b),
            shape(p + float2(0.0, 1.0), b) - shape(p - float2(0.0, 1.0), b));
        n = n / max(length(n), 0.0001);

        float m = 1.0 - smoothstep(0.0, band, -d);
        float bend = pow(m, 1.8);
        float2 q = abs(p) - b + r;
        float cap = 1.0 + capBoost * smoothstep(-0.2 * r, 0.35 * r, min(q.x, q.y));

        // Refraction: the world around the glass is pulled into its rim.
        float2 at = coord + n * strength * cap * bend;
        float2 spread = n * dispersion * bend * cap;
        half4 c = world(at);
        c.r = world(at + spread).r;
        c.b = world(at - spread).b;

        // Rim: broad soft light along the outline, brighter towards the light; shade opposite.
        float facing = dot(n, light);
        float rim = 1.0 - smoothstep(0.0, rimWidth, -d);
        float glow = highlight * rim * rim * (0.35 + 0.65 * max(facing, 0.0));
        c.rgb = c.rgb + (half3(1.0) - c.rgb) * half(glow);
        c.rgb = c.rgb * half(1.0 - shade * rim * max(-facing, 0.0));
        return c * half(coverage);
    }
    """

/** Environment lens of one surface, in px of its unclipped backdrop layer. */
internal class EnvironmentLens(
    /** Where the surface starts in the layer (the margin). */
    val origin: Float,
    val size: Size,
    /** The whole layer: surface plus margin on every side. */
    val layerSize: Size,
    /** Corner radii: top-left, top-right, bottom-right, bottom-left (resolved for the layout direction). */
    val cornerRadii: FloatArray,
    val band: Float,
    val strength: Float,
    val capBoost: Float,
    val dispersion: Float,
    val highlight: Float,
    val rimWidth: Float,
    val shade: Float,
    /** The app's environment colour, under content that draws nothing. */
    val environment: Color,
)

/** The full optics chain of an environment-lens surface (Android 13+). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun environmentOptics(
    blurPx: Float,
    saturation: Float,
    dim: Float,
    lift: Float,
    lens: EnvironmentLens,
): androidx.compose.ui.graphics.RenderEffect {
    val shader =
        RuntimeShader(ENVIRONMENT_SHADER).apply {
            setFloatUniform("origin", lens.origin, lens.origin)
            setFloatUniform("size", lens.size.width, lens.size.height)
            setFloatUniform("layer", lens.layerSize.width, lens.layerSize.height)
            setFloatUniform("radii", lens.cornerRadii)
            setFloatUniform("band", lens.band)
            setFloatUniform("strength", lens.strength)
            setFloatUniform("capBoost", lens.capBoost)
            setFloatUniform("dispersion", lens.dispersion)
            setFloatUniform("highlight", lens.highlight)
            setFloatUniform("rimWidth", lens.rimWidth)
            setFloatUniform("shade", lens.shade)
            setFloatUniform("light", EDGE_LIGHT_X, EDGE_LIGHT_Y)
            setFloatUniform("environment", lens.environment.red, lens.environment.green, lens.environment.blue, 1f)
        }
    val lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
    return RenderEffect.createChainEffect(lensEffect, blurAndTone(blurPx, saturation, dim, lift)).asComposeRenderEffect()
}

/** Optical zone: this share of the surface's smaller side (a pill's height), at most [ZONE_MAX]. */
private const val ZONE_FRACTION = 0.3f
private val ZONE_MAX = 24.dp

/** How far the world is pulled in at the outline (the curved ends get [CAP_BOOST] more). */
private val MAX_SHIFT = 14.dp
private const val CAP_BOOST = 0.6f

/** Red / blue separation around the refracted sample, at full optical depth. */
private val DISPERSION = 1.2.dp

/** Rim light: the theme's edge light times this, over this share of the optical zone. */
private const val RIM_SCALE = 2.3f
private const val RIM_WIDTH_FRACTION = 0.45f

/** Dark falloff on the side facing away from the light. */
private const val SHADE = 0.15f

/**
 * Draws the [backdrop] as seen through an environment lens, BEFORE (outside) the
 * surface's shape clip, so the margin around the surface is rendered and the lens can
 * pull it in. The shader draws the rounded shape itself.
 *
 * [strength] scales the optics against the mini player (1): the bend, the colour
 * separation, the rim light and the shade. The mechanism is the same for every surface.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun Modifier.environmentFrost(
    shape: Shape,
    glass: GlassTokens,
    backdrop: GlassBackdrop,
    strength: Float,
    surfaceOffset: () -> Offset,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val blurPx = glass.frostRadiusDp.dp.toPx()
        val shift = MAX_SHIFT.toPx() * strength
        val dispersion = DISPERSION.toPx() * strength
        val band = minOf(minOf(size.width, size.height) * ZONE_FRACTION, ZONE_MAX.toPx())
        // Room for the largest pull (with the cap boost and dispersion) plus the blur's reach.
        val margin = ceil((shift + dispersion) * (1f + CAP_BOOST) + blurPx * 2 + 2f).toInt()
        val layerSize = IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)
        val lens =
            EnvironmentLens(
                origin = margin.toFloat(),
                size = size,
                layerSize = Size(layerSize.width.toFloat(), layerSize.height.toFloat()),
                cornerRadii = outline.cornerRadii(),
                band = band,
                strength = shift,
                capBoost = CAP_BOOST,
                dispersion = dispersion,
                highlight = (glass.edgeLight * RIM_SCALE * strength).coerceAtMost(1f),
                rimWidth = band * RIM_WIDTH_FRACTION,
                shade = SHADE * strength,
                environment = Color(glass.backgroundGradient.first()),
            )
        val layer =
            obtainGraphicsLayer().apply {
                renderEffect = environmentOptics(blurPx, glass.frostSaturation, glass.frostDim, glass.frostLift, lens)
            }
        // Whether layer holds a rendering this cache can show again while the backdrop is frozen.
        var rendered = false
        onDrawBehind {
            // Frozen: the last rendering, without reading the position (no redraw, no lens pass as it moves).
            if (backdrop.frozen && rendered) {
                drawLayer(layer)
                return@onDrawBehind
            }
            if (!backdrop.hasContent || backdrop.isRecording) return@onDrawBehind
            // Read so this surface redraws whenever the source content changes.
            backdrop.contentVersion
            val offset = surfaceOffset() - backdrop.sourceOffset
            // Entirely outside its source (e.g. a closed drawer, parked off screen): nothing
            // of it is visible, so no re-recording and no lens pass while the content changes.
            val source = backdrop.layer.size
            val visible = Rect(offset, size).overlaps(Rect(Offset.Zero, source.toSize()))
            if (!visible) return@onDrawBehind
            layer.topLeft = IntOffset(-margin, -margin)
            traced(backdrop.lensSection) {
                layer.record(layerSize) {
                    translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
                }
            }
            rendered = true
            drawLayer(layer)
        }
    }

/** The backdrop a floating surface draws through the environment lens (Android 13+), if any. */
internal fun environmentSource(
    frost: GlassBackdrop?,
    environmentLens: Boolean,
    bar: Boolean,
): GlassBackdrop? = frost?.takeIf { environmentLens && !bar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

/** The environment-lens backdrop, drawn outside the shape clip (see [environmentFrost]). */
internal fun Modifier.environmentLayer(
    environment: GlassBackdrop?,
    shape: Shape,
    glass: GlassTokens,
    optics: Float,
    surfaceOffset: () -> Offset,
): Modifier =
    if (environment != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        then(Modifier.environmentFrost(shape, glass, environment, optics, surfaceOffset))
    } else {
        this
    }

/** Corner radii of an outline: top-left, top-right, bottom-right, bottom-left. */
private fun Outline.cornerRadii(): FloatArray =
    when (this) {
        is Outline.Rounded ->
            floatArrayOf(
                roundRect.topLeftCornerRadius.x,
                roundRect.topRightCornerRadius.x,
                roundRect.bottomRightCornerRadius.x,
                roundRect.bottomLeftCornerRadius.x,
            )
        is Outline.Rectangle -> FloatArray(CORNERS)
        is Outline.Generic -> FloatArray(CORNERS) { minOf(bounds.width, bounds.height) / 2f }
    }

private const val CORNERS = 4
