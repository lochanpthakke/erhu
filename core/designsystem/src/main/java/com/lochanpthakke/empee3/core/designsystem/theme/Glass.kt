package com.lochanpthakke.empee3.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.common.perf.traced
import kotlin.math.ceil
import kotlin.math.max

/** Glass tokens of the active theme, or null for classic (non-glass) themes. */
val LocalGlassTokens = staticCompositionLocalOf<GlassTokens?> { null }

/** Design-system access to glass styling; every helper is a no-op for non-glass themes. */
object EmPee3Glass {
    val tokens: GlassTokens?
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTokens.current

    val isActive: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTokens.current != null

    /**
     * Container color for a component that is wrapped in [glassPanel]: transparent
     * under a glass theme (the panel draws the fill), [default] otherwise.
     */
    @Composable
    @ReadOnlyComposable
    fun containerColor(default: Color): Color = if (LocalGlassTokens.current != null) Color.Transparent else default

    /**
     * Fill for chrome that sits over the background (top bar, tab row): a light
     * material tint under a glass theme, so the background continues behind the
     * header instead of being replaced by an opaque block; [default] otherwise.
     */
    @Composable
    @ReadOnlyComposable
    fun chromeColor(default: Color): Color = LocalGlassTokens.current?.let { Color(it.panelFill) } ?: default

    /**
     * Colour of the environment behind content near the top of the screen: the
     * top of the glass background gradient under a glass theme, [default]
     * (normally the theme background) otherwise. Used for header scrims.
     */
    @Composable
    @ReadOnlyComposable
    fun backdropColor(default: Color): Color = LocalGlassTokens.current?.let { Color(it.backgroundGradient.first()) } ?: default
}

private const val SHEEN_ALPHA = 0.06f

/** Upper light: a narrow sheen along the top edge, fading out by this fraction of the height. */
private const val SPECULAR_END = 0.3f

/** Lower shade: where it starts (fraction of the height). */
private const val SHADE_START = 0.55f

/**
 * Lens edge: light catches the top-start edge and, faintly, the opposite
 * bottom-end edge, like the rim of a thick piece of glass. Fractions of the
 * highlight colour along the diagonal.
 */
private const val RIM_MID_FRACTION = 0.08f
private const val RIM_FAR_FRACTION = 0.35f

/** Soft corner highlight: radius as a share of the larger side, strength as a share of the specular colour. */
private const val CORNER_LIGHT_RADIUS = 0.9f
private const val CORNER_LIGHT_FRACTION = 0.6f

/** Bar edge (full-width top bars): a faint hairline along the bottom instead of a rim and shade. */
private const val BAR_HAIRLINE_FRACTION = 0.4f

/**
 * Internal material levels; not a user setting. Strength is expressed through
 * the material, not opacity: how much light and shade the surface carries and
 * whether it floats with a soft shadow. [frostedFillScale] scales the panel fill
 * when something blurred is underneath.
 */
enum class GlassLevel(
    internal val frostedFillScale: Float,
    internal val lightScale: Float,
    internal val shadeScale: Float,
    internal val floats: Boolean,
) {
    /** Content-level surfaces (settings cards): a hint of material, no depth. */
    Minimal(frostedFillScale = 0.3f, lightScale = 0.5f, shadeScale = 0.3f, floats = false),

    /** Top bars, search, selected indicators. */
    Medium(frostedFillScale = 0.3f, lightScale = 0.8f, shadeScale = 0.6f, floats = false),

    /** Mini player, library navigation, floating buttons, Now Playing controls, drawer. */
    Strong(frostedFillScale = 0.3f, lightScale = 1f, shadeScale = 1f, floats = true),
}

/**
 * The shared glass material. Layers, bottom to top:
 *
 * 0. (Strong) a soft floating shadow, drawn only outside the shape so it never
 *    darkens the glass itself;
 * 1. the [backdrop] seen THROUGH the surface (Android 12+ with a [GlassBackdrop];
 *    skipped otherwise), through the glass optics (GlassOptics.kt): edge refraction
 *    (Android 13+, not on bars), a light blur, and a tone that keeps the colours
 *    while compressing brightness for legible text. The content stays recognisable;
 *    the material is a thin tint on top, not a frost;
 * 2. a translucent fill in the theme's own panel colour (lighter when frosted),
 *    or [fill] when given (a solid surface that still wants the material's light);
 * 3. light: a soft upper band plus a corner highlight at the top-start;
 * 4. a shade on the lower part, for depth;
 * 5. the edge: a lens rim along the diagonal ([outlined] = false: a softer one),
 *    or for a full-width [bar] only a faint hairline along the bottom.
 *
 * Without a backdrop or below Android 12 it is the same material minus the
 * blur, with the panel fill at full strength. Pure draw operations plus, when
 * frosted, one small graphics layer per surface; content is not recomposed.
 *
 * [optics]: strength of the edge optics (refraction, colour fringe, edge light),
 * 1 for the primary floating surface (the mini player), [SECONDARY_OPTICS] otherwise.
 *
 * [environmentLens] (Android 13+, floating surfaces): the backdrop is drawn outside the
 * shape clip with a margin, and the lens pulls in the content around the surface
 * (GlassEnvironmentOptics.kt) instead of the inward-only edge optics; [optics] sets
 * its strength against the mini player (1).
 *
 * [windowFrosted]: the platform already blurs behind this surface (a dialog
 * window with cross-window blur), so no in-window frost is drawn.
 *
 * Returns the receiver unchanged when the Glass Effect is off.
 */
@Composable
@Suppress("LongParameterList")
fun Modifier.glassPanel(
    shape: Shape,
    strong: Boolean = false,
    elevated: Boolean = true,
    outlined: Boolean = true,
    level: GlassLevel = if (strong) GlassLevel.Strong else GlassLevel.Medium,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    windowFrosted: Boolean = false,
    bar: Boolean = false,
    fill: Color? = null,
    optics: Float = SECONDARY_OPTICS,
    environmentLens: Boolean = false,
): Modifier {
    val glass = LocalGlassTokens.current ?: return this
    val frost = backdrop?.takeIf { isBackdropBlurSupported && !windowFrosted }
    // Floating surfaces: the lens pulls in the world around the surface.
    val environment = environmentSource(frost, environmentLens, bar)
    // The clipped frost, when the environment lens does not draw the backdrop itself.
    val clippedFrost = frost?.takeIf { environment == null }
    val position = remember { GlassPosition() }
    val surfaceFill = fill ?: glass.surfaceFill(level, frosted = frost != null || windowFrosted)
    val shadowModifier = Modifier.glassElevation(elevated && !level.floats, shape, glass)
    val floatShadow = Modifier.glassFloatShadow(enabled = level.floats && !bar, shape = shape, color = Color(glass.floatShadow))
    val tracked = if (frost != null) Modifier.onGloballyPositioned { position.offset = it.positionInRoot() } else Modifier
    return this
        .then(shadowModifier)
        .then(floatShadow)
        .then(tracked)
        .environmentLayer(environment, shape, glass, optics) { position.offset }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val layers = glassLayers(glass, level, surfaceFill, outlined, bar)
            val environment = Color(glass.backgroundGradient.first())
            val frostRadius = glass.frostRadiusDp.dp.toPx()
            // Record a margin around the surface so the blur has real content at its edges.
            val margin = ceil(frostRadius * 2).toInt()
            val frostLayer =
                if (clippedFrost != null && !clippedFrost.preBlurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Full-width bars get no edge optics: they have no curved edge to bend light.
                    val lens = if (bar) null else edgeLens(glass, outline, margin, optics)
                    obtainGraphicsLayer().apply {
                        renderEffect = glassOptics(frostRadius, glass.frostSaturation, glass.frostDim, glass.frostLift, lens)
                    }
                } else {
                    null
                }

            val backing = FrostBacking(clippedFrost, frostLayer, margin, environment, glass)
            onDrawBehind {
                drawBacking(backing, position)
                drawGlassLayers(layers, outline, bar)
            }
        }
}

/** Edge optics of a surface of this size drawn through a backdrop layer with [margin] around it. */
private fun CacheDrawScope.edgeLens(
    glass: GlassTokens,
    outline: Outline,
    margin: Int,
    optics: Float,
): GlassLens =
    GlassLens(
        origin = margin.toFloat(),
        size = size,
        cornerRadius = outline.cornerRadius(),
        band = glass.lensBandDp.dp.toPx(),
        strength = glass.lensStrengthDp.dp.toPx() * optics,
        capBoost = glass.lensCapBoost,
        dispersion = glass.lensDispersionDp.dp.toPx() * optics,
        highlight = glass.edgeLight * optics,
        shade = glass.edgeShadeOptics * optics,
    )

/** Panel fill for [level]; lighter whenever something blurred is underneath (our frost or the window's). */
private fun GlassTokens.surfaceFill(
    level: GlassLevel,
    frosted: Boolean,
): Color {
    val panel = panelFillColor(level == GlassLevel.Strong)
    return if (frosted) panel.copy(alpha = panel.alpha * level.frostedFillScale) else panel
}

/** The non-backdrop layers of one glass surface, resolved for its size. */
private class GlassLayers(
    val fill: Brush,
    val upperLight: Brush,
    val cornerLight: Brush,
    val shade: Brush,
    val edge: Brush,
    val edgeWidthPx: Float,
)

private fun CacheDrawScope.glassLayers(
    glass: GlassTokens,
    level: GlassLevel,
    fill: Color,
    outlined: Boolean,
    bar: Boolean,
): GlassLayers {
    val light = Color(glass.specular)
    val highlight = Color(glass.borderHighlight)
    val edge =
        when {
            bar -> SolidColor(highlight.copy(alpha = highlight.alpha * BAR_HAIRLINE_FRACTION))
            else -> {
                val strength = if (outlined) 1f else SOFT_RIM_FRACTION
                Brush.linearGradient(
                    0f to highlight.copy(alpha = highlight.alpha * strength),
                    0.5f to highlight.copy(alpha = highlight.alpha * strength * RIM_MID_FRACTION),
                    1f to highlight.copy(alpha = highlight.alpha * strength * RIM_FAR_FRACTION),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            }
        }
    return GlassLayers(
        fill = Brush.verticalGradient(listOf(fill.compositeSheen(), fill)),
        upperLight =
            Brush.verticalGradient(
                0f to light.copy(alpha = light.alpha * level.lightScale),
                SPECULAR_END to Color.Transparent,
            ),
        cornerLight =
            Brush.radialGradient(
                listOf(light.copy(alpha = light.alpha * level.lightScale * CORNER_LIGHT_FRACTION), Color.Transparent),
                center = Offset.Zero,
                radius = maxOf(size.width, size.height) * CORNER_LIGHT_RADIUS,
            ),
        shade =
            Brush.verticalGradient(
                SHADE_START to Color.Transparent,
                1f to Color(glass.edgeShade).let { it.copy(alpha = it.alpha * if (bar) 0f else level.shadeScale) },
            ),
        edge = edge,
        // The stroke is centred on the outline and the surface is clipped: double it.
        edgeWidthPx = glass.borderWidthDp.dp.toPx() * 2,
    )
}

private fun DrawScope.drawGlassLayers(
    layers: GlassLayers,
    outline: Outline,
    bar: Boolean,
) {
    drawRect(layers.fill)
    drawRect(layers.upperLight)
    drawRect(layers.cornerLight)
    drawRect(layers.shade)
    if (bar) {
        val hairline = layers.edgeWidthPx / 2
        drawRect(layers.edge, topLeft = Offset(0f, size.height - hairline), size = Size(size.width, hairline))
    } else {
        drawOutline(outline, layers.edge, style = Stroke(width = layers.edgeWidthPx))
    }
}

/** Rim strength for borderless surfaces: a hint of edge, not an outline. */
private const val SOFT_RIM_FRACTION = 0.6f

/**
 * Draws the blurred backdrop region behind a glass surface at [surfaceOffset]
 * (root coordinates). Content is usually drawn without a background of its own
 * (the theme background lies outside the source), so the area beneath the glass
 * is first covered with the [environment] colour; otherwise the sharp content
 * would show through the frosted copy.
 */
internal fun DrawScope.drawFrost(
    backdrop: GlassBackdrop,
    frostLayer: GraphicsLayer,
    surfaceOffset: Offset,
    margin: Int,
    environment: Color,
): Boolean {
    if (!backdrop.hasContent || backdrop.isRecording) return false
    // Read so this surface redraws whenever the source content changes.
    backdrop.contentVersion
    val offset = surfaceOffset - backdrop.sourceOffset
    drawRect(environment)
    frostLayer.topLeft = IntOffset(-margin, -margin)
    traced(backdrop.frostSection) {
        frostLayer.record(IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)) {
            translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
        }
    }
    drawLayer(frostLayer)
    return true
}

/** Where a glass surface is, in root coordinates; state so a move redraws its frost. */
@Stable
internal class GlassPosition {
    var offset by mutableStateOf(Offset.Zero)
}

/**
 * Edge optics of surfaces other than the primary one (the mini player passes 1): the
 * same material family, weaker, so the navigation and buttons do not compete with it.
 */
const val SECONDARY_OPTICS = 0.65f

private const val CHROME_FADE_EXTENT_DP = 40
private const val CHROME_FADE_EDGE_ALPHA = 0.9f
private const val CHROME_FADE_BOTTOM_ALPHA = 0.97f

/**
 * With real glass the chrome itself separates from the content, and the content must stay
 * visible THROUGH it: no fade at the chrome's edge, only a breath towards the screen bottom.
 */
private const val FROSTED_FADE_EDGE_ALPHA = 0f
private const val FROSTED_FADE_BOTTOM_ALPHA = 0.15f

/**
 * Behind floating bottom chrome (the mini player): content that scrolls
 * underneath fades progressively into the background, starting a little above
 * the chrome. Together with the translucent player this reads as a material
 * layer over the content while keeping its text legible — without a backdrop
 * blur, which Compose cannot apply to content behind a component.
 *
 * Draws outside its own bounds (upwards) and only under a glass theme.
 */
@Composable
fun Modifier.glassChromeFade(): Modifier {
    val glass = LocalGlassTokens.current ?: return this
    val base = remember(glass) { Color(glass.backgroundGradient.last()) }
    val edgeAlpha = if (isBackdropBlurSupported) FROSTED_FADE_EDGE_ALPHA else CHROME_FADE_EDGE_ALPHA
    val bottomAlpha = if (isBackdropBlurSupported) FROSTED_FADE_BOTTOM_ALPHA else CHROME_FADE_BOTTOM_ALPHA
    return drawBehind {
        val extent = CHROME_FADE_EXTENT_DP.dp.toPx()
        val total = size.height + extent
        drawRect(
            brush =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    extent / total to base.copy(alpha = edgeAlpha),
                    1f to base.copy(alpha = bottomAlpha),
                    startY = -extent,
                    endY = size.height,
                ),
            topLeft = Offset(0f, -extent),
            size = Size(size.width, total),
        )
    }
}

/** Slightly brighter version of a translucent fill, for the top of the sheen gradient. */
private fun Color.compositeSheen(): Color = copy(alpha = (alpha + SHEEN_ALPHA).coerceAtMost(1f))

/**
 * The app-wide glass backdrop: a static vertical gradient with two soft glows,
 * drawn once per size/theme change (no animation) behind all screens.
 *
 * For non-glass themes ([glass] == null) it draws nothing and only hosts the
 * content, so the composition structure is identical for every theme.
 */
@Composable
fun GlassBackground(
    glass: GlassTokens?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop =
        if (glass == null) {
            Modifier
        } else {
            val gradient = remember(glass) { Brush.verticalGradient(glass.backgroundColors()) }
            val glowPrimary = remember(glass) { Color(glass.glowPrimary) }
            val glowSecondary = remember(glass) { Color(glass.glowSecondary) }
            val hasGlows = glowPrimary.alpha > 0f || glowSecondary.alpha > 0f
            Modifier.drawBehind {
                drawRect(gradient)
                if (!hasGlows) return@drawBehind
                val radius = max(size.width, size.height) * GLOW_RADIUS_FRACTION
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowPrimary, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.1f),
                        radius = radius,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glowSecondary, Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.85f),
                        radius = radius,
                    ),
                )
            }
        }
    Box(modifier = modifier.fillMaxSize().then(backdrop)) {
        content()
    }
}

private const val GLOW_RADIUS_FRACTION = 0.6f
