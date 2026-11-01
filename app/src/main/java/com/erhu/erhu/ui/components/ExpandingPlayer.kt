package com.erhu.erhu.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.erhu.erhu.core.designsystem.component.LocalBottomChromeHeight
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.PlayerTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The surface's sides and bottom reach the screen edges over this part of the expansion. */
private const val EDGE_SPAN = 0.3f

/**
 * The player as one continuous object (see [PlayerTransitionState]). From a
 * single progress value it draws:
 *
 * - the expanding surface: from the mini player's measured thumbnail and its
 *   corners to the full screen, opaque in the theme's environment colour, and
 *   holding Now Playing, whose sections reveal themselves on the same progress
 *   (revealWithPlayer), so nothing here fades the content a second time;
 * - the existing carousel owns the complete travelling card, including its artwork.
 *   There is no separately loaded or animated artwork overlay.
 *
 * Everything is read in the draw phase, so dragging or animating the player
 * never recomposes it. Back collapses it (inner handlers such as lyrics first).
 */
@Composable
fun ExpandingPlayer(
    state: PlayerTransitionState,
    song: Song?,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Re-registered every time the player opens: the navigation host re-registers its own
    // handler on each navigation, and the most recently registered handler wins. Without
    // this, Back on a detail page popped the page behind the open player.
    key(state.isOpen) { BackHandler(enabled = state.isOpen) { scope.launch { state.collapse() } } }
    val visible by remember(state) { derivedStateOf { state.fraction > 0f } }
    // Now Playing is composed ahead of the first open (shortly after there is a song, not
    // during app start) and stays composed while collapsed, only unplaced: opening then
    // moves content that already exists, instead of building the whole screen (and
    // decoding its artwork) in the first frame of the motion.
    var warm by remember { mutableStateOf(false) }
    val hasSong = song != null
    LaunchedEffect(hasSong) {
        if (hasSong) {
            delay(WARM_UP_DELAY_MS)
            warm = true
        }
    }
    if (!visible && !warm) return

    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val environment = ErhuGlass.backdropColor(MaterialTheme.colorScheme.background)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Collapsed: measured and composed but not placed, so it is neither drawn nor
                // touchable. Read in the placement phase: no recomposition when it toggles.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = state.fraction
                        val full = Rect(0f, 0f, size.width, size.height)
                        val thumbnail = state.miniArtworkBounds
                        val mini = thumbnail.takeIf { it.width > 0f } ?: state.miniBounds.takeIf { it.width > 0f } ?: full
                        // The sides and bottom reach the screen edges early; the top edge
                        // follows the progress (the finger), like a sheet rising from the mini player.
                        val edges = (p / EDGE_SPAN).coerceAtMost(1f)
                        val bounds =
                            Rect(
                                left = lerp(mini.left, full.left, edges),
                                top = lerp(mini.top, full.top, p),
                                right = lerp(mini.right, full.right, edges),
                                bottom = lerp(mini.bottom, full.bottom, edges),
                            )
                        val corner = if (thumbnail.width > 0f) Radius.small.toPx() else mini.height / 2f
                        shape = ExpandingShape(bounds, lerp(corner, 0f, p))
                        clip = true
                        // Sections own their fades. Fading this parent would also dim
                        // the travelling card, then flash its artwork back to full
                        // opacity when the mini player takes over at zero.
                    }.background(environment),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // No bottom chrome while the player owns the screen: only the gesture area.
                CompositionLocalProvider(LocalBottomChromeHeight provides navigationBar, content = content)
            }
        }
    }
}

/** Now Playing is composed this long after a song is available (after app start settles). */
private const val WARM_UP_DELAY_MS = 1_500L

/** A rounded rectangle at arbitrary [bounds] inside the layer (the expanding surface). */
private class ExpandingShape(
    private val bounds: Rect,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}
