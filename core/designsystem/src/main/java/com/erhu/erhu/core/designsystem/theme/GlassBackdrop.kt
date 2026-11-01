package com.erhu.erhu.core.designsystem.theme

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
import androidx.compose.ui.layout.positionOnScreen
import com.erhu.erhu.core.common.perf.traced

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
    /** Names this backdrop in traces: when it re-records, and which surfaces frost it again. */
    name: String = "backdrop",
    /**
     * The source is already a strong blur (Now Playing's artwork backdrop): glass over it shows
     * its region directly, toned, without blurring a blur again (no offscreen pass per surface).
     */
    internal val preBlurred: Boolean = false,
    internal val screenCoordinates: Boolean = false,
) {
    internal val sourceSection = "GlassSource:$name"
    internal val frostSection = "GlassFrost:$name"
    internal val lensSection = "GlassLens:$name"

    /** Source position in the root, to map a glass surface onto the recording. */
    internal var sourceOffset by mutableStateOf(Offset.Zero)

    /** Bumped whenever the source re-records, so glass surfaces redraw with it. */
    internal var contentVersion by mutableIntStateOf(0)

    internal var consumers by mutableIntStateOf(0)

    internal var hasContent = false

    /** Guard against a misplaced glass surface sampling the source it is drawn in. */
    internal var isRecording = false

    /**
     * While true, glass over this backdrop keeps what it last rendered instead of rendering
     * again as it moves (e.g. the shell's chrome while the player opens over it and slides it
     * away): no blur or lens pass per frame for surfaces that are leaving or covered. When it
     * turns false again, every surface renders afresh.
     */
    var frozen by mutableStateOf(false)
}

@Composable
fun rememberGlassBackdrop(
    name: String = "backdrop",
    preBlurred: Boolean = false,
    screenCoordinates: Boolean = false,
): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer, name, preBlurred, screenCoordinates) { GlassBackdrop(layer, name, preBlurred, screenCoordinates) }
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
fun Modifier.glassBackdropSource(
    backdrop: GlassBackdrop,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || LocalGlassTokens.current == null || !isBackdropBlurSupported) return this
    return this
        .onGloballyPositioned { backdrop.sourceOffset = if (backdrop.screenCoordinates) it.positionOnScreen() else it.positionInRoot() }
        .drawWithContent {
            backdrop.isRecording = true
            traced(backdrop.sourceSection) { backdrop.layer.record { this@drawWithContent.drawContent() } }
            backdrop.isRecording = false
            drawLayer(backdrop.layer)
            backdrop.hasContent = true
            // Notify glass readers without making this draw observe its own counter.
            Snapshot.withoutReadObservation { backdrop.contentVersion++ }
        }
}
