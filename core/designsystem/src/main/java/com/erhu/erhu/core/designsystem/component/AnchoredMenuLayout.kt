package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.PopupGlassSurface
import com.erhu.erhu.core.designsystem.theme.popupShapeLayer
import kotlin.math.roundToInt

@Stable
private class MenuGeometry {
    var trigger by mutableStateOf(Rect.Zero)
    var panel by mutableStateOf(Rect.Zero)
    var viewport by mutableStateOf(Rect.Zero)
}

internal fun menuMorphRect(
    trigger: Rect,
    panel: Rect,
    fraction: Float,
): Rect = lerp(trigger, panel, fraction.coerceIn(0f, 1f))

/** Only the spring's excess scales the complete surface; the aperture and rows keep stable bounds. */
internal fun albumMenuSurfaceScale(progress: Float): Float = 1f + ((progress - 1f) * 0.5f).coerceIn(0f, 0.012f)

private class AnchoredMenuShape(
    private val geometry: MenuGeometry,
    private val progress: State<Float>,
    private val forContent: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect = menuMorphRect(geometry.trigger, geometry.panel, progress.value)
        val offset = if (forContent) geometry.panel.topLeft else geometry.viewport.topLeft
        val local = rect.translate(-offset)
        val startCorner = geometry.trigger.size.minDimension / 2f
        val corner = startCorner + (with(density) { 28.dp.toPx() } - startCorner) * progress.value.coerceIn(0f, 1f)
        return Outline.Rounded(RoundRect(local, CornerRadius(corner.coerceAtMost(local.size.minDimension / 2f))))
    }
}

/** Measure rows once at their final size; animate just the optical aperture between real endpoints. */
@Composable
internal fun AnchoredMenuLayout(
    anchor: Rect,
    progress: State<Float>,
    albumMotionPilot: Boolean = false,
    onWindowPosition: (IntOffset) -> Unit,
    drawTrigger: Boolean,
    dismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val geometry = remember { MenuGeometry() }
    val materialShape = remember { AnchoredMenuShape(geometry, progress, false) }
    val contentShape = remember { AnchoredMenuShape(geometry, progress, true) }
    Layout(
        modifier =
            if (albumMotionPilot) {
                Modifier.graphicsLayer {
                    val viewport = geometry.viewport
                    transformOrigin =
                        TransformOrigin(
                            ((geometry.trigger.center.x - viewport.left) / viewport.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                            ((geometry.trigger.center.y - viewport.top) / viewport.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        )
                    // Compose's duration scale also governs the spring: no separate decorative loop.
                    scaleX = albumMenuSurfaceScale(progress.value)
                    scaleY = scaleX
                }
            } else {
                Modifier
            },
        content = {
            PopupGlassSurface(
                Modifier.pointerInput(Unit) {
                    detectTapGestures { local ->
                        val current = menuMorphRect(geometry.trigger, geometry.panel, progress.value)
                        if (!current.contains(local + geometry.viewport.topLeft)) dismiss()
                    }
                },
                materialShape,
            ) { }
            Column(
                modifier
                    .width(IntrinsicSize.Max)
                    .popupShapeLayer(contentShape, opacity = { ((progress.value - 0.5f) / 0.4f).coerceIn(0f, 1f) })
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                content = content,
            )
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = if (drawTrigger) (1f - progress.value / 0.18f).coerceIn(0f, 1f) else 0f
                    },
            )
        },
    ) { measurables, constraints -> measureAnchoredMenu(measurables, constraints, anchor, geometry, onWindowPosition) }
}

private fun MeasureScope.measureAnchoredMenu(
    measurables: List<Measurable>,
    constraints: Constraints,
    anchor: Rect,
    geometry: MenuGeometry,
    onWindowPosition: (IntOffset) -> Unit,
): MeasureResult {
    val inset = 16.dp.roundToPx()
    val body =
        measurables[1].measure(
            Constraints(
                minWidth = minOf(240.dp.roundToPx(), constraints.maxWidth - inset * 2).coerceAtLeast(0),
                maxWidth = minOf(320.dp.roundToPx(), constraints.maxWidth - inset * 2).coerceAtLeast(0),
                maxHeight = minOf(600.dp.roundToPx(), constraints.maxHeight - inset * 2).coerceAtLeast(0),
            ),
        )
    val window = IntSize(constraints.maxWidth, constraints.maxHeight)
    val at =
        GlassMenuPositionProvider(inset, 4.dp.roundToPx()).calculatePosition(
            IntRect(anchor.left.roundToInt(), anchor.top.roundToInt(), anchor.right.roundToInt(), anchor.bottom.roundToInt()),
            window,
            layoutDirection,
            IntSize(body.width, body.height),
        )
    val panel = Rect(at.x.toFloat(), at.y.toFloat(), (at.x + body.width).toFloat(), (at.y + body.height).toFloat())
    val trigger =
        anchor.takeUnless { it.isEmpty } ?: Rect(panel.right - 44.dp.toPx(), panel.top, panel.right, panel.top + 44.dp.toPx())
    val viewport =
        Rect(
            minOf(trigger.left, panel.left),
            minOf(trigger.top, panel.top),
            maxOf(trigger.right, panel.right),
            maxOf(trigger.bottom, panel.bottom),
        ).inflate(inset.toFloat())
    geometry.trigger = trigger
    geometry.panel = panel
    geometry.viewport = viewport
    val material = measurables[0].measure(Constraints.fixed(viewport.width.roundToInt(), viewport.height.roundToInt()))
    val glyph = measurables[2].measure(Constraints.fixed(24.dp.roundToPx(), 24.dp.roundToPx()))
    onWindowPosition(IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()))
    return layout(viewport.width.roundToInt(), viewport.height.roundToInt()) {
        material.place(0, 0)
        body.place(at.x - viewport.left.roundToInt(), at.y - viewport.top.roundToInt())
        glyph.place(
            (trigger.center.x - viewport.left - glyph.width / 2f).roundToInt(),
            (trigger.center.y - viewport.top - glyph.height / 2f).roundToInt(),
        )
    }
}
