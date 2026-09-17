package com.lochanpthakke.empee3.core.model

data class LyricsResult(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

interface LyricsProvider {
    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Int? = null,
    ): LyricsResult?
}
