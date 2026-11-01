package com.erhu.erhu.core.designsystem.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

// Depth for the glass material: the floating shadow of Strong glass and the classic elevation shadow.

/** Floating depth of Strong glass: a soft shadow outside the shape (never under it). */
private val FLOAT_SHADOW_BLUR = 14.dp
private val FLOAT_SHADOW_OFFSET = 3.dp

/**
 * Soft shadow that makes Strong glass float. It is drawn only outside [shape]
 * (the inside is clipped away), so the translucent glass is never darkened by
 * its own shadow. Needs hardware blur of mask filters (Android 9+); skipped
 * below that rather than drawing a hard shadow.
 */
internal fun Modifier.glassFloatShadow(
    enabled: Boolean,
    shape: Shape,
    color: Color,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val surface = Path().apply { addOutline(outline) }
        val shadow = Path().apply { addPath(surface, Offset(0f, FLOAT_SHADOW_OFFSET.toPx())) }

        val blurPx = FLOAT_SHADOW_BLUR.toPx()
        val outerRect =
            Rect(
                -blurPx,
                -blurPx + FLOAT_SHADOW_OFFSET.toPx(),
                size.width + blurPx,
                size.height + blurPx + FLOAT_SHADOW_OFFSET.toPx(),
            )
        val cornerRadius =
            when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius
                else -> CornerRadius(size.height / 2f)
            }
        val outerPill =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = outerRect,
                        cornerRadius =
                            CornerRadius(
                                cornerRadius.x + blurPx,
                                cornerRadius.y + blurPx,
                            ),
                    ),
                )
            }

        val paint =
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        onDrawBehind {
            clipPath(outerPill) {
                clipPath(surface, ClipOp.Difference) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(shadow.asAndroidPath(), paint) }
                }
            }
        }
    }
}

/** Classic elevation shadow for opaque glass-framed content (e.g. Now Playing artwork). */
internal fun Modifier.glassElevation(
    enabled: Boolean,
    shape: Shape,
    glass: GlassTokens,
): Modifier =
    if (!enabled) {
        this
    } else {
        shadow(
            elevation = glass.panelElevationDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = ELEVATION_SHADOW_ALPHA),
            spotColor = Color.Black.copy(alpha = ELEVATION_SHADOW_ALPHA),
        )
    }

private const val ELEVATION_SHADOW_ALPHA = 0.35f
