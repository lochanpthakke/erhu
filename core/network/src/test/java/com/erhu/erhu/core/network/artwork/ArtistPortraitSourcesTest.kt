package com.erhu.erhu.core.network.artwork

import com.erhu.erhu.core.network.apple.model.AppleArtistSearchResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPortraitSourcesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun geniusDeduplicatesTheMatchedArtistAndIgnoresNamesakes() {
        val response = json.decodeFromString<GeniusArtistSearch>(
            """
            {"response":{"sections":[{"hits":[
              {"type":"artist","result":{"id":1110294,"name":"Drake","image_url":"https://example.test/wrong.jpg"}},
              {"type":"artist","result":{"id":130,"name":"Drake","image_url":"https://example.test/drake.jpg",
                "header_image_url":"https://example.test/drake.jpg#banner"}},
              {"type":"artist","result":{"id":45120,"name":"Drake Bell","image_url":"https://example.test/bell.jpg"}},
              {"type":"song","result":{"id":130,"name":"Drake","image_url":"https://example.test/cover.jpg"}}
            ]}]}}
            """.trimIndent(),
        )
        assertEquals(listOf("https://example.test/drake.jpg"), response.portraits(PortraitArtist("Drake")))
        assertEquals(listOf("https://example.test/drake.jpg"), response.portraits(PortraitArtist("Aubrey Graham")))
    }

    @Test
    fun geniusDoesNotChooseBetweenUnresolvedExactNamesakes() {
        val response = GeniusArtistSearch(
            GeniusArtistResults(
                hits = listOf(
                    GeniusArtistHit("artist", GeniusArtist(11, "Example", "https://example.test/one.jpg")),
                    GeniusArtistHit("artist", GeniusArtist(12, "Example", "https://example.test/two.jpg")),
                ),
            ),
        )
        assertTrue(response.portraits(PortraitArtist("Example")).isEmpty())
    }

    @Test
    fun duckResultsRejectNamesakesAndCoversAndUseTheImageUrl() {
        val response = json.decodeFromString<DuckImages>(
            """
            {"results":[
              {"title":"Drake rapper portrait","url":"https://press.example.test/interview","image":"https://example.test/photo.jpg",
                "thumbnail":"https://example.test/thumbnail.jpg"},
              {"title":"Drake Bell rapper portrait","image":"https://example.test/bell.jpg"},
              {"title":"Drake rapper album cover","image":"https://example.test/cover.jpg"},
              {"title":"Drake from the band","image":"https://example.test/band.jpg"},
              {"title":"Drake","image":"https://example.test/plain.jpg"},
              {"title":"Drake rapper portrait framed canvas","image":"https://example.test/canvas.jpg"}
            ]}
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                PortraitImage("https://example.test/photo.jpg", "https://example.test/thumbnail.jpg"),
                PortraitImage("https://example.test/plain.jpg"),
            ),
            response.portraits(PortraitArtist("Drake")),
        )
        assertEquals("4-123-456", DUCK_TOKEN.find("""var vqd="4-123-456";""")?.groupValues?.get(1))
    }

    @Test
    fun duckUsesNameFirstAndKeepsUpToThirtyResultsFromOneResponse() {
        val artist = PortraitArtist("Aubrey Drake Graham")
        assertEquals(listOf("Drake", "Drake profile photo", "Drake pfp"), artist.webQueries)

        val results = (1..35).map { DuckImage(title = "Drake", image = "https://example.test/$it.jpg") }
        assertEquals(15, DuckImages(results.take(15)).portraits(artist).size)
        assertEquals(30, DuckImages(results).portraits(artist).size)
    }

    @Test
    fun duckDistinguishesEmptyResultsFromMissingOrMalformedResults() {
        val artist = PortraitArtist("Drake")
        assertTrue(json.decodeFromString<DuckImages>("""{"results":[]}""").portraits(artist).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString<DuckImages>("""{"challenge":{}}""").portraits(artist)
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<DuckImages>("""{"results":[}""")
        }
    }

    @Test
    fun appleIgnoresAlbumArtworkAndSameNameArtists() {
        val response = json.decodeFromString<AppleArtistSearchResponse>(
            """
            {"results":{
              "albums":{"data":[{"id":"8","type":"albums","attributes":{"name":"Drake",
                "artwork":{"url":"https://example.test/cover.jpg","width":1200,"height":1200}}}]},
              "artists":{"data":[{"id":"271256","type":"artists","attributes":{"name":"Drake"}},
                {"id":"1147300457","type":"artists","attributes":{"name":"Drake",
                  "artwork":{"url":"https://example.test/other.jpg","width":1200,"height":1200}}}]}
            }}
            """.trimIndent(),
        )
        assertTrue(response.portraits(PortraitArtist("Drake")).isEmpty())
        assertTrue(response.portraits(PortraitArtist("Aubrey Drake Graham")).isEmpty())
    }

    @Test
    fun appleUsesArtworkFromTheMatchedArtistResource() {
        val response = json.decodeFromString<AppleArtistSearchResponse>(
            """
            {"results":{"artists":{"data":[{"id":"271256","type":"artists","attributes":{"name":"Drake",
                "artwork":{"url":"https://example.test/{w}x{h}.{f}","width":2400,"height":2000}}}]}}}
            """.trimIndent(),
        )
        assertEquals(listOf("https://example.test/1200x1200.jpg"), response.portraits(PortraitArtist("Drake")))
    }
}
