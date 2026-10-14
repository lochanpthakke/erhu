package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.network.api.LastFmApi
import com.lochanpthakke.empee3.core.network.di.redactSecrets
import com.lochanpthakke.empee3.core.network.model.LastFmResultsDto
import com.lochanpthakke.empee3.core.network.model.LastFmSearchResponse
import com.lochanpthakke.empee3.core.network.model.LastFmTrackDto
import com.lochanpthakke.empee3.core.network.model.LastFmTrackMatchesDto
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** The Last.fm path from credentials to the HTTP request, with fixed responses. */
class LastFmProviderTest {
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

    private class FakeApi(
        private val answer: () -> LastFmSearchResponse,
    ) : LastFmApi {
        val keys = mutableListOf<String>()

        override suspend fun searchTrack(
            track: String,
            artist: String?,
            apiKey: String,
            method: String,
            format: String,
        ): LastFmSearchResponse {
            keys += apiKey
            return answer()
        }
    }

    private fun provider(
        key: String,
        api: FakeApi,
    ) = LastFmProvider(api, LastFmCredentials(key), SilentLogger)

    /** HTTP 403 with Last.fm's JSON error, exactly as ws.audioscrobbler.com sends it for a bad key. */
    private fun invalidKeyHttpError() =
        HttpException(
            Response.error<LastFmSearchResponse>(
                403,
                """{"message":"Invalid API key - You must be granted a valid key by last.fm","error":10}"""
                    .toResponseBody("application/json".toMediaType()),
            ),
        )

    @Test
    fun withoutAKey_theProviderIsNotConfigured_andSendsNothing() {
        val api = FakeApi { error("must not be called") }
        val lastFm = provider("  ", api)
        assertFalse(lastFm.isConfigured)
        try {
            runBlocking { lastFm.searchTrack("Runaway", "Kanye West", null) }
            fail("expected ProviderAuthException")
        } catch (_: ProviderAuthException) {
        }
        assertTrue(api.keys.isEmpty())
    }

    @Test
    fun theConfiguredKeyIsSent_andResultsAreMapped() {
        val api =
            FakeApi {
                LastFmSearchResponse(LastFmResultsDto(LastFmTrackMatchesDto(listOf(LastFmTrackDto("Runaway", "Kanye West")))))
            }
        val found = runBlocking { provider("abc123", api).searchTrack("Runaway", "Kanye West", null) }
        assertEquals(listOf("abc123"), api.keys)
        assertEquals("Runaway", found.single().title)
        assertEquals("Last.fm", found.single().sourceName)
    }

    @Test
    fun emptyResults_areAnEmptyList() {
        val api = FakeApi { LastFmSearchResponse(LastFmResultsDto(LastFmTrackMatchesDto(emptyList()))) }
        assertEquals(emptyList<Any>(), runBlocking { provider("abc123", api).searchTrack("Nothing", null, null) })
    }

    @Test
    fun http403WithError10_isAnAuthFailure() {
        val api = FakeApi { throw invalidKeyHttpError() }
        try {
            runBlocking { provider("0d20d754f42df49ba8adca53531b7829", api).searchTrack("Runaway", "Kanye West", null) }
            fail("expected ProviderAuthException")
        } catch (e: ProviderAuthException) {
            assertEquals("Last.fm error 10: Invalid API key - You must be granted a valid key by last.fm", e.message)
        }
    }

    @Test
    fun lastFmErrorCodes_mapToTheirCategory() {
        assertTrue(lastFmApiError(10, "Invalid API key") is ProviderAuthException)
        assertTrue(lastFmApiError(26, "Suspended API key") is ProviderAuthException)
        assertTrue(lastFmApiError(29, "Rate limit exceeded") is ProviderRateLimitedException)
        assertTrue(lastFmApiError(8, "Operation failed") is ProviderUnavailableException)
    }

    @Test
    fun apiKeysNeverReachTheLog() {
        val line = "--> GET https://ws.audioscrobbler.com/2.0/?track=Runaway&api_key=0d20d754f42df49ba8adca53531b7829&format=json"
        val logged = redactSecrets(line)
        assertFalse(logged.contains("0d20d754"))
        assertTrue(logged.contains("api_key=<redacted>&format=json"))
    }
}
