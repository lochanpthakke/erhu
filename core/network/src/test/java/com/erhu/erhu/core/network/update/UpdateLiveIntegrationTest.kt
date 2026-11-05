package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.api.GitHubUpdateApi
import com.erhu.erhu.core.network.repository.UpdateCheckerRepositoryImpl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UpdateLiveIntegrationTest {
    @Test fun officialPublicRepositoryBetaWorksWithoutCredentials() = runBlocking {
        assumeTrue("Live API validation is opt-in", System.getenv("ERHU_LIVE_UPDATE_CHECK") == "1")
        val logger=object:Logger {
            override fun v(tag:String,message:String,throwable:Throwable?)=Unit
            override fun d(tag:String,message:String,throwable:Throwable?)=Unit
            override fun i(tag:String,message:String,throwable:Throwable?)=Unit
            override fun w(tag:String,message:String,throwable:Throwable?) { System.err.println("LIVE_CHECK: ${throwable?.javaClass?.simpleName}: ${throwable?.message?.substringBefore("?")}") }
            override fun e(tag:String,message:String,throwable:Throwable?)=Unit
        }
        val json=Json{ignoreUnknownKeys=true}
        val client=OkHttpClient()
        val api=Retrofit.Builder().baseUrl("https://api.github.com/").client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(GitHubUpdateApi::class.java)
        val repository=UpdateCheckerRepositoryImpl(api,logger,client,json)
        val old=InstalledAppVersion("com.lochanpthakke.empee3","1.0.1",2,listOf("arm64-v8a"))
        val available=repository.checkForUpdates(old)
        assertTrue("Actual release check: $available",available is AppResult.Success)
        val update=(available as AppResult.Success).data as UpdateCheck.Available
        assertTrue(update.release.prerelease)
        assertTrue(requireNotNull(update.versionCode) > old.versionCode)
        assertEquals(UpdateVersion.parse(update.release.tagName)?.numbers,UpdateVersion.parse(requireNotNull(update.versionName))?.numbers)
        assertTrue(update.asset.name.endsWith(".apk"))
        val current=old.copy(versionName=requireNotNull(update.versionName),versionCode=requireNotNull(update.versionCode))
        assertEquals(UpdateCheck.UpToDate,(repository.checkForUpdates(current) as AppResult.Success).data)
    }
}
