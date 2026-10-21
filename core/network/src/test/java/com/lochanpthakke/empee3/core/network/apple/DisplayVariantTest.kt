package com.lochanpthakke.empee3.core.network.apple

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Fetch screen used to take the first (highest bitrate) variant: SOS came down as 2048x2732
 * HEVC Main 10 at 17 Mbit/s, which the phone's decoder stalls on in a cover-sized view.
 */
class DisplayVariantTest {
    private fun variant(
        w: Int,
        h: Int,
        bandwidth: Int,
    ) = HlsVariantStream("${w}x$h", w, h, bandwidth, "https://example/$w.m3u8", 1f)

    // As the parser returns them: highest bandwidth first.
    private val tall =
        listOf(
            variant(2048, 2732, 17_000_000),
            variant(1440, 1920, 9_000_000),
            variant(1080, 1440, 5_000_000),
            variant(720, 960, 2_500_000),
            variant(540, 720, 1_200_000),
        )

    @Test
    fun `the largest variant that fits a phone screen is chosen`() {
        assertEquals(1080, HlsPlaylistParser.displayVariant(tall)!!.width)
    }

    @Test
    fun `the short edge decides, for square covers too`() {
        val square = listOf(variant(2048, 2048, 15_000_000), variant(1080, 1080, 4_000_000), variant(720, 720, 2_000_000))
        assertEquals(1080, HlsPlaylistParser.displayVariant(square)!!.width)
    }

    @Test
    fun `when every variant is larger the smallest is used`() {
        val big = listOf(variant(2048, 2732, 17_000_000), variant(1440, 1920, 9_000_000))
        assertEquals(1440, HlsPlaylistParser.displayVariant(big)!!.width)
    }

    @Test
    fun `no variants, no choice`() {
        assertEquals(null, HlsPlaylistParser.displayVariant(emptyList()))
    }
}
