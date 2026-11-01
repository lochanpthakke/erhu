package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.model.LyricsProvider
import com.erhu.erhu.core.model.LyricsResult
import com.erhu.erhu.core.network.api.LrclibApi
import com.erhu.erhu.core.network.lyrics.LyricsCandidate
import com.erhu.erhu.core.network.lyrics.LyricsMatcher
import com.erhu.erhu.core.network.lyrics.LyricsQuery
import com.erhu.erhu.core.network.lyrics.Lyricsfile
import com.erhu.erhu.core.network.lyrics.OnlineLyricsProvider
import com.erhu.erhu.core.network.lyrics.ProviderResponse
import com.erhu.erhu.core.network.model.LrclibLyricsDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * LRCLIB (https://lrclib.net/docs). One request at a time, a short gap between them, and after a
 * 429 no request at all until its Retry-After has passed: no retry storms. Only the track's title,
 * artist, album and duration are sent. Word timing is taken only from the Lyricsfile when LRCLIB
 * says the record has it (`hasWordSync`); `syncedLyrics` is line timing.
 */
@Singleton
class LrclibProvider
    internal constructor(
        private val api: LrclibApi,
        private val logger: Logger,
        private val nowMs: () -> Long,
        private val minGapMs: Long,
    ) : OnlineLyricsProvider,
        LyricsProvider {
        @Inject
        constructor(api: LrclibApi, logger: Logger) : this(api, logger, System::currentTimeMillis, MIN_GAP_MS)

        override val name: String = NAME

        private val gate = Mutex()
        private var lastRequestMs = 0L
        private var blockedUntilMs = 0L

        override suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate> {
            val response =
                call {
                    api.getLyrics(
                        trackName = query.title,
                        artistName = query.artist,
                        albumName = query.album?.takeIf { it.isNotBlank() },
                        durationInSeconds = query.durationMs.takeIf { it > 0 }?.let { (it / MS_PER_SECOND.toDouble()).roundToInt() },
                    )
                }
            return when (response) {
                is ProviderResponse.Success ->
                    response.value.toCandidate()?.let { ProviderResponse.Success(it) }
                        ?: ProviderResponse.NotFound
                else -> response.withoutValue()
            }
        }

        override suspend fun search(
            query: LyricsQuery,
            freeText: String?,
        ): ProviderResponse<List<LyricsCandidate>> {
            val response =
                call {
                    if (freeText != null) {
                        api.searchLyrics(query = freeText)
                    } else {
                        api.searchLyrics(trackName = query.title, artistName = query.artist)
                    }
                }
            return when (response) {
                is ProviderResponse.Success -> ProviderResponse.Success(response.value.mapNotNull { it.toCandidate() })
                else -> response.withoutValue()
            }
        }

        /** Older callers (metadata fixing): the best confident match, word timing first. */
        override suspend fun fetchLyrics(
            title: String,
            artist: String,
            album: String?,
            durationSec: Int?,
        ): LyricsResult? {
            val query = LyricsQuery(title, artist, album, (durationSec ?: 0) * MS_PER_SECOND)
            val exact = (exact(query) as? ProviderResponse.Success)?.value
            val found =
                exact?.takeIf { LyricsMatcher.score(query, it).confident }
                    ?: (search(query) as? ProviderResponse.Success)?.value?.let { LyricsMatcher.best(query, it) }
            return found?.let { LyricsResult(plainLyrics = it.plainLyrics, syncedLyrics = it.wordSyncedLrc ?: it.syncedLyrics) }
        }

        @Suppress("TooGenericExceptionCaught", "ReturnCount", "SwallowedException")
        private suspend fun <T> call(request: suspend () -> Response<T>): ProviderResponse<T> =
            gate.withLock {
                val now = nowMs()
                if (now < blockedUntilMs) return ProviderResponse.RateLimited(blockedUntilMs)
                val wait = lastRequestMs + minGapMs - now
                if (wait > 0) delay(wait)
                lastRequestMs = nowMs()
                try {
                    interpret(request())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UnknownHostException) {
                    ProviderResponse.Offline
                } catch (e: ConnectException) {
                    ProviderResponse.Offline
                } catch (e: SocketTimeoutException) {
                    ProviderResponse.Failed("timed out")
                } catch (e: IOException) {
                    ProviderResponse.Failed(e.javaClass.simpleName)
                } catch (e: SerializationException) {
                    logger.w(TAG, "Malformed LRCLIB response: ${e.message}")
                    ProviderResponse.Failed("malformed response")
                }
            }

        private fun <T> interpret(response: Response<T>): ProviderResponse<T> {
            val body = response.body()
            return when {
                response.code() == HTTP_TOO_MANY -> {
                    blockedUntilMs = nowMs() + retryAfterMs(response.headers()["Retry-After"])
                    logger.w(TAG, "LRCLIB rate limit: no requests until $blockedUntilMs")
                    ProviderResponse.RateLimited(blockedUntilMs)
                }
                response.code() == HTTP_NOT_FOUND -> ProviderResponse.NotFound
                response.isSuccessful && body != null -> ProviderResponse.Success(body)
                else -> ProviderResponse.Failed("HTTP ${response.code()}")
            }
        }

        /** Retry-After in seconds or as an HTTP date; [DEFAULT_RETRY_MS] when missing or unreadable. */
        internal fun retryAfterMs(header: String?): Long {
            val value = header?.trim().orEmpty()
            value.toLongOrNull()?.let { return (it * MS_PER_SECOND).coerceIn(0L, MAX_RETRY_MS) }
            return try {
                (
                    ZonedDateTime
                        .parse(
                            value,
                            DateTimeFormatter.RFC_1123_DATE_TIME,
                        ).toInstant()
                        .toEpochMilli() - nowMs()
                ).coerceIn(0L, MAX_RETRY_MS)
            } catch (
                @Suppress("SwallowedException") e: DateTimeParseException,
            ) {
                DEFAULT_RETRY_MS
            }
        }

        /** A non-success response, retyped. */
        private fun <R> ProviderResponse<*>.withoutValue(): ProviderResponse<R> =
            when (this) {
                is ProviderResponse.Success -> ProviderResponse.Failed("unexpected")
                is ProviderResponse.NotFound -> this
                is ProviderResponse.RateLimited -> this
                is ProviderResponse.Offline -> this
                is ProviderResponse.Failed -> this
            }

        companion object {
            const val NAME = "LRCLIB"
            private const val TAG = "LrclibProvider"
            private const val HTTP_TOO_MANY = 429
            private const val HTTP_NOT_FOUND = 404
            private const val MS_PER_SECOND = 1_000L
            private const val MIN_GAP_MS = 400L
            private const val DEFAULT_RETRY_MS = 60_000L
            private const val MAX_RETRY_MS = 3_600_000L

            /** A record as a candidate; null without an id or a title. Word timing only when the record has it. */
            @Suppress("ReturnCount") // no id, or no title
            internal fun LrclibLyricsDto.toCandidate(): LyricsCandidate? {
                val id = id ?: return null
                val title = trackName?.takeIf { it.isNotBlank() } ?: return null
                return LyricsCandidate(
                    provider = NAME,
                    providerId = id,
                    trackName = title,
                    artistName = artistName.orEmpty(),
                    albumName = albumName,
                    durationSec = duration?.toDouble(),
                    instrumental = instrumental || Lyricsfile.isInstrumental(lyricsfile),
                    plainLyrics = plainLyrics?.takeIf { it.isNotBlank() },
                    syncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
                    wordSyncedLrc = if (hasWordSync) Lyricsfile.toEnhancedLrc(lyricsfile) else null,
                )
            }
        }
    }
