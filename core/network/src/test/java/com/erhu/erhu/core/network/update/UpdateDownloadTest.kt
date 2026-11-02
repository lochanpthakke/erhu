package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.network.model.GitHubAssetDto
import com.erhu.erhu.core.network.model.GitHubReleaseDto
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private val payload = ByteArray(32768) { (it % 251).toByte() }
    private fun candidate(digest: String? = null) = UpdateCheck.Available(GitHubReleaseDto(tagName = "1.0.2"),
        GitHubAssetDto("erhu.apk", "https://github.com/lochanpthakke/erhu/releases/download/v1.0.2/erhu.apk", 7, payload.size.toLong(), digest = digest))
    private fun client(server: MockWebServer) = OkHttpClient.Builder().addInterceptor { chain ->
        val original = chain.request()
        chain.proceed(original.newBuilder().url(server.url("/asset.apk")).build()).newBuilder().request(original).build()
    }.build()
    private suspend fun terminal(manager: UpdateDownloadManager) = withTimeout(5000) {
        manager.state.filter { it is UpdateDownloadState.Ready || it is UpdateDownloadState.Failed || it == UpdateDownloadState.Cancelled }.first()
    }

    @Test fun `downloads content with progress checksum and validation then exposes a ready APK`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            var validated = false
            val manager = UpdateDownloadManager(temp.newFolder(), client(server)) { file, _ ->
                assertTrue(payload.contentEquals(file.readBytes())); validated = true
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            manager.start(candidate("sha256:$hash"))
            manager.start(candidate("sha256:$hash"))
            val result = terminal(manager) as UpdateDownloadState.Ready
            assertTrue(validated)
            assertEquals(payload.size.toLong(), result.file.length())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `bad checksum empty server response and rejected APK never become installable`() = runBlocking {
        MockWebServer().use { server ->
            val directory = temp.newFolder()
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            val manager = UpdateDownloadManager(directory, client(server)) { _, _ -> }
            manager.start(candidate("sha256:bad"))
            assertTrue(terminal(manager) is UpdateDownloadState.Failed)
            assertFalse(directory.resolve("update.apk").exists())
            server.enqueue(MockResponse().setBody(""))
            val empty = UpdateDownloadManager(temp.newFolder(), client(server)) { _, _ -> }
            empty.start(candidate())
            assertTrue(terminal(empty) is UpdateDownloadState.Failed)
            server.enqueue(MockResponse().setBody(Buffer().write(payload)))
            val invalid = UpdateDownloadManager(temp.newFolder(), client(server)) { _, _ -> throw IllegalArgumentException("Invalid APK") }
            invalid.start(candidate())
            assertEquals("Invalid APK", (terminal(invalid) as UpdateDownloadState.Failed).message)
        }
    }

    @Test fun `cancellation closes transfer and removes the partial download`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(payload)).throttleBody(1024, 100, TimeUnit.MILLISECONDS))
            val directory = temp.newFolder()
            val manager = UpdateDownloadManager(directory, client(server)) { _, _ -> error("Cancelled downloads must not validate") }
            manager.start(candidate())
            withTimeout(5000) { manager.state.filter { it is UpdateDownloadState.Downloading && it.bytes > 0 }.first() }
            manager.cancel()
            assertEquals(UpdateDownloadState.Cancelled, terminal(manager))
            withTimeout(3000) { while (directory.resolve("update.partial.apk").exists()) kotlinx.coroutines.delay(10) }
            assertFalse(directory.resolve("update.apk").exists())
        }
    }
}
