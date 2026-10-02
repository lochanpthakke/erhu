package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer

/** Pressed glass: a slight compression and a touch of light, instead of a ripple. */
private const val PRESSED_SCALE = 0.97f
private const val PRESSED_LIGHT_ALPHA = 0.08f

/**
 * Liquid-glass press feedback for a glass surface of [shape] driven by
 * [interactionSource]: the surface compresses very slightly and brightens a
 * little while pressed, short and interruptible. Pair it with a clickable that
 * has no indication (see [glassIndication]). No-op when the Glass Effect is off.
 */
@Composable
fun Modifier.glassPressEffect(
    interactionSource: InteractionSource,
    shape: Shape,
): Modifier {
    if (LocalGlassTokens.current == null) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(targetValue = if (pressed) 1f else 0f, label = "glassPress")
    return this
        .graphicsLayer {
            val scale = 1f - (1f - PRESSED_SCALE) * progress
            scaleX = scale
            scaleY = scale
        }.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                if (progress > 0f) drawOutline(outline, Color.White.copy(alpha = PRESSED_LIGHT_ALPHA * progress))
            }
        }
}

/**
 * Indication for clickable glass surfaces: none under the Glass Effect (the
 * press is shown by [glassPressEffect]), the normal ripple otherwise.
 */
@Composable
fun glassIndication(): Indication? = if (LocalGlassTokens.current != null) null else LocalIndication.current

/**
 * Ripple configuration for Material buttons on glass: none under the Glass
 * Effect (pair with [glassPressEffect]), the current one otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassRippleConfiguration(): RippleConfiguration? = if (LocalGlassTokens.current != null) null else LocalRippleConfiguration.current
