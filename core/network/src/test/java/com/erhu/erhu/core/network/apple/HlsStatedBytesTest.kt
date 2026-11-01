package com.erhu.erhu.core.network.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsStatedBytesTest {
    private val url = "https://example.invalid/cover/1080.m3u8"

    @Test
    fun `byte ranges for the init part and every segment add up to the cover's size`() {
        val playlist =
            HlsPlaylistParser.parseMediaPlaylist(
                url,
                """
                #EXTM3U
                #EXT-X-MAP:URI="cover.mp4",BYTERANGE="897@0"
                #EXTINF:2.833,
                #EXT-X-BYTERANGE:3485038@897
                cover.mp4
                #EXTINF:2.875,
                #EXT-X-BYTERANGE:3378275
                cover.mp4
                #EXT-X-ENDLIST
                """.trimIndent(),
            )

        assertEquals(897L + 3_485_038L + 3_378_275L, playlist.statedBytes)
    }

    @Test
    fun `a playlist without byte ranges states no size`() {
        val playlist =
            HlsPlaylistParser.parseMediaPlaylist(
                url,
                """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:2.833,
                seg1.m4s
                #EXT-X-ENDLIST
                """.trimIndent(),
            )

        assertNull(playlist.statedBytes)
    }
}
