package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Glass material tokens. Glass is a material, not a theme: when the Glass Effect
 * setting is on, [EmPee3Theme] provides tokens derived from the active theme's
 * own environment ([derivedFrom]); when it is off there are none and every glass
 * helper is a no-op, so components fall back to normal theme surfaces.
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
    /** Backdrop blur of glass surfaces (Android 12+, where a backdrop is available). */
    val frostRadiusDp: Int = 20,
    /** Soft light on the upper part of a glass surface (specular response). */
    val specular: Long = 0x14FFFFFFL,
    /** Shade along the lower part of a glass surface, for depth without a drop shadow. */
    val edgeShade: Long = 0x1A000000L,
    /** Soft shadow under floating (Strong) glass, drawn only outside the surface. */
    val floatShadow: Long = 0x40000000L,
) {
    fun backgroundColors(): List<Color> = backgroundGradient.map(::Color)

    fun panelFillColor(strong: Boolean): Color = Color(if (strong) panelFillStrong else panelFill)

    companion object {
        private const val OPAQUE = 0xFF000000L
        private const val RGB = 0x00FFFFFFL
        private const val NO_GLOW = 0x00000000L

        /**
         * Glass for a theme's environment. The background stays the theme's own
         * (no gradient, no glows, no extra colour); only floating surfaces change:
         * - dark themes: dark translucent panels in the theme's surface colour,
         *   a faint light rim;
         * - light themes: bright translucent panels, a white rim and a faint dark
         *   edge so they stay defined on light backgrounds.
         * Accent colours are untouched, so e.g. Cyberpunk keeps its palette.
         */
        fun derivedFrom(
            colors: EmPee3Colors,
            isDark: Boolean,
        ): GlassTokens {
            val background = colors.background or OPAQUE
            val surfaceRgb = colors.surface and RGB
            val backgroundRgb = colors.background and RGB
            return if (isDark) {
                GlassTokens(
                    backgroundGradient = listOf(background, background),
                    glowPrimary = NO_GLOW,
                    glowSecondary = NO_GLOW,
                    panelFill = 0x14FFFFFFL,
                    panelFillStrong = 0xB8000000L or surfaceRgb,
                    borderHighlight = 0x33FFFFFFL,
                    borderShadow = 0x0AFFFFFFL,
                    artworkScrim = 0xA6000000L or backgroundRgb,
                    specular = 0x14FFFFFFL,
                    edgeShade = 0x26000000L,
                    floatShadow = 0x59000000L,
                )
            } else {
                GlassTokens(
                    backgroundGradient = listOf(background, background),
                    glowPrimary = NO_GLOW,
                    glowSecondary = NO_GLOW,
                    panelFill = 0x99FFFFFFL,
                    panelFillStrong = 0xC7000000L or surfaceRgb,
                    borderHighlight = 0xCCFFFFFFL,
                    borderShadow = 0x14000000L,
                    artworkScrim = 0x99000000L or backgroundRgb,
                    specular = 0x59FFFFFFL,
                    edgeShade = 0x0F000000L,
                    floatShadow = 0x1F000000L,
                )
            }
        }
    }
}
