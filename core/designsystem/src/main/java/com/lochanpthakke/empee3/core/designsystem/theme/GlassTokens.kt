package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Extra tokens for glassmorphism themes. Only themes that define them render
 * glass surfaces; for every other theme the glass helpers are no-ops, so their
 * appearance is untouched.
 *
 * Colors are ARGB longs like the rest of [EmPee3Colors].
 */
@Immutable
@Serializable
data class GlassTokens(
    /** Opaque app background, drawn top to bottom behind all screens. */
    val backgroundGradient: List<Long>,
    /** Soft light spots over the background (top-start, bottom-end). */
    val glowPrimary: Long,
    val glowSecondary: Long,
    /** Fill of ordinary glass panels (cards, rows of controls). */
    val panelFill: Long,
    /** Fill of panels that float over content (mini player). */
    val panelFillStrong: Long,
    /** Border gradient: bright top edge fading to a faint bottom edge. */
    val borderHighlight: Long,
    val borderShadow: Long,
    val borderWidthDp: Int = 1,
    val panelElevationDp: Int = 6,
    /** Blur applied to the Now Playing artwork backdrop on Android 12+. */
    val artworkBlurRadiusDp: Int = 40,
    /** Darkening over the artwork backdrop so text stays readable. */
    val artworkScrim: Long,
) {
    fun backgroundColors(): List<Color> = backgroundGradient.map(::Color)

    fun panelFillColor(strong: Boolean): Color = Color(if (strong) panelFillStrong else panelFill)
}
