package com.lochanpthakke.empee3.core.model

/**
 * Domain snapshot of playback state persisted across app restarts.
 *
 * Carries song IDs rather than [Song] instances — rehydrating IDs into playable
 * [Song] objects requires a library lookup (Room, added in Phase 3) and is
 * deliberately out of scope here. See [SongLookup].
 */
data class PersistedPlaybackState(
    val queueSongIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: RepeatMode,
)
