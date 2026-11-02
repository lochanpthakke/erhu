package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.network.api.GitHubUpdateApi
import com.erhu.erhu.core.network.api.UpdateRepositoryConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class GitHubUpdateApiTest {
    @Test fun `real Retrofit request uses official release path and public headers and decodes release fields`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"tag_name":"v1.0.2","name":null,"body":null,"draft":false,"prerelease":false,
                 "published_at":"2026-11-01T10:00:00Z","html_url":"https://github.com/lochanpthakke/erhu/releases/tag/v1.0.2",
                 "assets":[{"id":42,"name":"erhu-1.0.2-arm64-v8a.apk","state":"uploaded","size":12345,
                 "digest":"sha256:abc","browser_download_url":"https://github.com/lochanpthakke/erhu/releases/download/v1.0.2/erhu.apk"}]}
            """.trimIndent()))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
                .build().create(GitHubUpdateApi::class.java)
            val release = api.getLatestRelease().body()!!
            val request = server.takeRequest()
            assertEquals("/" + UpdateRepositoryConfig.LATEST, request.path)
            assertEquals("application/vnd.github+json", request.getHeader("Accept"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("v1.0.2", release.tagName)
            assertNull(release.body)
            assertFalse(release.draft)
            assertEquals("2026-11-01T10:00:00Z", release.publishedAt)
            assertEquals(12345L, release.assets.single().size)
            assertEquals(42L, release.assets.single().id)
        }
    }
}
