package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.DeezerApi
import com.lochanpthakke.empee3.core.network.model.DeezerArtistDto
import com.lochanpthakke.empee3.core.network.model.DeezerArtistSearchResponse
import com.lochanpthakke.empee3.core.network.model.DeezerErrorDto
import com.lochanpthakke.empee3.core.network.model.DeezerSearchResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deezer ranks `artist:"Drake"` results so that the real Drake is not returned at all
 * (observed 2026-10-04: "William D Drake", "Drake Lamar", …), while a plain `Drake`
 * query returns the exact artist first. The artwork lookup must use the plain query.
 */
class DeezerArtistArtworkQueryTest {
    private class RecordingApi(
        private val respond: () -> DeezerArtistSearchResponse,
    ) : DeezerApi {
        val queries = mutableListOf<String>()

        override suspend fun searchTrack(query: String) = DeezerSearchResponse()

        override suspend fun searchArtist(query: String): DeezerArtistSearchResponse {
            queries += query
            return respond()
        }
    }

    private fun lookup(
        api: DeezerApi,
        name: String = "Drake",
    ) = runBlocking { DeezerProvider(api, SilentLogger).lookupArtistArtwork(name) }

    private object SilentLogger : Logger {
        override fun v(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun d(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun i(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun w(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit

        override fun e(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    @Test
    fun `artist artwork is searched with the plain artist name`() {
        val api = RecordingApi { DeezerArtistSearchResponse(data = listOf(DeezerArtistDto(id = 1, name = "Drake", pictureBig = "https://img/drake.jpg"))) }
        assertEquals(ArtistArtworkLookup.Found("https://img/drake.jpg"), lookup(api))
        assertEquals(listOf("Drake"), api.queries)
    }

    @Test
    fun `a successful search without the exact artist is not found`() {
        val api = RecordingApi { DeezerArtistSearchResponse(data = listOf(DeezerArtistDto(id = 2, name = "Drake Bell", pictureBig = "x"))) }
        assertEquals(ArtistArtworkLookup.NotFound, lookup(api))
    }

    @Test
    fun `a network failure is a failed lookup, not a not-found`() {
        // review finding 3: offline must not burn the one-time retry
        val api = RecordingApi { throw java.io.IOException("offline") }
        assertEquals(ArtistArtworkLookup.Failed, lookup(api))
    }

    @Test
    fun `a Deezer error response (quota) is a failed lookup`() {
        val api = RecordingApi { DeezerArtistSearchResponse(error = DeezerErrorDto(type = "Exception", message = "Quota limit exceeded", code = 4)) }
        assertEquals(ArtistArtworkLookup.Failed, lookup(api))
    }
}
