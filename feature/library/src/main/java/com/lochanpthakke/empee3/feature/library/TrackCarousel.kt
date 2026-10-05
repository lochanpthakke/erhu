package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/** Dragged past this share of the page, the track changes on release. */
private const val SKIP_DISTANCE_FRACTION = 0.25f

/** A sideways release faster than this skips regardless of distance (in its direction). */
private val SKIP_FLING_VELOCITY = 900.dp

/** Dragging towards a side with no song moves the content this much per finger pixel. */
private const val EDGE_RESISTANCE = 0.25f

/** A committed skip is sent to the player once the strip is this close (share of a page) to the new page. */
private const val COMMIT_FRACTION = 0.04f

/** ...or after this long at the latest. */
private const val COMMIT_TIMEOUT_MS = 450L

/** A committed skip whose track change never arrives (e.g. the player refused it) springs back after this. */
private const val TRACK_CHANGE_TIMEOUT_MS = 1_000L

/**
 * Now Playing's tracks as a strip of pages: previous | current | next, one
 * [page] apart. The strip's position is [position] = [offset] + [base]: the
 * strip rests where [offset] == -[base]. While a finger is down the offset is
 * the finger (direct manipulation); the neighbours are already composed, so
 * their artwork is loaded before they come into view.
 *
 * A committed swipe asks the player to skip AT RELEASE and settles the strip
 * toward the next page with the finger's velocity. When the player reports the
 * new track, the pages move one slot over and [base] moves one page the other
 * way IN THE SAME COMPOSITION, so not a pixel moves at that moment and the
 * running settle already aims at the new rest position. A track change from the
 * buttons or the end of a song slides the same strip, so every track change is
 * one motion with no second animation on top.
 */
@Stable
internal class TrackCarouselState(
    private val scope: CoroutineScope,
) {
    val offset = Animatable(0f)

    /** Page shifts applied when the current track changed (see the class doc). */
    private var base by mutableFloatStateOf(0f)

    /** Distance between two pages (the player's width), in px. */
    var page by mutableFloatStateOf(0f)

    /** Where the strip is drawn, relative to the current page at rest (0). Read in the draw phase. */
    val position: Float get() = offset.value + base

    private var dragging = false

    /** Where the finger has put the strip (relative, like [position]): updated per pointer event, so no movement is lost. */
    private var dragPosition = 0f

    /** Committed swipes whose track change has not arrived yet (+ = next, - = previous). */
    private var pending = 0
    private var settle: Job? = null
    private var watchdog: Job? = null

    /** A committed skip waiting for the strip to arrive on the new page (see [commit]). */
    private var deferredSkip: (() -> Unit)? = null
    private var deferredWait: Job? = null

    internal fun startDrag() {
        // A new swipe commits the previous one now, so rapid swipes each change the track.
        fireDeferredSkip()
        dragging = true
        settle?.cancel()
        dragPosition = position
    }

    internal fun dragBy(
        delta: Float,
        canGoNext: Boolean,
        canGoPrevious: Boolean,
    ) {
        val towardsMissing = (dragPosition + delta < 0f && !canGoNext) || (dragPosition + delta > 0f && !canGoPrevious)
        dragPosition += if (towardsMissing) delta * EDGE_RESISTANCE else delta
        val target = dragPosition - base
        scope.launch { offset.snapTo(target) }
    }

    /** Ends a drag: [direction] +1 = skip to next, -1 = previous, 0 = spring back. */
    internal fun release(
        direction: Int,
        velocity: Float,
        skip: (Int) -> Unit,
    ) {
        dragging = false
        if (direction == 0) {
            settleAt(0f, velocity)
            return
        }
        commit(direction, velocity, skip)
    }

    /** The skip buttons: the same strip motion as a swipe, starting at rest. */
    internal fun skipFromButton(
        direction: Int,
        skip: (Int) -> Unit,
    ) {
        fireDeferredSkip()
        commit(direction, 0f, skip)
    }

    /**
     * The strip settles on the new page at once: its artwork and title are already
     * there, so the new track is established visually without waiting for anything.
     * The player is told to skip as the strip arrives (within [COMMIT_FRACTION] of a
     * page): the new state recomposes Now Playing, and that work lands on a frame
     * where nothing is moving instead of stalling the settle half-way.
     */
    private fun commit(
        direction: Int,
        velocity: Float,
        skip: (Int) -> Unit,
    ) {
        pending += direction
        settleAt(-direction * page, velocity)
        deferredSkip = { skip(direction) }
        deferredWait?.cancel()
        deferredWait =
            scope.launch {
                withTimeoutOrNull(COMMIT_TIMEOUT_MS) {
                    snapshotFlow { abs(position + direction * page) }.first { it <= page * COMMIT_FRACTION }
                }
                fireDeferredSkip()
            }
    }

    private fun fireDeferredSkip() {
        val skip = deferredSkip ?: return
        deferredSkip = null
        deferredWait?.cancel()
        skip()
        armWatchdog()
    }

    /**
     * Called DURING COMPOSITION when the current track changed, together with the
     * new pages. [direction]: +1 the new one was the next song, -1 the previous one,
     * 0 an unrelated jump (e.g. a queue tap).
     */
    internal fun onTrackChanged(direction: Int) {
        val shift =
            if (pending != 0) {
                sign(pending.toFloat()).toInt().also { pending -= it }
            } else {
                direction
            }
        base += shift * page
        if (dragging) dragPosition += shift * page
    }

    /** After a track change has been composed: settle on the (new) current page unless a finger holds the strip. */
    internal fun settleAfterTrackChange() {
        if (pending == 0) watchdog?.cancel()
        if (!dragging) settleAt(0f, offset.velocity)
    }

    /** Animates [position] to [target] (relative to the current page). */
    private fun settleAt(
        target: Float,
        velocity: Float,
    ) {
        settle?.cancel()
        val absolute = target - base
        settle = scope.launch { offset.animateTo(absolute, Motion.trackSettle(), initialVelocity = velocity) }
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog =
            scope.launch {
                delay(TRACK_CHANGE_TIMEOUT_MS)
                if (pending != 0) {
                    pending = 0
                    if (!dragging) settleAt(0f, 0f)
                }
            }
    }
}

@Composable
internal fun rememberTrackCarouselState(): TrackCarouselState {
    val scope = rememberCoroutineScope()
    return remember(scope) { TrackCarouselState(scope) }
}

/**
 * Follows the current song: when it changes, tells [state] from which side it
 * came (the previous composition's next / previous song) in the same
 * composition that shows the new pages, then lets the strip settle.
 */
@Composable
internal fun TrackChangeEffect(
    state: TrackCarouselState,
    current: Song?,
    previousSong: Song?,
    nextSong: Song?,
) {
    val last = remember { LastNeighbours() }
    val id = current?.id
    if (id != last.current) {
        if (last.current != null && id != null) {
            state.onTrackChanged(
                when (id) {
                    last.next -> 1
                    last.previous -> -1
                    else -> 0
                },
            )
        }
        last.current = id
    }
    last.next = nextSong?.id
    last.previous = previousSong?.id
    LaunchedEffect(id) { state.settleAfterTrackChange() }
}

private class LastNeighbours {
    var current: Long? = null
    var next: Long? = null
    var previous: Long? = null
}

/**
 * The strip itself: [previous], [current] and [next] side by side, [page] apart,
 * moved by the shared position in the draw phase only (no recomposition per frame).
 * Pages are keyed by song, so when the strip re-centres a page keeps its
 * composition (and its loaded artwork) as it becomes the current one.
 */
@Composable
internal fun TrackCarousel(
    state: TrackCarouselState,
    previous: Song?,
    current: Song,
    next: Song?,
    modifier: Modifier = Modifier,
    content: @Composable (song: Song, isCurrent: Boolean) -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val pages = listOfNotNull(previous?.let { -1 to it }, 0 to current, next?.let { 1 to it })
        pages.forEach { (slot, song) ->
            // A song can be its own neighbour (2-song repeat, single song): keep keys unique.
            val duplicate = slot != 0 && (song.id == current.id || (slot == 1 && song.id == previous?.id))
            key(if (duplicate) "${song.id}#$slot" else song.id) {
                Box(
                    modifier =
                        Modifier.graphicsLayer {
                            val x = slot * state.page + state.position
                            translationX = x
                            // A page entirely off screen stays composed (its artwork stays loaded) but is not drawn.
                            alpha = if (abs(x) >= state.page && state.page > 0f) 0f else 1f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    content(song, slot == 0)
                }
            }
        }
    }
}

/**
 * Horizontal swipe on the strip. Only clearly horizontal drags are claimed (the
 * vertical drag collapses the player). Release: a fling in a direction decides
 * on its own; otherwise a quarter page decides; otherwise it springs back.
 */
@Composable
internal fun Modifier.trackCarouselSwipe(
    state: TrackCarouselState,
    canGoNext: () -> Boolean,
    canGoPrevious: () -> Boolean,
    skip: (direction: Int) -> Unit,
): Modifier =
    pointerInput(state) {
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
            state.startDrag()
            var travelled = slop.x
            state.dragBy(slop.x, canGoNext(), canGoPrevious())
            trackDrag(start.id, tracker) { delta ->
                travelled += delta.x
                state.dragBy(delta.x, canGoNext(), canGoPrevious())
            }
            val velocity = tracker.calculateVelocity().x
            val wanted =
                when {
                    abs(velocity) > fling -> if (velocity < 0) 1 else -1
                    abs(travelled) > state.page * SKIP_DISTANCE_FRACTION -> if (travelled < 0) 1 else -1
                    else -> 0
                }
            val allowed = (wanted == 1 && canGoNext()) || (wanted == -1 && canGoPrevious())
            state.release(if (allowed) wanted else 0, velocity, skip)
        }
    }
