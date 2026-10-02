package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion

/** Scroll distance that takes the bottom chrome from expanded to fully compact. */
private val COLLAPSE_DISTANCE = 120.dp

/** When scrolling stops part-way, the chrome settles to the nearer state. */
private const val SETTLE_THRESHOLD = 0.5f

/**
 * How compact the library's bottom chrome is: 0 = expanded (mini player above
 * the section navigation), 1 = compact (one row: current section, mini player,
 * search). Library content scrolling drives it continuously through
 * [connection]: scrolling down compacts, scrolling up expands, and when the
 * scroll ends it settles to the nearer state.
 *
 * [fraction] is read only in layout/draw of the chrome, so following the finger
 * never recomposes the library.
 */
@Stable
class LibraryChromeState internal constructor(
    private val collapseDistancePx: Float,
) {
    var fraction by mutableFloatStateOf(0f)
        private set

    val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only scrolling the content really did moves the chrome (no overscroll at the ends).
                if (consumed.y != 0f) {
                    fraction = (fraction - consumed.y / collapseDistancePx).coerceIn(0f, 1f)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                settle()
                return Velocity.Zero
            }
        }

    /** Animates to whichever state is nearer (interrupted by the next touch). */
    suspend fun settle() {
        val target = if (fraction > SETTLE_THRESHOLD) 1f else 0f
        if (fraction != target) animate(fraction, target, animationSpec = Motion.gentle()) { value, _ -> fraction = value }
    }

    /** Back to the expanded chrome (section change, search, tapping the compact button). */
    suspend fun expand() {
        if (fraction != 0f) animate(fraction, 0f, animationSpec = Motion.gentle()) { value, _ -> fraction = value }
    }
}

@Composable
fun rememberLibraryChromeState(): LibraryChromeState {
    val distance = with(LocalDensity.current) { COLLAPSE_DISTANCE.toPx() }
    return remember(distance) { LibraryChromeState(distance) }
}
