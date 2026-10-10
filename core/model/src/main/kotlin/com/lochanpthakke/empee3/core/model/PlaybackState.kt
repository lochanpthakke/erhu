package com.lochanpthakke.empee3.core.model

data class PlaybackState(
    val currentSong: Song? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** The play queue in exact physical playback order. */
    val queue: List<Song> = emptyList(),
    /** Index of [currentSong] within [queue], or -1 when the queue is empty. */
    val currentIndex: Int = -1,
    /** Index in [queue] of the song that plays next, or -1. */
    val nextIndex: Int = -1,
    /** Index in [queue] of the song before this one in physical play order, or -1. */
    val previousIndex: Int = -1,
    /** Tracks that started playing this session, most recent first (not part of [queue]). */
    val history: List<QueueHistoryEntry> = emptyList(),
    /** The current track's A–B loop markers, or null when none are set. */
    val abLoop: AbLoopState? = null,
) {
    val nextSong: Song? get() = queue.getOrNull(nextIndex)
    val previousSong: Song? get() = queue.getOrNull(previousIndex)
}
