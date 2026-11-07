package com.erhu.erhu.core.model

import java.security.MessageDigest

data class LyricsTimingContext(
    val songKey: String,
    val sourceKey: String,
    val label: String,
    val durationMs: Long = 0,
) {
    val id: String get() = "$songKey:$sourceKey"

    companion object {
        fun create(
            song: Song,
            text: String,
            sourceId: String,
            label: String,
        ): LyricsTimingContext =
            LyricsTimingContext(hash("${song.id}|${song.uri}|${song.durationMs}"), hash("$sourceId\n$text"), label, song.durationMs)

        private fun hash(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

data class LyricsTimingAdjustment(
    val songKey: String,
    val sourceKey: String,
    val label: String,
    val offsetMs: Long,
    val savedAtMs: Long,
) {
    val id: String get() = "$songKey:$sourceKey"
}

/** User offsets: positive = later. Applied after the LRC parser's own embedded offset. */
object LyricsTimingOffset {
    const val LIMIT_MS = 30000L
    const val STEP_MS = 50L

    fun clamp(offset: Long): Long = offset.coerceIn(-LIMIT_MS, LIMIT_MS)

    fun lyricsPosition(
        playbackMs: Long,
        offsetMs: Long,
    ): Long = shift(playbackMs, -clamp(offsetMs))

    fun playbackPosition(
        lyricMs: Long,
        offsetMs: Long,
        durationMs: Long = 0,
    ): Long = shift(lyricMs, clamp(offsetMs)).coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

    private fun shift(
        value: Long,
        delta: Long,
    ): Long =
        when {
            delta > 0 && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
            else -> value + delta
        }
}
