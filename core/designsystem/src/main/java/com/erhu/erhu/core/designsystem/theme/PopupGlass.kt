package com.erhu.erhu.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val LocalPopupGlassOpacity = compositionLocalOf { 0.5f }
internal val LocalPopupGlassInMotion = staticCompositionLocalOf<() -> Boolean> { { false } }
internal val LocalPopupGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** A coordinated optical mapping: clear still compresses brightness for readable ink. */
internal data class PopupGlassStyle(
    val opacity: Float,
    val blurDp: Int,
    val dim: Float,
    val lift: Float,
    val luminanceLimit: Float,
    val refractionDp: Float,
    val reflectionAlpha: Float,
    val edgeAlpha: Float,
    val lensHighlight: Float,
)

internal fun popupGlassStyle(
    value: Float,
    dark: Boolean,
): PopupGlassStyle {
    val amount = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f
    val frost = 1f - amount
    return PopupGlassStyle(
        opacity = if (dark) 0.04f + frost * 0.56f else 0.06f + frost * 0.56f,
        blurDp = (4f + frost * frost * 28f).roundToInt(),
        dim = if (dark) 0.64f + frost * 0.16f else 0f,
        lift = if (dark) 0f else 0.6f + frost * 0.22f,
        luminanceLimit = if (dark) 0.14f - frost * 0.09f else 0.28f + frost * 0.42f,
        refractionDp = 3f + amount * 7f,
        reflectionAlpha = 0.012f + amount * 0.043f,
        edgeAlpha = 0.1f + amount * 0.23f,
        lensHighlight = 0.045f + amount * 0.095f,
    )
}

internal fun popupGlassTokens(
    dark: Boolean,
    value: Float,
): GlassTokens {
    val style = popupGlassStyle(value, dark)
    val neutral = if (dark) Color.Black else Color.White
    val fill = neutral.copy(alpha = style.opacity).toArgb().toLong() and 0xFFFFFFFFL
    return GlassTokens(
        backgroundGradient = listOf(if (dark) 0xFF161616L else 0xFFF5F5F5L),
        glowPrimary = 0L,
        glowSecondary = 0L,
        panelFill = fill,
        panelFillStrong = fill,
        borderHighlight =
            Color.White
                .copy(alpha = style.edgeAlpha)
                .toArgb()
                .toLong() and 0xFFFFFFFFL,
        borderShadow = 0x24000000L,
        artworkScrim = 0L,
        frostRadiusDp = style.blurDp,
        frostSaturation = 1f,
        frostDim = style.dim,
        frostLift = style.lift,
        lensBandDp = 24,
        lensStrengthDp = style.refractionDp.roundToInt(),
        lensDispersionDp = 0f,
        edgeLight = 0.08f,
        specular =
            Color.White
                .copy(alpha = style.reflectionAlpha)
                .toArgb()
                .toLong() and 0xFFFFFFFFL,
        edgeShade = 0x08000000L,
        floatShadow = 0x24000000L,
        tint = 0L,
    )
}

/** Shared neutral material for transient surfaces; does not alter permanent chrome. */
@Composable
fun PopupGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    PopupGlassTheme {
        Box(modifier.popupGlass(shape), propagateMinConstraints = true) { content() }
    }
}

@Composable
internal fun PopupGlassTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.luminance() > scheme.surface.luminance()
    val ink = if (dark) Color(0xFFF9F9F9) else Color(0xFF171717)
    val neutral = if (dark) Color(0xFF202020) else Color(0xFFF5F5F5)
    val popupScheme =
        scheme.copy(
            surface = neutral,
            surfaceContainer = neutral,
            surfaceContainerHigh = neutral,
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.9f),
            outlineVariant = ink.copy(alpha = 0.16f),
        )
    MaterialTheme(colorScheme = popupScheme) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            content()
        }
    }
}

/** Transient native windows sample the app/window backdrop, excluding their own contents. */
@Composable
fun Modifier.popupGlass(shape: Shape = RoundedCornerShape(28.dp)): Modifier =
    sharedLiquidGlass(shape, LocalPopupGlassBackdrop.current, (LocalMenuGlassTokens.current ?: LocalGlassTokens.current) != null)

/** Permanent floating navigation samples screen content, never the shell that contains itself. */
@Composable
fun Modifier.liquidGlass(shape: Shape): Modifier {
    if (LocalGlassTokens.current == null) return this
    return sharedLiquidGlass(shape, LocalGlassBackdrop.current, true)
}

@Composable
private fun Modifier.sharedLiquidGlass(
    shape: Shape,
    source: GlassBackdrop?,
    enabled: Boolean,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.luminance() > scheme.surface.luminance()
    val backdrop = source.takeIf { enabled && isBackdropBlurSupported }
    DisposableEffect(backdrop) {
        if (backdrop != null) backdrop.consumers++
        onDispose { if (backdrop != null) backdrop.consumers-- }
    }
    val strength = LocalPopupGlassOpacity.current
    val tokens = popupGlassTokens(dark, strength)
    if (backdrop == null) {
        val neutral = if (dark) Color(0xFF202020) else Color(0xFFF5F5F5)
        return popupShapeLayer(shape, 4.dp).drawBehind {
            drawOutline(shape.createOutline(size, layoutDirection, this), neutral)
        }
    }
    return popupShapeLayer(shape, 5.dp).cachedPopupOptics(shape, tokens, popupGlassStyle(strength, dark), dark, backdrop)
}
