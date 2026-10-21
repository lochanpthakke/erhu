package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.lochanpthakke.empee3.core.common.perf.traced

/*
 * What a glass surface shows through itself, and when that is rendered again: the parts of
 * [glassPanel] that decide how much work a surface does per frame.
 */

/** What a surface shows through itself, for one cache of its drawing. */
internal class FrostBacking(
    val backdrop: GlassBackdrop?,
    val layer: GraphicsLayer?,
    val margin: Int,
    val environment: Color,
    val glass: GlassTokens,
) {
    /** Whether [layer] holds a rendering that can be shown again while the backdrop is frozen. */
    var rendered = false
}

/**
 * The backdrop as seen through the surface: drawn directly when it is already blurred; else the
 * frost layer, rendered again only when needed (the last rendering while the backdrop is frozen,
 * without reading the position, so a moving surface does not redraw).
 */
internal fun DrawScope.drawBacking(
    backing: FrostBacking,
    position: GlassPosition,
) {
    val backdrop = backing.backdrop ?: return
    val layer = backing.layer
    when {
        backdrop.preBlurred -> drawPreBlurred(backdrop, position.offset, backing.environment, backing.glass)
        layer == null -> Unit
        backdrop.frozen && backing.rendered -> drawLayer(layer)
        else -> backing.rendered = drawFrost(backdrop, layer, position.offset, backing.margin, backing.environment)
    }
}

/**
 * Glass over an already blurred backdrop: its region drawn directly (the source's own cached
 * rendering, no new layer), then the same brightness tone the frost applies (pulled towards
 * black by frostDim, towards white by frostLift).
 */
private fun DrawScope.drawPreBlurred(
    backdrop: GlassBackdrop,
    surfaceOffset: Offset,
    environment: Color,
    glass: GlassTokens,
) {
    if (!backdrop.hasContent || backdrop.isRecording) return
    backdrop.contentVersion
    val offset = surfaceOffset - backdrop.sourceOffset
    drawRect(environment)
    traced(backdrop.frostSection) { translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) } }
    if (glass.frostDim > 0f) drawRect(Color.Black.copy(alpha = glass.frostDim))
    if (glass.frostLift > 0f) drawRect(Color.White.copy(alpha = glass.frostLift))
}
