package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class EmPee3Colors(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val bevelLight: Long,
    val bevelDark: Long,
    val lcdBackground: Long,
    val lcdText: Long,
    val lcdGlow: Long,
    val cardBackground: Long,
    val glassScrim: Long,
) {
    fun getPrimaryColor(): Color = Color(primary)
    fun getOnPrimaryColor(): Color = Color(onPrimary)
    fun getPrimaryContainerColor(): Color = Color(primaryContainer)
    fun getOnPrimaryContainerColor(): Color = Color(onPrimaryContainer)
    fun getSecondaryColor(): Color = Color(secondary)
    fun getOnSecondaryColor(): Color = Color(onSecondary)
    fun getBackgroundColor(): Color = Color(background)
    fun getOnBackgroundColor(): Color = Color(onBackground)
    fun getSurfaceColor(): Color = Color(surface)
    fun getOnSurfaceColor(): Color = Color(onSurface)
    fun getSurfaceVariantColor(): Color = Color(surfaceVariant)
    fun getOnSurfaceVariantColor(): Color = Color(onSurfaceVariant)
    fun getOutlineColor(): Color = Color(outline)
    fun getBevelLightColor(): Color = Color(bevelLight)
    fun getBevelDarkColor(): Color = Color(bevelDark)
    fun getLcdBackgroundColor(): Color = Color(lcdBackground)
    fun getLcdTextColor(): Color = Color(lcdText)
    fun getLcdGlowColor(): Color = Color(lcdGlow)
    fun getCardBackgroundColor(): Color = Color(cardBackground)
    fun getGlassScrimColor(): Color = Color(glassScrim)
}
