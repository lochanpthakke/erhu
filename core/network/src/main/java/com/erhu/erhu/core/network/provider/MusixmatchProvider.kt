package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.network.api.MusixmatchApi
import com.erhu.erhu.core.network.lyrics.LyricsCandidate
import com.erhu.erhu.core.network.lyrics.LyricsMatcher
import com.erhu.erhu.core.network.lyrics.LyricsQuery
import com.erhu.erhu.core.network.lyrics.OnlineLyricsProvider
import com.erhu.erhu.core.network.lyrics.ProviderResponse
import com.erhu.erhu.core.network.lyrics.usableSyncedLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import retrofit2.Response
import java.io.IOException

/** Disabled without a provisioned subtitle API key. Only real, unrestricted LRC is accepted. */
class MusixmatchProvider(
    private val api: MusixmatchApi,
    private val key: String,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : OnlineLyricsProvider {
    override val name = "Musixmatch"
    private val gate = Mutex()
    private var blockedUntilMs = 0L

    // track.search supplies the metadata needed to validate a recording before fetching subtitles.
    override suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate> = ProviderResponse.NotFound

    override suspend fun search(
        query: LyricsQuery,
        freeText: String?,
    ): ProviderResponse<List<LyricsCandidate>> =
        gate.withLock {
            if (key.isBlank() || query.artist.isBlank() || freeText != null) return@withLock ProviderResponse.NotFound
            if (nowMs() < blockedUntilMs) return@withLock ProviderResponse.RateLimited(blockedUntilMs)
            try {
                val tracks = body(api.tracks(key, query.title, query.artist))
                val matches =
                    (tracks["track_list"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { entry ->
                            val track = (entry as? JsonObject)?.get("track") as? JsonObject ?: return@mapNotNull null
                            if (track.number("restricted") == 1L || track.number("has_subtitles") != 1L) return@mapNotNull null
                            LyricsCandidate(
                                provider = name,
                                providerId = track.number("track_id") ?: return@mapNotNull null,
                                trackName = track.text("track_name") ?: return@mapNotNull null,
                                artistName = track.text("artist_name") ?: return@mapNotNull null,
                                albumName = track.text("album_name"),
                                durationSec = (track["track_length"] as? JsonPrimitive)?.doubleOrNull,
                            ).takeIf { LyricsMatcher.score(query, it).confident }
                        }.sortedByDescending { LyricsMatcher.score(query, it).score }
                for (match in matches.take(3)) {
                    val subtitleBody =
                        try {
                            body(api.subtitle(key, match.providerId, duration = query.durationMs.takeIf { it > 0 }?.div(1000)))
                        } catch (e: ApiFailure) {
                            if (e.code == 404) continue else throw e
                        }
                    val subtitle = subtitleBody["subtitle"] as? JsonObject ?: continue
                    if (subtitle.number("restricted") == 1L) continue
                    val lrc = subtitle.text("subtitle_body")
                    if (!usableSyncedLyrics(lrc, query.durationMs)) continue
                    return@withLock ProviderResponse.Success(listOf(match.copy(syncedLyrics = lrc)))
                }
                ProviderResponse.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiFailure) {
                if (e.code == 429 || e.code == 402) {
                    blockedUntilMs = nowMs() + e.retryMs
                    ProviderResponse.RateLimited(blockedUntilMs)
                } else if (e.code == 404) {
                    ProviderResponse.NotFound
                } else {
                    ProviderResponse.Failed("Musixmatch status ${e.code}")
                }
            } catch (_: IOException) {
                ProviderResponse.Failed("Musixmatch unavailable")
            } catch (_: SerializationException) {
                ProviderResponse.Failed("Malformed Musixmatch response")
            } catch (_: IllegalArgumentException) {
                ProviderResponse.Failed("Malformed Musixmatch response")
            }
        }

    private fun body(response: Response<JsonObject>): JsonObject {
        val retry =
            response
                .headers()["Retry-After"]
                ?.toLongOrNull()
                ?.coerceIn(1, 86400)
                ?.times(1000) ?: 60_000L
        if (!response.isSuccessful) throw ApiFailure(response.code(), retry)
        val message = response.body()?.get("message") as? JsonObject ?: throw ApiFailure(502, retry)
        val status = ((message["header"] as? JsonObject)?.get("status_code") as? JsonPrimitive)?.intOrNull
        if (status != 200) throw ApiFailure(status ?: 502, retry)
        return message["body"] as? JsonObject ?: throw ApiFailure(502, retry)
    }

    private class ApiFailure(
        val code: Int,
        val retryMs: Long,
    ) : IOException()
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.content

private fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
