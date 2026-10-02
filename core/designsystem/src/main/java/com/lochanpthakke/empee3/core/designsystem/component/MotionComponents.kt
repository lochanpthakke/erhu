package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.theme.glassRippleConfiguration
import kotlin.math.roundToInt

/** How far a pressed control shrinks. */
private const val PRESSED_SCALE = 0.88f

/**
 * Press feedback by scale: the control dips slightly while pressed and springs
 * back on release (Motion.snappy). Works in every theme; only this composable's
 * layer is touched, nothing recomposes but the press state itself.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappy(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Icon button for player and action controls: scale press feedback, and no
 * ripple under the Glass Effect (a ripple on glass reads as a second, ghost
 * surface). Glass off keeps the standard ripple as well.
 */
@Composable
fun PressIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides glassRippleConfiguration()) {
        IconButton(
            onClick = onClick,
            modifier = modifier.pressScale(interactionSource),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

/**
 * Folds this element away horizontally as [fraction] goes 0 -> 1: its width
 * shrinks to nothing and it fades out. Read in layout and draw only, so a
 * scroll-driven fraction never recomposes it.
 */
fun Modifier.collapseHorizontally(fraction: () -> Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val visible = 1f - fraction().coerceIn(0f, 1f)
        layout((placeable.width * visible).roundToInt(), placeable.height) {
            placeable.placeRelativeWithLayer(0, 0) { alpha = visible }
        }
    }
