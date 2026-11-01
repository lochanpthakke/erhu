package com.erhu.erhu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** Maps what the square crop frame shows back to the source image's pixels. */
class SquareCropTest {
    @Test
    fun `unzoomed and centred, a landscape image is cropped to its centre square`() {
        // 2000×1000 image in a 500 px frame: scaled ×0.5 to cover, centre 1000×1000 visible
        assertEquals(CropRect(left = 500, top = 0, size = 1000), squareCropRect(2000, 1000, 500f, zoom = 1f, offsetX = 0f, offsetY = 0f))
    }

    @Test
    fun `unzoomed and centred, a portrait image is cropped to its centre square`() {
        assertEquals(CropRect(left = 0, top = 500, size = 1000), squareCropRect(1000, 2000, 500f, zoom = 1f, offsetX = 0f, offsetY = 0f))
    }

    @Test
    fun `zooming in shrinks the source square around the centre`() {
        // ×2 zoom: half as many source pixels visible
        assertEquals(CropRect(left = 750, top = 250, size = 500), squareCropRect(2000, 1000, 500f, zoom = 2f, offsetX = 0f, offsetY = 0f))
    }

    @Test
    fun `dragging the image right shows more of its left side`() {
        // +250 frame px at scale 0.5 = 500 source px to the left
        assertEquals(CropRect(left = 0, top = 0, size = 1000), squareCropRect(2000, 1000, 500f, zoom = 1f, offsetX = 250f, offsetY = 0f))
    }

    @Test
    fun `offsets are clamped so the frame never shows outside the image`() {
        // landscape at zoom 1: may move ±250 frame px horizontally, not at all vertically
        assertEquals(250f to 0f, clampCropOffset(2000, 1000, 500f, zoom = 1f, offsetX = 9999f, offsetY = 40f))
        assertEquals(-250f to 0f, clampCropOffset(2000, 1000, 500f, zoom = 1f, offsetX = -9999f, offsetY = -40f))
        // zoomed ×2: the image covers 2000×1000 frame px → ±750 horizontally, ±250 vertically
        assertEquals(750f to 250f, clampCropOffset(2000, 1000, 500f, zoom = 2f, offsetX = 9999f, offsetY = 9999f))
    }

    @Test
    fun `the crop rect always stays inside the image`() {
        val rect = squareCropRect(1200, 900, 300f, zoom = 1.37f, offsetX = 9999f, offsetY = -9999f)
        assert(rect.left >= 0 && rect.top >= 0 && rect.left + rect.size <= 1200 && rect.top + rect.size <= 900) { rect.toString() }
    }
}
