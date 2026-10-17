package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.network.model.TheAudioDbArtistResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TheAudioDbTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun parseArtistResponse_validJson() {
        val jsonText = """
            {
              "artists": [
                {
                  "idArtist": "111493",
                  "strArtist": "Drake",
                  "strMusicBrainzID": "15938f7b-8455-4e01-a22e-c9d092260263",
                  "strArtistThumb": "https://www.theaudiodb.com/images/media/artist/thumb/drake.jpg",
                  "strArtistFanart": "https://www.theaudiodb.com/images/media/artist/fanart/drake1.jpg",
                  "strArtistFanart2": "https://www.theaudiodb.com/images/media/artist/fanart/drake2.jpg",
                  "strArtistFanart3": "https://www.theaudiodb.com/images/media/artist/fanart/drake3.jpg",
                  "strArtistFanart4": "https://www.theaudiodb.com/images/media/artist/fanart/drake4.jpg"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<TheAudioDbArtistResponse>(jsonText)
        val dto = parsed.artists?.firstOrNull()
        assertEquals("Drake", dto?.strArtist)
        assertEquals("15938f7b-8455-4e01-a22e-c9d092260263", dto?.strMusicBrainzID)
        assertEquals("https://www.theaudiodb.com/images/media/artist/thumb/drake.jpg", dto?.strArtistThumb)
        assertEquals("https://www.theaudiodb.com/images/media/artist/fanart/drake2.jpg", dto?.strArtistFanart2)
        assertEquals("https://www.theaudiodb.com/images/media/artist/fanart/drake3.jpg", dto?.strArtistFanart3)
        assertEquals("https://www.theaudiodb.com/images/media/artist/fanart/drake4.jpg", dto?.strArtistFanart4)
    }

    @Test
    fun parseArtistResponse_nullArtists() {
        val jsonText = """{ "artists": null }"""
        val parsed = json.decodeFromString<TheAudioDbArtistResponse>(jsonText)
        assertNull(parsed.artists)
    }
}
