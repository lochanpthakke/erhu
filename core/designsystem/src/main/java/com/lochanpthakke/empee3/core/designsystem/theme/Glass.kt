package com.lochanpthakke.empee3.core.designsystem.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
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

/** Saturation of the blurred backdrop: keeps colours alive through the frost. */
private const val FROST_SATURATION = 1.15f

/** Where the specular light fades out and the edge shade starts (fractions of the height). */
private const val SPECULAR_END = 0.45f
private const val SHADE_START = 0.6f

/** Rim strength for borderless surfaces: a hint of edge, not an outline. */
private const val SOFT_RIM_FRACTION = 0.6f

/**
 * Internal material levels; not a user setting. They only change how much of
 * the frosted backdrop shows through ([blurredFillScale] scales the panel fill
 * when a blurred backdrop is drawn underneath it).
 */
enum class GlassLevel(
    internal val blurredFillScale: Float,
) {
    /** Header buttons, search, secondary floating controls. */
    Standard(0.55f),

    /** Mini player, library navigation. */
    Floating(0.62f),

    /** Major controls and the drawer: more separation from busy backgrounds. */
    Strong(0.85f),
}

/**
 * The shared glass material. Layers, bottom to top, all clipped to [shape]:
 *
 * 1. the [backdrop] behind the surface, blurred and slightly saturated
 *    (Android 12+ with a [GlassBackdrop]; skipped otherwise);
 * 2. a translucent fill in the theme's own panel colour (lighter when frosted);
 * 3. a soft specular light on the upper part;
 * 4. a shade on the lower part, for depth;
 * 5. a thin rim: a gradient outline, or (when not [outlined]) a faint top edge.
 *
 * Without a backdrop or below Android 12 it is the same material minus the
 * blur, with the panel fill at full strength. Pure draw operations plus, when
 * frosted, one small graphics layer per surface; content is not recomposed.
 *
 * Returns the receiver unchanged when the Glass Effect is off.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    strong: Boolean = false,
    elevated: Boolean = true,
    outlined: Boolean = true,
    level: GlassLevel = if (strong) GlassLevel.Floating else GlassLevel.Standard,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
): Modifier {
    val glass = LocalGlassTokens.current ?: return this
    val frost = backdrop?.takeIf { isBackdropBlurSupported }
    val position = remember { GlassPosition() }
    val panel = glass.panelFillColor(level != GlassLevel.Standard)
    val fill = if (frost != null) panel.copy(alpha = panel.alpha * level.blurredFillScale) else panel
    val shadowModifier =
        if (elevated) {
            Modifier.shadow(
                elevation = glass.panelElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
        } else {
            Modifier
        }
    val tracked = if (frost != null) Modifier.onGloballyPositioned { position.offset = it.positionInRoot() } else Modifier
    return this
        .then(shadowModifier)
        .then(tracked)
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fillBrush = Brush.verticalGradient(listOf(fill.compositeSheen(), fill))
            val specular =
                Brush.verticalGradient(0f to Color(glass.specular), SPECULAR_END to Color.Transparent)
            val shade = Brush.verticalGradient(SHADE_START to Color.Transparent, 1f to Color(glass.edgeShade))
            val highlight = Color(glass.borderHighlight)
            val rim =
                if (outlined) {
                    Brush.verticalGradient(listOf(highlight, Color(glass.borderShadow)))
                } else {
                    Brush.verticalGradient(
                        listOf(highlight.copy(alpha = highlight.alpha * SOFT_RIM_FRACTION), Color.Transparent),
                    )
                }
            // The stroke is centred on the outline and the surface is clipped: double it.
            val rimStroke = Stroke(width = glass.borderWidthDp.dp.toPx() * 2)

            val environment = Color(glass.backgroundGradient.first())
            val frostRadius = glass.frostRadiusDp.dp.toPx()
            val frostLayer =
                if (frost != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    obtainGraphicsLayer().apply { renderEffect = frostEffect(frostRadius) }
                } else {
                    null
                }
            // Record a margin around the surface so the blur has real content at its edges.
            val margin = ceil(frostRadius * 2).toInt()

            onDrawBehind {
                if (frostLayer != null && frost != null) {
                    drawFrost(frost, frostLayer, position.offset, margin, environment)
                }
                drawRect(fillBrush)
                drawRect(specular)
                drawRect(shade)
                drawOutline(outline, rim, style = rimStroke)
            }
        }
}

/**
 * Draws the blurred backdrop region behind a glass surface at [surfaceOffset]
 * (root coordinates). Content is usually drawn without a background of its own
 * (the theme background lies outside the source), so the area beneath the glass
 * is first covered with the [environment] colour; otherwise the sharp content
 * would show through the frosted copy.
 */
private fun DrawScope.drawFrost(
    backdrop: GlassBackdrop,
    frostLayer: GraphicsLayer,
    surfaceOffset: Offset,
    margin: Int,
    environment: Color,
) {
    if (!backdrop.hasContent || backdrop.isRecording) return
    // Read so this surface redraws whenever the source content changes.
    backdrop.contentVersion
    val offset = surfaceOffset - backdrop.sourceOffset
    drawRect(environment)
    frostLayer.topLeft = IntOffset(-margin, -margin)
    frostLayer.record(IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)) {
        translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
    }
    drawLayer(frostLayer)
}

/** Where a glass surface is, in root coordinates; state so a move redraws its frost. */
@Stable
private class GlassPosition {
    var offset by mutableStateOf(Offset.Zero)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun frostEffect(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    val saturate =
        RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(FROST_SATURATION) }),
        )
    // Blur first, then saturate the blurred result.
    return RenderEffect.createChainEffect(saturate, blur).asComposeRenderEffect()
}

private const val CHROME_FADE_EXTENT_DP = 40
private const val CHROME_FADE_EDGE_ALPHA = 0.9f
private const val CHROME_FADE_BOTTOM_ALPHA = 0.97f

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
    return drawBehind {
        val extent = CHROME_FADE_EXTENT_DP.dp.toPx()
        val total = size.height + extent
        drawRect(
            brush =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    extent / total to base.copy(alpha = CHROME_FADE_EDGE_ALPHA),
                    1f to base.copy(alpha = CHROME_FADE_BOTTOM_ALPHA),
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
