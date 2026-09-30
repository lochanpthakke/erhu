package com.lochanpthakke.empee3.core.model

/**
 * Domain snapshot of playback state persisted across app restarts.
 *
 * Carries song IDs rather than [Song] instances — IDs are rehydrated into
 * playable [Song] objects through [SongLookup] (Room) on restore, so songs
 * deleted from the device in the meantime are dropped rather than replayed.
 */
data class PersistedPlaybackState(
    val queueSongIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: RepeatMode,
)
