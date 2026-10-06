package com.lochanpthakke.empee3.core.model

data class PlaybackState(
    val currentSong: Song? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** The play queue in playback-list order (not shuffle order). */
    val queue: List<Song> = emptyList(),
    /** Index of [currentSong] within [queue], or -1 when the queue is empty. */
    val currentIndex: Int = -1,
    /** Index in [queue] of the song that plays next (follows shuffle and repeat), or -1. */
    val nextIndex: Int = -1,
    /** Index in [queue] of the song before this one in play order (follows shuffle and repeat), or -1. */
    val previousIndex: Int = -1,
) {
    val nextSong: Song? get() = queue.getOrNull(nextIndex)
    val previousSong: Song? get() = queue.getOrNull(previousIndex)
}
