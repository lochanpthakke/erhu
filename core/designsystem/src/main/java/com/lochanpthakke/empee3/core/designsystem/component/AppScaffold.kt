package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass

/**
 * Height of the app shell's bottom chrome (mini player + system navigation
 * bar), measured by the shell. Screens draw edge to edge and use this as
 * their bottom content padding; they never add system insets themselves.
 */
val LocalBottomChromeHeight = compositionLocalOf { 0.dp }

/** [AppScaffold] padding split for a scrolling container. */
@Immutable
data class ScrollingPadding(
    /** Padding for the scroll container itself. */
    val container: PaddingValues,
    /** Extra bottom content padding inside the scroll container. */
    val contentBottom: Dp,
)

/**
 * Under a glass theme scrolling content continues underneath the bottom chrome
 * (the mini player is a translucent layer over it): the container takes only the
 * top padding and the chrome height becomes scroll content padding, so the last
 * item still scrolls fully above the player. Other themes keep content ending
 * at the top edge of the chrome.
 */
@Composable
fun PaddingValues.forScrollingContent(): ScrollingPadding =
    if (EmPee3Glass.isActive) {
        ScrollingPadding(container = PaddingValues(top = calculateTopPadding()), contentBottom = calculateBottomPadding())
    } else {
        ScrollingPadding(container = this, contentBottom = 0.dp)
    }

/**
 * Screen-level scaffold. Inset ownership:
 * - top: the [topBar] (an [AppTopBar]) pads itself for the status bar;
 * - bottom: the shell's measured bottom chrome via [LocalBottomChromeHeight];
 * - nothing else adds system insets, so they are never applied twice.
 *
 * `content` receives padding that already covers both. Apply it as padding
 * so content ends exactly at the top of the mini player: nothing scrolls
 * underneath it (it can be translucent) and the last item stays reachable.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    val bottomChrome = LocalBottomChromeHeight.current
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        // A screen-specific bottom bar must sit above the shell's chrome, and its
        // own height then becomes the content's bottom padding.
        bottomBar =
            bottomBar?.let { bar ->
                { Box(Modifier.padding(bottom = bottomChrome)) { bar() } }
            } ?: {},
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(bottom = if (bottomBar == null) bottomChrome else 0.dp),
        content = content,
    )
}
