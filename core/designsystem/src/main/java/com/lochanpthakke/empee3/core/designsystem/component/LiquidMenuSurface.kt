package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.theme.ArtworkColors
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.GlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel

/** Color grading only: extraction remains in the existing DominantColorExtractor. */
internal fun glassMenuColors(
    dark: Boolean,
    glassAlpha: Float?,
    artwork: ArtworkColors? = null,
    themeSurface: Color = if (dark) Color(0xFF161B21) else Color(0xFFF4F4F4),
): GlassMenuColors {
    val neutral = if (dark) Color(0xFF141414) else Color(0xFFF4F4F4)
    val ambient = artwork?.takeIf { glassAlpha != null }?.let { lerp(Color.Gray, lerp(it.primary, it.accentColor, 0.18f), 0.65f) }
    val base = if (ambient == null) lerp(neutral, themeSurface, 0.12f) else lerp(neutral, ambient, if (dark) 0.18f else 0.12f)
    val light = ambient?.let { lerp(Color.White, it, 0.28f) } ?: Color.White
    return GlassMenuColors(
        surface =
            base.copy(
                alpha =
                    if (glassAlpha == null) {
                        1f
                    } else if (dark) {
                        0.8f
                    } else {
                        0.74f
                    },
            ),
        content = if (dark) Color(0xFFF5F5F5) else Color(0xFF202124),
        reflection = light.copy(alpha = if (dark) 0.065f else 0.1f),
        edge = light.copy(alpha = if (dark) 0.28f else 0.5f),
    )
}

/** Reuses the media player's layered glass renderer; no per-frame bitmap, shader or backdrop copy. */
@Composable
internal fun LiquidMenuSurface(
    modifier: Modifier,
    glass: GlassTokens?,
    colors: GlassMenuColors,
    content: @Composable () -> Unit,
) {
    val spec = Motion.fade<Color>(Motion.NAVIGATION)
    val fill by animateColorAsState(colors.surface, spec, label = "Menu ambient fill")
    val reflection by animateColorAsState(colors.reflection, spec, label = "Menu ambient reflection")
    val edge by animateColorAsState(colors.edge, spec, label = "Menu ambient edge")
    val material =
        glass?.copy(
            // The page/theme tint may describe a browsed album. Only the playback palette colors this menu.
            tint = 0L,
            specular = reflection.argbLong(),
            borderHighlight = edge.argbLong(),
            edgeShade = Color.Black.copy(alpha = 0.075f).argbLong(),
            floatShadow = Color.Black.copy(alpha = 0.2f).argbLong(),
        )
    val shape = RoundedCornerShape(28.dp)
    CompositionLocalProvider(LocalGlassTokens provides material) {
        Surface(
            modifier = modifier.glassPanel(shape, level = GlassLevel.Strong, elevated = false, backdrop = null, fill = fill),
            shape = shape,
            color = if (material == null) fill else Color.Transparent,
            contentColor = colors.content,
            tonalElevation = 0.dp,
            shadowElevation = if (material == null) 4.dp else 0.dp,
            border = if (material == null) BorderStroke(0.5.dp, colors.content.copy(alpha = 0.16f)) else null,
            content = content,
        )
    }
}

private fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Keep the theme's error hue, grading it toward the legible ink for translucent backgrounds. */
internal fun menuDestructiveColor(
    error: Color,
    content: Color,
): Color = lerp(error, content, if (content.luminance() < 0.5f) 0.85f else 0.7f)
