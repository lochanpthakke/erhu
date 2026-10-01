package com.lochanpthakke.empee3.core.designsystem.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * What glass surfaces see through themselves. Content marked with
 * [glassBackdropSource] is recorded into a [GraphicsLayer] while it draws (a
 * display list, not a bitmap), and [glassPanel] re-draws just its own region of
 * that recording, blurred. No CPU pixel work, no extra bitmaps, nothing per frame
 * unless the content itself redraws.
 *
 * A glass surface must never sit inside the source it samples. The app wires
 * sources so that glass is always a sibling drawn after its source:
 * - shell screen content → mini player, library navigation and search;
 * - a screen's content ([AppScaffold]) → that screen's top/bottom bars;
 * - the whole shell → the navigation drawer;
 * - Now Playing's artwork backdrop → Now Playing's controls.
 * Ordinary content (lists, cards) gets no backdrop, so it never blurs.
 */
@Stable
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    /** Source position in the root, to map a glass surface onto the recording. */
    internal var sourceOffset by mutableStateOf(Offset.Zero)

    /** Bumped whenever the source re-records, so glass surfaces redraw with it. */
    internal var contentVersion by mutableIntStateOf(0)

    internal var hasContent = false

    /** Guard against a misplaced glass surface sampling the source it is drawn in. */
    internal var isRecording = false
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** The backdrop glass surfaces blur by default; null means tint-only glass. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Backdrop blur needs RenderEffect (Android 12+); older devices get tint-only glass. */
internal val isBackdropBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Marks this content as a [GlassBackdrop] source. It still draws normally; the
 * recording only happens while the Glass Effect is on and blur is supported, so
 * with Glass off this is a no-op.
 */
@Composable
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier {
    if (LocalGlassTokens.current == null || !isBackdropBlurSupported) return this
    return this
        .onGloballyPositioned { backdrop.sourceOffset = it.positionInRoot() }
        .drawWithContent {
            backdrop.isRecording = true
            backdrop.layer.record { this@drawWithContent.drawContent() }
            backdrop.isRecording = false
            drawLayer(backdrop.layer)
            backdrop.hasContent = true
            // Notify glass readers without making this draw observe its own counter.
            Snapshot.withoutReadObservation { backdrop.contentVersion++ }
        }
}
