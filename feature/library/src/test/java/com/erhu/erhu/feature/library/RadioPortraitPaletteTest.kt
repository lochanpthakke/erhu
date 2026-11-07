package com.erhu.erhu.feature.library

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioPortraitPaletteTest {
    @Test fun stationPalettesFollowTheSourceImageAndDoNotRecycleTheSharedBitmap() {
        fun palette(color: Int): LyricsPalette {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val result = radioPalette(bitmap)
            assertFalse(bitmap.isRecycled)
            bitmap.recycle()
            return result
        }
        val blue = palette(Color.BLUE)
        val orange = palette(0xffff8800.toInt())
        assertNotEquals(blue.surface, orange.surface)
        listOf(blue, orange, palette(Color.WHITE), palette(Color.BLACK)).forEach { result ->
            result.gradient.forEach { background ->
                assertTrue(ColorUtils.calculateContrast(result.content, background) >= 4.5)
            }
        }
    }

    @Test fun missingImageUsesTheExistingScreenTheme() {
        val theme = androidx.compose.material3.darkColorScheme()
        assertSame(theme, radioColorScheme(theme, null))
    }
}
