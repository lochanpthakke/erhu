package com.lochanpthakke.empee3.feature.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Test

class CardOverlayTest {
    private val light = Color.White.copy(alpha = 0.1f)
    private val shade = Color.Black.copy(alpha = 0.16f)

    @Test
    fun aBandIsLinearBetweenItsStopsAndHeldOutside() {
        val band = VerticalBand(10f to Color.Black.copy(alpha = 0f), 20f to Color.Black)
        assertEquals(0f, band.at(0f).alpha, 0.001f)
        assertEquals(1f, band.at(30f).alpha, 0.001f)
        assertEquals(0.5f, band.at(15f).alpha, 0.02f)
    }

    @Test
    fun overlappingBandsCompositeInDrawingOrder() {
        val lower = VerticalBand(0f to light, 100f to light)
        val upper = VerticalBand(0f to shade, 100f to shade)
        val expected = shade.compositeOver(light.compositeOver(Color.Transparent))
        val actual = upper.at(50f).compositeOver(lower.at(50f).compositeOver(Color.Transparent))
        assertEquals(expected.alpha, actual.alpha, 0.001f)
        // flatten builds a brush from the same evaluation; it must accept stops beyond the card and repeated ones.
        flatten(100f, lower, upper, VerticalBand(-5f to shade, 100f to shade, 140f to shade))
    }
}
