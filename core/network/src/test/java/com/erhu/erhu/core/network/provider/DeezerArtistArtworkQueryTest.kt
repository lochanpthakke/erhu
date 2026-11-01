package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.network.api.DeezerApi
import com.erhu.erhu.core.network.model.DeezerAlbumDetailDto
import com.erhu.erhu.core.network.model.DeezerArtistDto
import com.erhu.erhu.core.network.model.DeezerArtistSearchResponse
import com.erhu.erhu.core.network.model.DeezerErrorDto
import com.erhu.erhu.core.network.model.DeezerSearchResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

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
        val limits = mutableListOf<Int>()

        override suspend fun searchTrack(query: String) = DeezerSearchResponse()

        override suspend fun getAlbum(albumId: Long) = DeezerAlbumDetailDto()

        override suspend fun searchArtist(
            query: String,
            limit: Int,
        ): DeezerArtistSearchResponse {
            queries += query
            limits += limit
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
        assertEquals(ArtistArtworkLookup.Found("https://img/drake.jpg", deezerArtistId = 1), lookup(api))
        assertEquals(listOf("Drake"), api.queries)
        assertEquals(listOf(DeezerApi.ARTIST_SEARCH_LIMIT), api.limits)
    }

    @Test
    fun `ambiguous same-name profiles are not found`() {
        val api =
            RecordingApi {
                DeezerArtistSearchResponse(
                    data =
                        listOf(
                            DeezerArtistDto(id = 1, name = "Drake", pictureBig = "https://img/a.jpg", nbFan = 166),
                            DeezerArtistDto(id = 2, name = "Drake", pictureBig = "https://img/b.jpg", nbFan = 141),
                        ),
                )
            }
        assertEquals(ArtistArtworkLookup.NotFound, lookup(api))
    }

    @Test
    fun `a response with neither results nor an error is a failed lookup, not a not-found`() {
        val api = RecordingApi { DeezerArtistSearchResponse() }
        assertEquals(ArtistArtworkLookup.Failed, lookup(api))
    }

    @Test
    fun `an unreadable response is a failed lookup`() {
        val api = RecordingApi { throw SerializationException("bad json") }
        assertEquals(ArtistArtworkLookup.Failed, lookup(api))
    }

    @Test
    fun `an HTTP error such as rate limiting is a failed lookup`() {
        val api = RecordingApi { throw HttpException(Response.error<Any>(429, "".toResponseBody(null))) }
        assertEquals(ArtistArtworkLookup.Failed, lookup(api))
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
