// Geometry of the generated art (spline weights, half sizes): fixed by the drawing, not tunables.
@file:Suppress("MagicNumber")

package com.erhu.erhu.feature.library.mix

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A smart mix's generated cover ([MixArt]), drawn with Compose primitives. The art is generated
 * once per mix and its paths once per size; nothing animates, so a cover costs nothing between
 * frames. Give it a square size (and clip it to the card's shape).
 */
@Composable
fun MixArtwork(
    kind: MixKind,
    modifier: Modifier = Modifier,
) {
    val art = remember(kind) { MixArt.generate(kind) }
    MixArtCanvas(art = art, description = "${kind.title} cover", modifier = modifier)
}

/** Draws already composed [art] (a cover, or a banner composed for its shape) at whatever size it is given. */
@Composable
fun MixArtCanvas(
    art: MixArt,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                .drawWithCache { drawArt(art) },
    )
}

private fun CacheDrawScope.drawArt(art: MixArt): DrawResult {
    val side = size.minDimension
    val steps = art.layers.map { layer -> prepare(layer, size, side) }
    return onDrawBehind { clipRect { steps.forEach { it() } } }
}

/** Builds what [layer] needs at this size once, returning the draw call. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun prepare(
    layer: ArtLayer,
    size: Size,
    side: Float,
): DrawScope.() -> Unit {
    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * size.width, y * size.height)
    return when (layer) {
        is ArtLayer.Backdrop -> {
            val angle = Math.toRadians(layer.angleDeg.toDouble())
            val dx = cos(angle).toFloat() / 2
            val dy = sin(angle).toFloat() / 2
            val brush = Brush.linearGradient(layer.colors.map(::Color), start = p(0.5f - dx, 0.5f - dy), end = p(0.5f + dx, 0.5f + dy))
            ({ drawRect(brush) })
        }
        is ArtLayer.Glow -> {
            val center = p(layer.x, layer.y)
            val color = Color(layer.color)
            val brush = Brush.radialGradient(listOf(color.copy(alpha = layer.alpha), color.copy(alpha = 0f)), center, layer.radius * side)
            ({ drawCircle(brush, layer.radius * side, center) })
        }
        is ArtLayer.Blob -> {
            val path = wobblyPath(p(layer.x, layer.y), layer.radius * side, layer.wobble, layer.rotation)
            val color = Color(layer.color).copy(alpha = layer.alpha)
            ({ drawPath(path, color) })
        }
        is ArtLayer.Ring -> {
            val path = wobblyPath(p(layer.x, layer.y), layer.radius * side, layer.wobble, 0f)
            val color = Color(layer.color).copy(alpha = layer.alpha)
            val stroke = Stroke(width = layer.stroke * side, cap = StrokeCap.Round, join = StrokeJoin.Round)
            ({ drawPath(path, color, style = stroke) })
        }
        is ArtLayer.Ribbon -> {
            val path = smoothPath(layer.points.chunked(2).map { (x, y) -> p(x, y) })
            val color = Color(layer.color).copy(alpha = layer.alpha)
            val stroke = Stroke(width = layer.width * side, cap = StrokeCap.Round, join = StrokeJoin.Round)
            ({ drawPath(path, color, style = stroke) })
        }
        is ArtLayer.Disc -> {
            val center = p(layer.x, layer.y)
            val color = Color(layer.color).copy(alpha = layer.alpha)
            ({ drawCircle(color, layer.radius * side, center) })
        }
        is ArtLayer.Sector -> {
            val radius = layer.radius * side
            val center = p(layer.x, layer.y)
            val color = Color(layer.color).copy(alpha = layer.alpha)
            (
                {
                    drawArc(
                        color = color,
                        startAngle = layer.startDeg,
                        sweepAngle = layer.sweepDeg,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                    )
                }
            )
        }
        is ArtLayer.Shard -> {
            val path =
                Path().apply {
                    layer.points.chunked(2).forEachIndexed { i, (x, y) ->
                        val point = p(x, y)
                        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
            val color = Color(layer.color).copy(alpha = layer.alpha)
            ({ drawPath(path, color) })
        }
        is ArtLayer.Block -> {
            val topLeft = p(layer.left, layer.top)
            val bottomRight = p(layer.right, layer.bottom)
            val corner =
                androidx.compose.ui.geometry
                    .CornerRadius(layer.corner * side)
            val color = Color(layer.color).copy(alpha = layer.alpha)
            ({ drawRoundRect(color, topLeft, Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y), corner) })
        }
    }
}

/** A closed curve around [center] whose radius follows [wobble] smoothly (cubic segments). */
private fun wobblyPath(
    center: Offset,
    radius: Float,
    wobble: List<Float>,
    rotationDeg: Float,
): Path {
    val n = wobble.size.coerceAtLeast(3)
    val rotation = Math.toRadians(rotationDeg.toDouble())
    val points =
        List(n) { i ->
            val angle = rotation + 2 * PI * i / n
            val r = radius * wobble[i % wobble.size]
            Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
        }
    return smoothPath(points, closed = true)
}

/** A Catmull-Rom spline through [points] as cubic Béziers: smooth, passing through every point. */
private fun smoothPath(
    points: List<Offset>,
    closed: Boolean = false,
): Path {
    val path = Path()
    if (points.size < 2) return path
    val n = points.size

    fun at(i: Int) = if (closed) points[Math.floorMod(i, n)] else points[i.coerceIn(0, n - 1)]
    path.moveTo(points[0].x, points[0].y)
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    if (closed) path.close()
    return path
}
