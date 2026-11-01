package com.erhu.erhu.core.network.lyrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The track to find lyrics for: only this metadata ever leaves the device. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    /** 0 when unknown. */
    val durationMs: Long = 0L,
)

/** How well a result is timed; higher is better. */
enum class LyricsTiming {
    NONE,
    PLAIN,
    LINE,
    WORD,
}

/**
 * One online result, as the provider describes it. [wordSyncedLrc] is enhanced LRC built from the
 * provider's genuine word timing (null without it); [syncedLyrics] is line-timed LRC.
 */
@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.lyrics.LyricsCandidate")
data class LyricsCandidate(
    val provider: String,
    val providerId: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    /** Seconds; null when unknown. */
    val durationSec: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val wordSyncedLrc: String? = null,
) {
    val timing: LyricsTiming
        get() =
            when {
                !wordSyncedLrc.isNullOrBlank() -> LyricsTiming.WORD
                !syncedLyrics.isNullOrBlank() -> LyricsTiming.LINE
                !plainLyrics.isNullOrBlank() -> LyricsTiming.PLAIN
                else -> LyricsTiming.NONE
            }

    /** The best text this result offers: word-timed, else line-timed, else plain. */
    val bestText: String?
        get() =
            wordSyncedLrc?.takeIf { it.isNotBlank() } ?: syncedLyrics?.takeIf { it.isNotBlank() }
                ?: plainLyrics?.takeIf { it.isNotBlank() }
}

/** What a provider answered for one request. */
sealed interface ProviderResponse<out T> {
    data class Success<T>(
        val value: T,
    ) : ProviderResponse<T>

    /** The provider has nothing for this request. */
    data object NotFound : ProviderResponse<Nothing>

    /** Rate limited: no request until [retryAtMs] (wall clock). */
    data class RateLimited(
        val retryAtMs: Long,
    ) : ProviderResponse<Nothing>

    /** No connection. */
    data object Offline : ProviderResponse<Nothing>

    data class Failed(
        val reason: String,
    ) : ProviderResponse<Nothing>
}

/**
 * A legitimate online lyrics source. More providers can be added behind this without touching
 * the lyrics UI; the repository asks them in order.
 */
interface OnlineLyricsProvider {
    val name: String

    /** The provider's best record for exactly this track (title, artist, album, duration). */
    suspend fun exact(query: LyricsQuery): ProviderResponse<LyricsCandidate>

    /** Records that may match [query]; [freeText] searches by text instead (manual search). */
    suspend fun search(
        query: LyricsQuery,
        freeText: String? = null,
    ): ProviderResponse<List<LyricsCandidate>>
}
