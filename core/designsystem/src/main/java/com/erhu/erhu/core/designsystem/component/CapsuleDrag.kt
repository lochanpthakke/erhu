package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Past an end, the dragged capsule moves this much per unit of finger travel (rubber band). */
private const val OVERDRAG_RESISTANCE = 0.3f

/** A release this fast (positions per second) carries the capsule this far further when choosing. */
private const val FLING_PROJECTION_S = 0.08f

/** Grabbing the capsule: its bounds plus this margin (an easy target for a thumb). */
private val GRAB_MARGIN = 8.dp

/**
 * Drag the capsule between items: touch it (or just around it) and move sideways past the touch
 * slop; it follows the finger and, released, settles on the nearest item ([onSettle] once, only if
 * it differs from [current]). Taps elsewhere, vertical movement and other gestures are left alone;
 * a cancelled drag returns to [current]. Only the capsule's [position] moves while dragging: the
 * selection itself changes once, on release.
 */
@Composable
fun Modifier.capsuleDrag(
    position: Animatable<Float, AnimationVector1D>,
    slots: CapsuleSlots,
    current: () -> Int,
    enabled: Boolean,
    onSettle: (Int) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    if (!enabled) return this
    return pointerInput(position, slots) {
        val padding = CAPSULE_PADDING.toPx()
        val inset = CAPSULE_INSET.toPx()
        val grab = GRAB_MARGIN.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val capsule = slots.capsuleBetween(position.value, padding, inset)
            val grabbed = capsule != null && capsule.inflate(grab).contains(down.position)
            val drag = if (grabbed) awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } else null
            if (drag != null) {
                // From here the gesture is the capsule's: the item under the finger sees no tap.
                val start = position.value + (drag.position.x - down.position.x) / slots.step
                var released = false
                var velocity = 0f
                try {
                    val (lifted, speed) = followDrag(drag, start, slots) { scope.launch { position.snapTo(it) } }
                    released = lifted
                    velocity = speed
                } finally {
                    val target = if (released) nearest(position.value, velocity, slots.count) else current()
                    scope.launch {
                        try {
                            position.animateTo(target.toFloat(), Motion.selection(), initialVelocity = velocity)
                        } catch (_: CancellationException) {
                            // A new selection or drag took over.
                        }
                    }
                    if (released && target != current()) onSettle(target)
                }
            }
        }
    }
}

/**
 * Moves the capsule with the finger until it lifts (true, with the release velocity in positions
 * per second) or the pointer goes away (false). Past the ends the movement is resisted.
 */
private suspend fun AwaitPointerEventScope.followDrag(
    drag: PointerInputChange,
    start: Float,
    slots: CapsuleSlots,
    moveTo: (Float) -> Unit,
): Pair<Boolean, Float> {
    val step = slots.step
    val last = (slots.count - 1).toFloat()
    val tracker = VelocityTracker()
    tracker.addPosition(drag.uptimeMillis, drag.position)
    var raw = start
    moveTo(rubberBand(raw, last))
    var change: PointerInputChange? = drag
    while (change != null && change.pressed) {
        change = awaitPointerEvent().changes.firstOrNull { it.id == drag.id }
        if (change != null) {
            tracker.addPosition(change.uptimeMillis, change.position)
            raw += change.positionChange().x / step
            change.consume()
            moveTo(rubberBand(raw, last))
        }
    }
    val released = change != null
    return released to if (released) tracker.calculateVelocity().x / step else 0f
}

/** The item a release lands on: the nearest to where the capsule is, carried a little by a flick. */
private fun nearest(
    position: Float,
    velocity: Float,
    count: Int,
): Int = (position + velocity * FLING_PROJECTION_S).roundToInt().coerceIn(0, count - 1)

private fun rubberBand(
    raw: Float,
    last: Float,
): Float =
    when {
        raw < 0f -> raw * OVERDRAG_RESISTANCE
        raw > last -> last + (raw - last) * OVERDRAG_RESISTANCE
        else -> raw
    }
