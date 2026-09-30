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
}

private const val SHEEN_ALPHA = 0.06f

/**
 * Frosted glass panel: translucent fill with a faint top sheen, a thin
 * light-to-faint gradient border and a soft shadow. Pure draw operations — no
 * blur and no animation — so it is cheap enough for lists.
 *
 * Returns the receiver unchanged when the active theme has no glass tokens.
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape,
    strong: Boolean = false,
    elevated: Boolean = true,
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
    return this
        .then(shadowModifier)
        .clip(shape)
        .background(fillBrush, shape)
        .border(glass.borderWidthDp.dp, borderBrush, shape)
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
