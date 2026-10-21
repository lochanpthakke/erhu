package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleMusicTokenProvider @Inject constructor(
    private val client: OkHttpClient
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var validUntilMs: Long = 0

    suspend fun getToken(forceRefresh: Boolean = false): AppResult<String> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < validUntilMs) {
            return AppResult.Success(cachedToken!!)
        }

        appResultOf {
            val request = Request.Builder()
                .url("https://music.apple.com/us/browse")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to fetch browse page")
            val html = response.body?.string() ?: throw Exception("Empty browse page")

            val jsRegex = Regex("/assets/[^\"]+\\.js")
            val jsPaths = jsRegex.findAll(html).map { it.value }.distinct()
                .filter { "index" in it || "web-client" in it || "apple-music" in it }
                .toList()

            var foundToken: String? = null
            var tokenExpMs: Long? = null

            for (path in jsPaths) {
                val jsRequest = Request.Builder()
                    .url("https://music.apple.com\$path")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val jsResponse = client.newCall(jsRequest).execute()
                val jsContent = jsResponse.body?.string() ?: ""
                jsResponse.closeQuietly()

                val jwtRegex = Regex("(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)")
                val match = jwtRegex.find(jsContent)
                if (match != null) {
                    val token = match.value
                    try {
                        val payloadBase64 = token.split(".")[1]
                        val decoded = String(Base64.getUrlDecoder().decode(payloadBase64))
                        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(decoded).jsonObject
                        val exp = json["exp"]?.jsonPrimitive?.longOrNull
                        foundToken = token
                        tokenExpMs = exp?.times(1000L)
                        break
                    } catch (e: Exception) {
                        // ignore invalid tokens
                    }
                }
            }

            if (foundToken == null) throw Exception("Could not extract JWT")

            cachedToken = foundToken
            if (tokenExpMs != null) {
                val lifetime = tokenExpMs - now
                validUntilMs = tokenExpMs - minOf(3600_000L, lifetime / 10)
            } else {
                validUntilMs = now + AnimatedArtworkConfig.TTL_NO_ANIMATION_MS
            }
            foundToken
        }
    }

    suspend fun invalidate() {
        mutex.withLock {
            cachedToken = null
            validUntilMs = 0
        }
    }
}
