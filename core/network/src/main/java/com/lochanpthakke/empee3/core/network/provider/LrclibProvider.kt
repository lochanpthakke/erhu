@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.LyricsProvider
import com.lochanpthakke.empee3.core.model.LyricsResult
import com.lochanpthakke.empee3.core.network.api.LrclibApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrclibProvider
    @Inject
    constructor(
        private val lrclibApi: LrclibApi,
        private val logger: Logger,
    ) : LyricsProvider {
        override suspend fun fetchLyrics(
            title: String,
            artist: String,
            album: String?,
            durationSec: Int?,
        ): LyricsResult? =
            try {
                val dto =
                    lrclibApi.getLyrics(
                        trackName = title,
                        artistName = artist,
                        albumName = album,
                        durationInSeconds = durationSec,
                    )
                if (!dto.plainLyrics.isNullOrBlank() || !dto.syncedLyrics.isNullOrBlank()) {
                    LyricsResult(
                        plainLyrics = dto.plainLyrics,
                        syncedLyrics = dto.syncedLyrics,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.d(TAG, "LRCLIB direct lookup miss for $title by $artist: ${e.message}")
                searchLyricsFallback(title, artist)
            }

        private suspend fun searchLyricsFallback(
            title: String,
            artist: String,
        ): LyricsResult? =
            try {
                val query = "$title $artist"
                val results = lrclibApi.searchLyrics(query)
                val firstMatch =
                    results.firstOrNull {
                        !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank()
                    }
                if (firstMatch != null) {
                    LyricsResult(
                        plainLyrics = firstMatch.plainLyrics,
                        syncedLyrics = firstMatch.syncedLyrics,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.w(TAG, "LRCLIB search fallback failed: ${e.message}", e)
                null
            }

        companion object {
            private const val TAG = "LrclibProvider"
        }
    }
