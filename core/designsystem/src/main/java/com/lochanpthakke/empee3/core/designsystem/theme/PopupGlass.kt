package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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

val LocalPopupGlassOpacity = staticCompositionLocalOf { 0.5f }
internal val LocalPopupGlassInMotion = staticCompositionLocalOf<() -> Boolean> { { false } }
internal val LocalPopupGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** A coordinated optical mapping: clear still compresses brightness for readable ink. */
internal data class PopupGlassStyle(
    val opacity: Float,
    val blurDp: Int,
    val dim: Float,
    val lift: Float,
    val luminanceLimit: Float,
)

internal fun popupGlassStyle(
    value: Float,
    dark: Boolean,
): PopupGlassStyle {
    val amount = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f
    return PopupGlassStyle(
        opacity = if (dark) 0.04f + amount * 0.2f else 0.06f + amount * 0.2f,
        blurDp = (4f + amount * 10f).roundToInt(),
        dim = if (dark) 0.64f + amount * 0.06f else 0f,
        lift = if (dark) 0f else 0.6f + amount * 0.12f,
        luminanceLimit = if (dark) 0.14f - amount * 0.03f else 0.3f + amount * 0.12f,
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
        borderHighlight = 0x40FFFFFFL,
        borderShadow = 0x24000000L,
        artworkScrim = 0L,
        frostRadiusDp = style.blurDp,
        frostSaturation = 1f,
        frostDim = style.dim,
        frostLift = style.lift,
        lensBandDp = 24,
        lensStrengthDp = 12,
        lensDispersionDp = 0.25f,
        edgeLight = 0.08f,
        specular = if (dark) 0x06FFFFFFL else 0x0AFFFFFFL,
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

/** The same RenderEffect/lens path as in-window glass, mapped in screen coordinates. */
@Composable
fun Modifier.popupGlass(shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.luminance() > scheme.surface.luminance()
    val enabled = (LocalMenuGlassTokens.current ?: LocalGlassTokens.current) != null
    val backdrop = LocalPopupGlassBackdrop.current.takeIf { enabled && isBackdropBlurSupported }
    DisposableEffect(backdrop) {
        if (backdrop != null) backdrop.consumers++
        onDispose { if (backdrop != null) backdrop.consumers-- }
    }
    val tokens = popupGlassTokens(dark, LocalPopupGlassOpacity.current)
    // No optical support or disabled glass: keep the existing solid-surface fallback.
    if (backdrop == null) {
        val neutral = if (dark) Color(0xFF202020) else Color(0xFFF5F5F5)
        return popupShapeLayer(shape, 4.dp).drawBehind {
            drawOutline(shape.createOutline(size, layoutDirection, this), neutral)
        }
    }
    val style = popupGlassStyle(LocalPopupGlassOpacity.current, dark)
    return popupShapeLayer(shape, 5.dp).cachedPopupOptics(shape, tokens, style, dark, backdrop)
}
