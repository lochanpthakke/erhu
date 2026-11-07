package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.LyricsTimingAdjustment
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.LyricsTimingOffset
import com.erhu.erhu.core.model.Song

/** Source identity stays the same for online and offline copies, but not for another record/text. */
@Suppress("ReturnCount")
internal fun withLyricsTiming(
    song: Song?,
    lyrics: SongLyrics?,
    saved: Map<String, LyricsTimingAdjustment>,
): SongLyrics? {
    if (song == null || lyrics == null || lyrics.songId != song.id) return null
    val text = lyrics.text
    if (text == null ||
        LyricsParser.parse(text) !is Lyrics.Synced
    ) {
        return lyrics.copy(timingContext = null, offsetMs = 0, previousTiming = emptyList())
    }
    val file = lyrics.origin == LyricsOrigin.FILE
    val label = if (file) "File lyrics" else lyrics.provider ?: "Online lyrics"
    val context = LyricsTimingContext.create(song, text, if (file) "file" else lyrics.sourceId ?: label, label)
    return lyrics.copy(
        timingContext = context,
        offsetMs = LyricsTimingOffset.clamp(saved[context.id]?.offsetMs ?: 0),
        previousTiming =
            saved.values
                .filter {
                    it.songKey == context.songKey && it.sourceKey != context.sourceKey
                }.sortedByDescending { it.savedAtMs },
    )
}
