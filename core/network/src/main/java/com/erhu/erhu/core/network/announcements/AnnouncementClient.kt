package com.erhu.erhu.core.network.announcements

import com.erhu.erhu.core.network.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

@Singleton
class AnnouncementClient internal constructor(
    client: OkHttpClient,
    private val json: Json,
    private val endpoint: String,
    private val insecureForTests: Boolean = false,
) {
    @Inject
    constructor(client: OkHttpClient, json: Json) : this(client, json, BuildConfig.ANNOUNCEMENT_API_URL)

    private val http =
        client
            .newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    val configured: Boolean get() =
        runCatching {
            val uri = java.net.URI(endpoint)
            uri.rawQuery == null && uri.rawFragment == null && (safeAnnouncementUrl(endpoint) || (insecureForTests && uri.scheme == "http"))
        }.getOrDefault(false)

    suspend fun fetch(id: String? = null): List<Announcement> =
        withContext(Dispatchers.IO) {
            require(configured) { "Announcements are unavailable in this build." }
            require(id == null || validAnnouncementId(id))
            val path = if (id == null) "/feed" else "/announcements/$id"
            val call =
                http.newCall(
                    Request
                        .Builder()
                        .url(endpoint.trimEnd('/') + path)
                        .header("Accept", "application/json")
                        .build(),
                )
            val response =
                suspendCancellableCoroutine<Response> { c ->
                    c.invokeOnCancellation { call.cancel() }
                    call.enqueue(
                        object : Callback {
                            override fun onFailure(
                                call: Call,
                                e: IOException,
                            ) {
                                if (c.isActive) c.resumeWithException(e)
                            }

                            override fun onResponse(
                                call: Call,
                                response: Response,
                            ) {
                                c.resume(response) { _, value, _ -> value.close() }
                            }
                        },
                    )
                }
            response.use {
                if (id != null && it.code == 404) return@withContext emptyList()
                if (!it.isSuccessful) throw IOException("Announcements are temporarily unavailable (HTTP ${it.code}).")
                val input = it.body?.byteStream() ?: throw IOException("Empty announcement response")
                val bytes =
                    input.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n <
                                0
                            ) {
                                break
                            }
                            require(output.size() + n <= 524288)
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                val data = bytes.toString(Charsets.UTF_8)
                val items =
                    if (id == null) {
                        json
                            .parseToJsonElement(data)
                            .jsonObject
                            .getValue("announcements")
                            .jsonArray
                            .take(100)
                            .mapNotNull { element -> runCatching { json.decodeFromJsonElement<Announcement>(element) }.getOrNull() }
                    } else {
                        listOf(json.decodeFromString<Announcement>(data))
                    }
                items.take(100).filter { a -> a.valid() && (id == null || a.id == id) }.distinctBy { a -> a.id }
            }
        }
}
