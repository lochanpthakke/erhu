package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DistributionNetworkPolicyTest {
    @Test
    fun candidateBlocksRestrictedRequestsBeforeNetworkAccess() {
        assertTrue(BuildConfig.PROPRIETARY_CANDIDATE)
        val client = OkHttpClient.Builder().addInterceptor(DistributionNetworkPolicy).build()
        listOf(
            "music.apple.com", "itunes.apple.com", "api.deezer.com", "api.musixmatch.com",
            "www.theaudiodb.com", "musicbrainz.org", "coverartarchive.org", "lrclib.net",
        ).forEach { host ->
            val request = Request.Builder().url("https://$host/test").build()
            val error = assertThrows(IOException::class.java) { client.newCall(request).execute().close() }
            assertTrue(error.message.orEmpty().contains("pending permission"))
        }
    }

    @Test
    fun unrelatedRequestsStillReachTheirServer() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("allowed"))
            val client = OkHttpClient.Builder().addInterceptor(DistributionNetworkPolicy).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response ->
                assertEquals("allowed", response.body?.string())
            }
            assertEquals(1, server.requestCount)
        }
    }
}
