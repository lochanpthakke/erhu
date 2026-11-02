package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.api.GitHubUpdateApi
import com.erhu.erhu.core.network.model.GitHubReleaseDto
import com.erhu.erhu.core.network.model.GitHubAssetDto
import com.erhu.erhu.core.network.repository.UpdateCheckerRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class UpdateCheckerTest {
    private val installed = InstalledAppVersion("com.lochanpthakke.empee3", "1.0.1", 2, listOf("arm64-v8a"))
    private val apk = GitHubAssetDto("erhu-1.0.2-arm64-v8a.apk", "https://github.com/lochanpthakke/erhu/releases/download/v1.0.2/erhu-arm64-v8a.apk", 1, 123)
    private val release = GitHubReleaseDto("v1.0.2", "erhu", "Release notes", "https://github.com/lochanpthakke/erhu/releases/tag/v1.0.2", listOf(apk), publishedAt = "2026-11-01T10:00:00Z")
    private class Api : GitHubUpdateApi {
        @Volatile var calls = 0
        var answer: suspend () -> Response<GitHubReleaseDto> = { error("unset") }
        override suspend fun getLatestRelease(): Response<GitHubReleaseDto> { calls++; return answer() }
    }
    private val logger = object : Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) = Unit
        override fun d(tag: String, message: String, throwable: Throwable?) = Unit
        override fun i(tag: String, message: String, throwable: Throwable?) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
    private fun repo(api: Api, version: PublishedAppVersion? = null) = UpdateCheckerRepositoryImpl(api, logger, { version }) { 1000L }

    @Test fun `numeric ordering prereleases prefixes and build metadata`() {
        fun compare(a: String, b: String) = UpdateVersion.parse(a)!!.compareTo(UpdateVersion.parse(b)!!)
        assertTrue(compare("v1.10.0", "1.9.9") > 0)
        assertTrue(compare("1.0.1", "1.0.1-rc.10") > 0)
        assertTrue(compare("1.0.1-beta.10", "1.0.1-beta.2") > 0)
        assertTrue(compare("1.0.1-alpha", "1.0.1-beta") < 0)
        assertEquals(0, compare("erhu-v1.0.1+build.1", "1.0.1"))
        assertEquals(0, compare("1.0", "1.0.0"))
        for (bad in listOf("garbage", "1.0.1-01", "v", "1.2.nope", "01.0.0")) assertNull(UpdateVersion.parse(bad))
    }

    @Test fun `newer release offers notes date and correct ABI`() = runBlocking {
        val api = Api().apply { answer = { Response.success(release) } }
        val result = repo(api).checkForUpdates(installed) as AppResult.Success
        val candidate = result.data as UpdateCheck.Available
        assertEquals(apk, candidate.asset)
        assertEquals("Release notes", candidate.release.body)
        assertEquals(release.publishedAt, candidate.release.publishedAt)
    }

    @Test fun `actual installed versionCode blocks same-code and older APK manifests`() = runBlocking {
        val manifest = apk.copy(name = "verification.json")
        val api = Api().apply { answer = { Response.success(release.copy(assets = listOf(apk, manifest))) } }
        assertEquals(UpdateCheck.UpToDate, (repo(api, PublishedAppVersion(installed.packageName, "1.0.2", 2)).checkForUpdates(installed) as AppResult.Success).data)
        assertTrue((repo(api, PublishedAppVersion(installed.packageName, "1.0.2", 3)).checkForUpdates(installed) as AppResult.Success).data is UpdateCheck.Available)
        assertTrue(repo(api, PublishedAppVersion("other.app", "1.0.2", 3)).checkForUpdates(installed) is AppResult.Error)
    }

    @Test fun `equal and older release names are up to date and drafts never offered`() = runBlocking {
        val api = Api()
        for (tag in listOf("v1.0.1", "1.0.1", "erhu-v1.0.1", "1.0.0")) {
            api.answer = { Response.success(release.copy(tagName = tag)) }
            assertEquals(UpdateCheck.UpToDate, (repo(api).checkForUpdates(installed) as AppResult.Success).data)
        }
        for (r in listOf(release.copy(draft = true), release.copy(prerelease = true), release.copy(tagName = "1.0.2-beta.1"), release.copy(publishedAt = null))) {
            api.answer = { Response.success(r) }
            assertTrue(repo(api).checkForUpdates(installed) is AppResult.Error)
        }
    }

    @Test fun `network failure and no public release stay errors not false up to date`() = runBlocking {
        val api = Api().apply { answer = { throw IOException("offline") } }
        assertTrue((repo(api).checkForUpdates(installed) as AppResult.Error).error.message.contains("internet"))
        api.answer = { Response.error(404, "not found".toResponseBody()) }
        assertTrue((repo(api).checkForUpdates(installed) as AppResult.Error).error.message.contains("public"))
        api.answer = { Response.success(release.copy(assets = emptyList())) }
        assertTrue((repo(api).checkForUpdates(installed) as AppResult.Error).error.message.contains("APK"))
    }

    @Test fun `rate limit cooldown prevents repeated requests`() = runBlocking {
        val api = Api().apply { answer = { Response.error(429, "limit".toResponseBody()) } }
        val repo = repo(api)
        assertTrue(repo.checkForUpdates(installed) is AppResult.Error)
        assertTrue(repo.checkForUpdates(installed) is AppResult.Error)
        assertEquals(1, api.calls)
    }

    @Test fun `GitHub 403 quota responses are rate limited too`() = runBlocking {
        val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url("https://api.github.com/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(403).message("Forbidden")
            .header("X-RateLimit-Remaining", "0").header("X-RateLimit-Reset", "3600").build()
        val api = Api().apply { answer = { Response.error("limit".toResponseBody(), raw) } }
        val repo = repo(api)
        assertTrue((repo.checkForUpdates(installed) as AppResult.Error).error.message.contains("rate limit"))
        repo.checkForUpdates(installed)
        assertEquals(1, api.calls)
    }

    @Test fun `manual and monthly simultaneous lookups share a request`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val api = Api().apply { answer = { ready.await(); Response.success(release) } }
        val repo = repo(api)
        val a = async { repo.checkForUpdates(installed) }
        val b = async { repo.checkForUpdates(installed) }
        withTimeout(3000) { while (api.calls == 0) kotlinx.coroutines.delay(5) }
        kotlinx.coroutines.delay(50)
        ready.complete(Unit)
        assertEquals(a.await(), b.await())
        assertEquals(1, api.calls)
    }

    @Test fun `ABI selection rejects foreign debug and ambiguous assets`() {
        val x86 = apk.copy(name = "erhu-x86_64.apk")
        val universal = apk.copy(name = "erhu-universal.apk")
        assertEquals(apk, selectReleaseApk(listOf(x86, universal, apk), installed.abis))
        assertEquals(universal, selectReleaseApk(listOf(x86, universal), installed.abis))
        assertNull(selectReleaseApk(listOf(x86), installed.abis))
        assertNull(selectReleaseApk(listOf(apk.copy(name = "erhu-debug-arm64-v8a.apk")), installed.abis))
        assertNull(selectReleaseApk(listOf(apk.copy(browserDownloadUrl = "https://example.com/app.apk")), installed.abis))
    }
}
