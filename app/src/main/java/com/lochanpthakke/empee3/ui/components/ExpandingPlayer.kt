package com.lochanpthakke.empee3.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.LocalBottomChromeHeight
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.feature.library.PlayerTransitionState
import kotlinx.coroutines.launch

/** The surface becomes opaque over the first part of the expansion (it covers the mini player). */
private const val SURFACE_FADE_SPAN = 0.15f

/** The surface's sides and bottom reach the screen edges over this part of the expansion. */
private const val EDGE_SPAN = 0.3f

/** Now Playing's content fades in over this part of the expansion, once the surface is large. */
private const val CONTENT_FADE_START = 0.35f
private const val CONTENT_FADE_SPAN = 0.5f

/** Corner radius of the hero artwork in Now Playing (Radius.large). */
private val HERO_CORNER = 16.dp

/** Corner radius of the mini player's artwork (Radius.small). */
private val MINI_CORNER = 8.dp

/**
 * The player as one continuous object (see [PlayerTransitionState]). From a
 * single progress value it draws:
 *
 * - the expanding surface: from the mini player's exact rectangle and capsule
 *   corners to the full screen, opaque in the theme's environment colour, and
 *   holding Now Playing, whose content fades in as the surface opens;
 * - the artwork: ONE image travelling and scaling from the mini player's artwork
 *   to Now Playing's hero artwork (both of those are hidden while it travels).
 *   It is the cached bitmap at the hero size; nothing is decoded per frame.
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
    val visible by remember(state) { derivedStateOf { state.progress.value > 0f } }
    if (!visible) return

    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val environment = EmPee3Glass.backdropColor(MaterialTheme.colorScheme.background)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = state.progress.value
                        val full = Rect(0f, 0f, size.width, size.height)
                        val mini = state.miniBounds.takeIf { it.width > 0f } ?: full
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
                        shape = ExpandingShape(bounds, lerp(mini.height / 2f, 0f, p))
                        clip = true
                        alpha = (p / SURFACE_FADE_SPAN).coerceAtMost(1f)
                    }.background(environment),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((state.progress.value - CONTENT_FADE_START) / CONTENT_FADE_SPAN).coerceIn(0f, 1f)
                        },
            ) {
                // No bottom chrome while the player owns the screen: only the gesture area.
                CompositionLocalProvider(LocalBottomChromeHeight provides navigationBar, content = content)
            }
        }
        if (song != null) TravellingArtwork(state, song)
    }
}

/**
 * The artwork while the player is between states: laid out once at the hero
 * size and moved/scaled with layer transforms from the mini artwork to the hero
 * artwork. At either end it is invisible and the real artwork shows.
 */
@Composable
private fun TravellingArtwork(
    state: PlayerTransitionState,
    song: Song,
) {
    val hero = state.heroArtworkBounds
    if (hero.width <= 0f) return
    val density = LocalDensity.current
    val heroSize = with(density) { DpSize(hero.width.toDp(), hero.height.toDp()) }
    val heroCorner = with(density) { HERO_CORNER.toPx() }
    val miniCorner = with(density) { MINI_CORNER.toPx() }
    SongArtwork(
        song = song,
        modifier =
            Modifier
                .size(heroSize)
                .graphicsLayer {
                    val p = state.progress.value
                    val mini = state.miniArtworkBounds
                    alpha = if (p > 0f && !state.isSettledOpen && mini.width > 0f) 1f else 0f
                    val target = lerp(mini, state.heroArtworkBounds, p)
                    val scale = target.width / hero.width
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = target.left
                    translationY = target.top
                    scaleX = scale
                    scaleY = scale
                    // Visual corner radius goes from the mini's to the hero's; undo the scale.
                    shape = RoundedCornerShape(lerp(miniCorner, heroCorner, p) / scale.coerceAtLeast(MIN_SCALE))
                    clip = true
                },
    )
}

private const val MIN_SCALE = 0.01f

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
