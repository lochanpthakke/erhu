package com.erhu.erhu.core.model

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
    /**
     * The collection each queue entry was started from ([CollectionRef.key]), parallel to
     * [queueSongIds]; "" for entries added outside an album or playlist. Empty for older snapshots.
     */
    val queueCollectionKeys: List<String> = emptyList(),
)
