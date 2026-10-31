package com.lochanpthakke.empee3.core.designsystem.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Neutral luminance guard. Dark media passes through unchanged; only highlights are compressed. */
private const val POPUP_CONTRAST_SHADER = """
    uniform shader content;
    uniform float limit;
    uniform float dark;
    float3 decodeSrgb(float3 c) {
        return mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
    }
    float3 encodeSrgb(float3 c) {
        return mix(c * 12.92, 1.055 * pow(max(c, 0.0), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
    }
    half4 main(float2 coord) {
        half4 pixel = content.eval(coord);
        float3 rgb = decodeSrgb(float3(pixel.rgb));
        float luminance = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        if (dark > 0.5 && luminance > limit) rgb *= limit / max(luminance, 0.0001);
        if (dark < 0.5 && luminance < limit) rgb = mix(rgb, float3(1.0), (limit - luminance) / max(1.0 - luminance, 0.0001));
        return half4(half3(encodeSrgb(rgb)), pixel.a);
    }
"""

/** Resolve stateful aperture geometry inside the layer observer, bypassing Shape identity caches. */
@Composable
internal fun Modifier.popupShapeLayer(
    shape: Shape,
    elevation: Dp = 0.dp,
    opacity: () -> Float = { 1f },
): Modifier {
    val direction = LocalLayoutDirection.current
    return graphicsLayer {
        val outline = shape.createOutline(size, direction, this)
        this.shape = ResolvedPopupShape(outline)
        clip = true
        shadowElevation = elevation.toPx()
        ambientShadowColor = Color.Black.copy(alpha = 0.12f)
        spotShadowColor = Color.Black.copy(alpha = 0.18f)
        alpha = opacity()
    }
}

private class ResolvedPopupShape(
    private val outline: Outline,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = outline
}

/** Two retained GPU layers: cached diffusion, then the changing optical aperture. No bitmap readback. */
@Composable
internal fun Modifier.cachedPopupOptics(
    shape: Shape,
    glass: GlassTokens,
    style: PopupGlassStyle,
    dark: Boolean,
    backdrop: GlassBackdrop,
): Modifier {
    var position by remember { mutableStateOf(Offset.Zero) }
    val moving = LocalPopupGlassInMotion.current
    val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { RuntimeShader(ENVIRONMENT_SHADER) } else null
    val contrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { RuntimeShader(POPUP_CONTRAST_SHADER) } else null
    return onGloballyPositioned { position = it.positionOnScreen() }.drawWithCache {
        val cache = popupOpticalCache(glass, style, dark, shader, contrast)
        var lastReady: Boolean? = null
        onDrawBehind {
            // Subscribe before the readiness guard so the first source recording invalidates this draw.
            backdrop.contentVersion
            val ready = backdrop.hasContent && !backdrop.isRecording
            if (lastReady != ready) {
                if (Log.isLoggable(
                        "Empee3Popup",
                        Log.DEBUG,
                    )
                ) {
                    Log.d("Empee3Popup", "backdropReady=$ready viewport=$size source=${backdrop.layer.size}")
                }
                lastReady = ready
            }
            val outline = shape.createOutline(size, layoutDirection, this)
            if (ready) {
                cache.draw(this, outline, backdrop, position, moving())
            } else {
                drawOutline(outline, if (dark) Color(0xFF202020) else Color(0xFFF5F5F5))
            }
        }
    }
}

private fun CacheDrawScope.popupOpticalCache(
    glass: GlassTokens,
    style: PopupGlassStyle,
    dark: Boolean,
    shader: RuntimeShader?,
    contrast: RuntimeShader?,
): CachedPopupOptics {
    val blur = style.blurDp.dp.toPx()
    val margin = ceil(blur * 2f + 24.dp.toPx()).toInt()
    val layerSize = IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)
    val diffusion =
        obtainGraphicsLayer().apply {
            renderEffect =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && contrast != null) {
                    popupContrastEffect(contrast, blur, style.luminanceLimit, dark)
                } else {
                    glassOptics(blur, 1f, style.dim, style.lift, null)
                }
        }
    val optical = obtainGraphicsLayer().apply { topLeft = IntOffset(-margin, -margin) }
    val reflection =
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.022f else 0.035f), Color.Transparent),
            endY = size.height * 0.32f,
        )
    val rim =
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f)),
        )
    return CachedPopupOptics(diffusion, optical, layerSize, margin, glass, shader, reflection, rim)
}

private class CachedPopupOptics(
    val diffusion: GraphicsLayer,
    val optical: GraphicsLayer,
    val layerSize: IntSize,
    val margin: Int,
    val glass: GlassTokens,
    val shader: RuntimeShader?,
    val reflection: Brush,
    val rim: Brush,
) {
    private val radii = FloatArray(4)
    private var version = -1
    private var recordedPosition = Offset.Unspecified
    private var aperture = Rect.Zero
    private var rendered = false

    fun draw(
        scope: DrawScope,
        outline: Outline,
        backdrop: GlassBackdrop,
        position: Offset,
        moving: Boolean,
    ) = with(scope) {
        val current = backdrop.contentVersion
        val offset = position - backdrop.sourceOffset
        val sourceChanged = version != current || recordedPosition != offset
        if (!rendered || (!moving && sourceChanged)) updateBacking(backdrop, offset, current)
        val rounded = outline as? Outline.Rounded
        val rect = rounded?.roundRect?.let { Rect(it.left, it.top, it.right, it.bottom) } ?: Rect(Offset.Zero, size)
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && aperture != rect) {
            updateLens(rect, rounded, this)
        }
        drawLayer(optical)
        drawOutline(outline, Color(glass.panelFill))
        drawOutline(outline, reflection)
        drawOutline(outline, rim, style = Stroke(1.dp.toPx()))
    }

    private fun DrawScope.updateBacking(
        backdrop: GlassBackdrop,
        offset: Offset,
        current: Int,
    ) {
        diffusion.record(layerSize) {
            drawRect(Color(glass.backgroundGradient.first()))
            translate(margin - offset.x, margin - offset.y) { drawLayer(backdrop.layer) }
        }
        optical.record(layerSize) { drawLayer(diffusion) }
        rendered = true
        version = current
        recordedPosition = offset
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateLens(
        rect: Rect,
        rounded: Outline.Rounded?,
        scope: DrawScope,
    ) = with(scope) {
        radii.fill(rounded?.roundRect?.topLeftCornerRadius?.x ?: 0f)
        val band = minOf(rect.size.minDimension * 0.22f, 18.dp.toPx())
        val lens =
            EnvironmentLens(
                origin = rect.topLeft + Offset(margin.toFloat(), margin.toFloat()),
                size = rect.size,
                layerSize = Size(layerSize.width.toFloat(), layerSize.height.toFloat()),
                cornerRadii = radii,
                band = band,
                strength = 9.dp.toPx(),
                capBoost = 0.35f,
                dispersion = 0f,
                highlight = 0.09f,
                rimWidth = band * 0.3f,
                shade = 0.06f,
                environment = Color(glass.backgroundGradient.first()),
            )
        optical.renderEffect = environmentLensEffect(lens, checkNotNull(shader)).asComposeRenderEffect()
        aperture = rect
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun popupContrastEffect(
    shader: RuntimeShader,
    blur: Float,
    limit: Float,
    dark: Boolean,
): androidx.compose.ui.graphics.RenderEffect {
    shader.setFloatUniform("limit", limit)
    shader.setFloatUniform("dark", if (dark) 1f else 0f)
    val tone = RenderEffect.createRuntimeShaderEffect(shader, "content")
    return RenderEffect.createChainEffect(tone, blurAndTone(blur, 1f, 0f, 0f)).asComposeRenderEffect()
}
