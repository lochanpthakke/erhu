package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

@Stable
internal class GlassMenuAnchorState {
    var bounds by mutableStateOf(Rect.Zero)
    var active by mutableStateOf(false)
}

internal val LocalGlassMenuAnchor = staticCompositionLocalOf<GlassMenuAnchorState?> { null }

/** The button and popup share identity and window geometry, including the return journey. */
@Composable
fun GlassMenuAnchor(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val anchor = remember { GlassMenuAnchorState() }
    Box(modifier.onGloballyPositioned { anchor.bounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalGlassMenuAnchor provides anchor) { content() }
    }
}

@Composable
fun Modifier.glassMenuTrigger(): Modifier {
    val anchor = LocalGlassMenuAnchor.current
    return graphicsLayer { alpha = if (anchor?.active == true) 0f else 1f }
}
