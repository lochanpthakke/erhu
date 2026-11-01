package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.theme.glassRippleConfiguration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a pressed control shrinks. */
private const val PRESSED_SCALE = 0.94f
private const val SCALE_THRESHOLD = 0.001f
private const val QUICK_TAP_EPSILON = 0.005f
private const val RELEASE_IMPULSE = 24f
private const val SELECTION_IMPULSE = 32f

/** Wide buttons and chips (Play / Shuffle, suggestions) dip less than small icons. */
const val BUTTON_PRESSED_SCALE = 0.96f

/** Tiny artwork compression; its parent keeps the original hit and layout bounds. */
const val ARTWORK_PRESSED_SCALE = 0.975f

/** One owner for press scale. A released fast tap gets a velocity impulse, not a fake hold/delay. */
internal class PressMotionState(
    private val pressedScale: Float,
) {
    val scale = Animatable(1f, visibilityThreshold = SCALE_THRESHOLD)

    suspend fun press() = scale.animateTo(pressedScale, Motion.responsive(SCALE_THRESHOLD))

    suspend fun release() {
        val velocity =
            if (abs(scale.value - 1f) < QUICK_TAP_EPSILON) {
                -(1f - pressedScale) * RELEASE_IMPULSE
            } else {
                scale.velocity
            }
        scale.animateTo(1f, Motion.expressive(SCALE_THRESHOLD), initialVelocity = velocity)
    }

    suspend fun cancel() = scale.animateTo(1f, Motion.responsive(SCALE_THRESHOLD))

    suspend fun reset() = scale.snapTo(1f)
}

/** Remember the actual reported state, so initial composition and failed actions never jiggle. */
internal class SelectionMotionState(
    initialValue: Any?,
) {
    private var previousValue = initialValue
    val displacement = Animatable(0f, visibilityThreshold = SCALE_THRESHOLD)

    suspend fun update(
        value: Any?,
        kickWhen: (Any?) -> Boolean = { true },
    ) {
        if (value == previousValue) return
        previousValue = value
        if (!kickWhen(value)) return
        // Rapid changes can interrupt an existing pulse near its peak. Spend
        // only its remaining amplitude, rather than adding a full kick each tap.
        val remaining = (1f - abs(displacement.value)).coerceIn(0f, 1f)
        displacement.animateTo(0f, Motion.playful(SCALE_THRESHOLD), initialVelocity = SELECTION_IMPULSE * remaining)
    }
}

/**
 * Feedback belongs to the icon, not the button's touch target or surrounding layout.
 * [kickWhen] picks the changes that jiggle (e.g. only becoming selected); every change is
 * still remembered, so returning to a value later doesn't jiggle by itself.
 */
@Composable
fun Modifier.selectionJiggle(
    value: Any?,
    identity: Any? = Unit,
    expressive: Boolean = false,
    kickWhen: (Any?) -> Boolean = { true },
): Modifier {
    val state = remember(identity) { SelectionMotionState(value) }
    val currentKickWhen by rememberUpdatedState(kickWhen)
    LaunchedEffect(state, value) { state.update(value, currentKickWhen) }
    return graphicsLayer {
        val impulse = state.displacement.value
        rotationZ = impulse * if (expressive) FAVORITE_ROTATION else SELECTION_ROTATION
        val scale = 1f + impulse * if (expressive) FAVORITE_POP else SELECTION_POP
        scaleX = scale
        scaleY = scale
    }
}

private const val FAVORITE_ROTATION = 5f
private const val FAVORITE_POP = 0.10f
private const val SELECTION_ROTATION = 2f
private const val SELECTION_POP = 0.035f

/**
 * Press feedback by scale: the control dips slightly while pressed and springs
 * back on release. Observe raw interactions: a press and release can arrive in
 * one frame and be lost by collectIsPressedAsState. Actions never wait for motion.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PRESSED_SCALE,
    enabled: Boolean = true,
): Modifier {
    val state = remember(pressedScale) { PressMotionState(pressedScale) }
    LaunchedEffect(interactionSource, state, enabled) {
        state.reset()
        if (!enabled) return@LaunchedEffect
        val presses = mutableSetOf<PressInteraction.Press>()
        interactionSource.interactions.filter { it is PressInteraction }.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    presses.add(interaction)
                    state.press()
                }
                is PressInteraction.Release -> if (presses.remove(interaction.press) && presses.isEmpty()) state.release()
                is PressInteraction.Cancel -> if (presses.remove(interaction.press) && presses.isEmpty()) state.cancel()
            }
        }
    }
    return graphicsLayer {
        scaleX = if (enabled) state.scale.value else 1f
        scaleY = scaleX
    }
}

/**
 * A directional control's tap (previous / next): as the press is released the icon leans a
 * touch towards [direction] (-1 = start, +1 = end) and springs back. Touch feedback only: the
 * result (the track changing) has its own motion. Rapid taps spend the remaining amplitude.
 */
@Composable
fun Modifier.releaseNudge(
    interactionSource: InteractionSource,
    direction: Int,
    enabled: Boolean = true,
): Modifier {
    val offset = remember { Animatable(0f, visibilityThreshold = SCALE_THRESHOLD) }
    LaunchedEffect(interactionSource, enabled) {
        offset.snapTo(0f)
        if (!enabled) return@LaunchedEffect
        interactionSource.interactions.filterIsInstance<PressInteraction.Release>().collectLatest {
            val remaining = (1f - abs(offset.value)).coerceIn(0f, 1f)
            offset.animateTo(0f, Motion.playful(SCALE_THRESHOLD), initialVelocity = SELECTION_IMPULSE * remaining)
        }
    }
    return graphicsLayer { translationX = offset.value * direction * NUDGE_DISTANCE.toPx() }
}

private val NUDGE_DISTANCE = 3.dp

/**
 * Icon button for player and action controls: scale press feedback, and no
 * ripple under the Glass Effect (a ripple on glass reads as a second, ghost
 * surface). Glass off keeps the standard ripple as well. [nudge] (-1 / +1) adds
 * a [releaseNudge] for directional controls.
 */
@Composable
fun PressIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    nudge: Int = 0,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides glassRippleConfiguration()) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        ) {
            Box(
                Modifier
                    .pressScale(interactionSource, enabled = enabled)
                    .then(if (nudge != 0) Modifier.releaseNudge(interactionSource, nudge, enabled) else Modifier),
            ) { content() }
        }
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
