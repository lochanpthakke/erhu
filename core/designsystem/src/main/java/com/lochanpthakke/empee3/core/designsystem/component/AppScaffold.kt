package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassBackdrop
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassBackdrop
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop

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
    /** Extra top content padding inside the scroll container (overlay header). */
    val contentTop: Dp = 0.dp,
)

/**
 * Scrolling content continues underneath the bottom chrome (the floating
 * mini player and controls sit over it): the container takes 0dp bottom padding
 * and the chrome height becomes scroll content padding inside the list, so the
 * last item still scrolls fully above the player while content flows edge-to-edge.
 */
@Composable
fun PaddingValues.forScrollingContent(topOverlay: Boolean = false): ScrollingPadding {
    val top = calculateTopPadding()
    val bottom = calculateBottomPadding()
    val glass = EmPee3Glass.isActive
    // Content scrolls underneath the top bar when it is an OverlayHeader (every
    // theme) or a glass AppTopBar (glass on): the bar's height then becomes content
    // padding, so the first item is still fully visible.
    val underTop = topOverlay || glass
    return ScrollingPadding(
        container = PaddingValues(top = if (underTop) 0.dp else top, bottom = 0.dp),
        contentBottom = bottom,
        contentTop = if (underTop) top else 0.dp,
    )
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
    contentBackdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    /**
     * Whether the content is recorded as the backdrop of this screen's own glass bars. A screen
     * whose bars are not glass passes false: recording a whole screen nobody samples is waste.
     */
    contentIsBackdrop: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val bottomChrome = LocalBottomChromeHeight.current
    // Glass in this screen's bars frosts the screen's own content. Glass inside
    // the content frosts [contentBackdrop] instead (inherited by default, usually
    // none), so nothing ever samples the source it is drawn in.
    val screenBackdrop = rememberGlassBackdrop("screen")
    Scaffold(
        modifier = modifier,
        topBar = { CompositionLocalProvider(LocalGlassBackdrop provides screenBackdrop) { topBar() } },
        // A screen-specific bottom bar must sit above the shell's chrome, and its
        // own height then becomes the content's bottom padding.
        bottomBar =
            bottomBar?.let { bar ->
                {
                    CompositionLocalProvider(LocalGlassBackdrop provides screenBackdrop) {
                        Box(Modifier.padding(bottom = bottomChrome)) { bar() }
                    }
                }
            } ?: {},
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(bottom = if (bottomBar == null) bottomChrome else 0.dp),
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (contentIsBackdrop) Modifier.glassBackdropSource(screenBackdrop) else Modifier),
        ) {
            // Glass inside the content frosts [contentBackdrop] (e.g. Now Playing's
            // artwork), never the content's own source.
            CompositionLocalProvider(LocalGlassBackdrop provides contentBackdrop) {
                content(innerPadding)
            }
        }
    }
}
