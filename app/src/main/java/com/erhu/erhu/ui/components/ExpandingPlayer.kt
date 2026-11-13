package com.erhu.erhu.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.layout.layout
import com.erhu.erhu.core.designsystem.component.LocalBottomChromeHeight
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.PlayerTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The player as one continuous object (see [PlayerTransitionState]). From a
 * single progress value:
 *
 * - Now Playing sections reveal themselves smoothly on the progress timeline
 *   (revealWithPlayer);
 * - the carousel owns the complete travelling card, including its artwork.
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
        Box(modifier = Modifier.fillMaxSize()) {
            // No bottom chrome while the player owns the screen: only the gesture area.
            CompositionLocalProvider(LocalBottomChromeHeight provides navigationBar, content = content)
        }
    }
}

/** Now Playing is composed this long after a song is available (after app start settles). */
private const val WARM_UP_DELAY_MS = 1_500L
