package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
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

/**
 * Frosted glass panel: translucent fill with a faint top sheen, a thin
 * light-to-faint gradient border and a soft shadow. Pure draw operations — no
 * blur and no animation — so it is cheap enough for lists.
 *
 * [outlined] = false gives a borderless material layer whose edge comes only
 * from tonal separation and the sheen (used for floating chrome).
 *
 * Returns the receiver unchanged when the active theme has no glass tokens.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    strong: Boolean = false,
    elevated: Boolean = true,
    outlined: Boolean = true,
): Modifier {
    val glass = LocalGlassTokens.current ?: return this
    val fill = glass.panelFillColor(strong)
    val fillBrush =
        remember(glass, strong) {
            Brush.verticalGradient(listOf(fill.compositeSheen(), fill))
        }
    val borderBrush =
        remember(glass) {
            Brush.verticalGradient(listOf(Color(glass.borderHighlight), Color(glass.borderShadow)))
        }
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
    val base =
        this
            .then(shadowModifier)
            .clip(shape)
            .background(fillBrush, shape)
    return if (outlined) base.border(glass.borderWidthDp.dp, borderBrush, shape) else base
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
            Modifier.drawBehind {
                drawRect(gradient)
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
