package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** At or above this the player counts as fully expanded (float tolerance). */
private const val SETTLED = 0.999f

/** Released below this expansion (and not flung up), the player collapses. */
private const val SETTLE_THRESHOLD = 0.65f

/** A release faster than this (per second, in screen heights) decides the direction on its own. */
private const val FLING_FRACTION_PER_SECOND = 1.2f

/** Dragged sideways past this share of the content width, the track changes on release. */
private const val SKIP_DISTANCE_FRACTION = 0.25f

/** A sideways release faster than this skips regardless of distance. */
private val SKIP_FLING_VELOCITY = 1100.dp

/** How long a thrown swipe waits for the player to report the new track. */
private const val TRACK_CHANGE_TIMEOUT_MS = 800L

/**
 * The player as ONE object with one continuous state: [progress] 0 = collapsed
 * (the mini player), 1 = expanded (Now Playing). Everything that belongs to the
 * player — the expanding surface, the travelling artwork, the content fading in,
 * the bottom chrome stepping aside — is drawn from this single value, so a tap,
 * a drag and a release all move the same timeline and can interrupt each other
 * at any point.
 *
 * The geometry it interpolates between is measured by the two representations
 * themselves (root coordinates, updated on layout, never per frame).
 */
@Stable
class PlayerTransitionState {
    val progress = Animatable(0f)

    /** The mini player surface, and its artwork, in root coordinates. */
    var miniBounds by mutableStateOf(Rect.Zero)
    var miniArtworkBounds by mutableStateOf(Rect.Zero)

    /** Now Playing's hero artwork in root coordinates (in the expanded layout). */
    var heroArtworkBounds by mutableStateOf(Rect.Zero)

    /** Fully expanded: the real Now Playing artwork shows instead of the travelling one. */
    val isSettledOpen: Boolean get() = progress.value >= SETTLED

    /** True while the player is open or opening (drives Back and accessibility). */
    val isOpen: Boolean get() = progress.targetValue > 0f

    suspend fun expand(velocity: Float = 0f) = progress.animateTo(1f, Motion.hero(), initialVelocity = velocity)

    suspend fun collapse(velocity: Float = 0f) = progress.animateTo(0f, Motion.hero(), initialVelocity = velocity)

    /** Direct manipulation: the finger moved by [delta] in units of the drag distance. */
    internal suspend fun dragBy(delta: Float) = progress.snapTo((progress.value + delta).coerceIn(0f, 1f))

    /**
     * Finishing a drag: a fast enough fling decides by its direction, otherwise
     * the nearer state wins. The settle starts from the finger's velocity.
     */
    internal suspend fun release(velocity: Float) {
        val expand =
            when {
                velocity > FLING_FRACTION_PER_SECOND -> true
                velocity < -FLING_FRACTION_PER_SECOND -> false
                else -> progress.value > SETTLE_THRESHOLD
            }
        progress.animateTo(if (expand) 1f else 0f, Motion.gestureSettle(), initialVelocity = velocity)
    }
}

@Composable
fun rememberPlayerTransitionState(): PlayerTransitionState = remember { PlayerTransitionState() }

/** The player's transition, provided by the shell to both representations. */
val LocalPlayerTransition = staticCompositionLocalOf<PlayerTransitionState?> { null }

/**
 * Vertical drag that moves the player between its states: down on Now Playing
 * collapses it, up on the mini player expands it. The player follows the finger
 * one-to-one (dragging from the mini player's height to the top of the screen is
 * the whole transition) and the release settles with the finger's velocity. Taps are untouched (no movement,
 * no drag); a drag in the other direction or sideways is left to other handlers.
 */
@Composable
fun Modifier.playerDrag(
    state: PlayerTransitionState,
    expandsUpward: Boolean,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(state, expandsUpward) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var slop = Offset.Zero
            val start =
                awaitTouchSlopOrCancellation(down.id) { change, over ->
                    // Only claim clearly vertical drags in this handler's direction.
                    val vertical = abs(over.y) > abs(over.x)
                    val rightWay = if (expandsUpward) over.y < 0 else over.y > 0
                    if (vertical && rightWay) {
                        change.consume()
                        slop = over
                    }
                } ?: return@awaitEachGesture
            if (slop == Offset.Zero) return@awaitEachGesture
            // A full transition is the distance from the mini player to the top of the screen.
            val travel = state.miniBounds.top.takeIf { it > 0f } ?: size.height.toFloat().coerceAtLeast(1f)
            val tracker = VelocityTracker()
            tracker.addPosition(start.uptimeMillis, start.position)
            scope.launch { state.dragBy(-slop.y / travel) }
            drag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                val dy = change.positionChange().y
                change.consume()
                scope.launch { state.dragBy(-dy / travel) }
            }
            val velocity = -tracker.calculateVelocity().y / travel
            scope.launch { state.release(velocity) }
        }
    }
}

/**
 * Sideways swipe on Now Playing's artwork / title: the content follows the
 * finger ([swipeOffset]); far or fast enough, it keeps travelling off screen,
 * the track changes and the new one arrives from the other side ([onSkipThrown]
 * hands the direction to the track transition) — one continuous motion.
 * Otherwise, or if "previous" just restarts the song, it springs back.
 */
@Composable
internal fun Modifier.trackSwipe(
    swipeOffset: Animatable<Float, *>,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    canSkipNext: () -> Boolean,
    onSkipThrown: (direction: Int) -> Unit,
    currentTrackId: () -> Long?,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        val fling = SKIP_FLING_VELOCITY.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var slop = Offset.Zero
            val start =
                awaitTouchSlopOrCancellation(down.id) { change, over ->
                    if (abs(over.x) > abs(over.y)) {
                        change.consume()
                        slop = over
                    }
                } ?: return@awaitEachGesture
            if (slop == Offset.Zero) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(start.uptimeMillis, start.position)
            // This gesture's own travel decides the outcome: the shared offset can be
            // reset under it when an earlier swipe's track change lands mid-drag.
            var travelled = slop.x
            scope.launch { swipeOffset.snapTo(swipeOffset.value + slop.x) }
            drag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                val dx = change.positionChange().x
                travelled += dx
                change.consume()
                scope.launch { swipeOffset.snapTo(swipeOffset.value + dx) }
            }
            val velocity = tracker.calculateVelocity().x
            val width = size.width.toFloat()
            val direction =
                when {
                    abs(velocity) > fling -> if (velocity < 0) 1 else -1
                    abs(travelled) > width * SKIP_DISTANCE_FRACTION -> if (travelled < 0) 1 else -1
                    else -> 0
                }
            scope.launch {
                releaseSwipe(swipeOffset, velocity, width, direction, canSkipNext, onNext, onPrevious, onSkipThrown, currentTrackId)
            }
        }
    }
}

@Suppress("LongParameterList")
private suspend fun releaseSwipe(
    swipeOffset: Animatable<Float, *>,
    velocity: Float,
    width: Float,
    direction: Int,
    canSkipNext: () -> Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipThrown: (Int) -> Unit,
    currentTrackId: () -> Long?,
) {
    // +1 = thrown left (next), -1 = thrown right (previous), 0 = not far or fast enough.
    val towardsNext = direction > 0
    if (direction == 0 || (towardsNext && !canSkipNext())) {
        swipeOffset.animateTo(0f, Motion.gestureSettle(), initialVelocity = velocity)
        return
    }
    val before = currentTrackId()
    try {
        swipeOffset.animateTo(if (towardsNext) -width else width, Motion.gestureSettle(), initialVelocity = velocity)
    } finally {
        // The skip is committed even when the next swipe interrupts this throw, so
        // rapid swipes each change the track. Thrown left = next, right = previous.
        onSkipThrown(if (towardsNext) 1 else -1)
        if (towardsNext) onNext() else onPrevious()
    }
    val changed = withTimeoutOrNull(TRACK_CHANGE_TIMEOUT_MS) { snapshotFlow { currentTrackId() }.first { it != before } } != null
    if (!changed) {
        onSkipThrown(0)
        swipeOffset.animateTo(0f, Motion.gestureSettle())
    }
}
