package com.lochanpthakke.empee3.core.playback

import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.QueueOrigin
import com.lochanpthakke.empee3.core.model.Song

internal data class RestoredQueue(
    val songs: List<Song>,
    val startIndex: Int,
    val startPositionMs: Long,
    /** Where each restored entry was started from (parallel to [songs]); null = an ordinary entry. */
    val origins: List<QueueOrigin?> = emptyList(),
)

/**
 * Rebuilds a playable queue from a persisted snapshot and the songs that still
 * exist in the library ([available], any order).
 *
 * Songs removed from the device since the snapshot are dropped. If the song
 * that was current is gone, playback restarts at the next surviving song
 * (or the first one) from position 0.
 */
@Suppress("ReturnCount")
internal fun buildRestoredQueue(
    state: PersistedPlaybackState,
    available: List<Song>,
): RestoredQueue? {
    if (state.queueSongIds.isEmpty()) return null
    val byId = available.associateBy { it.id }

    val songs = ArrayList<Song>(state.queueSongIds.size)
    val origins = ArrayList<QueueOrigin?>(state.queueSongIds.size)
    var restoredCurrentIndex = -1
    var nextSurvivingIndex = -1
    state.queueSongIds.forEachIndexed { originalIndex, id ->
        val song = byId[id] ?: return@forEachIndexed
        if (originalIndex == state.currentIndex) {
            restoredCurrentIndex = songs.size
        } else if (originalIndex > state.currentIndex && nextSurvivingIndex == -1) {
            nextSurvivingIndex = songs.size
        }
        songs += song
        // Older snapshots have no keys: those entries come back without a collection context.
        origins += QueueOrigin.fromKey(state.queueCollectionKeys.getOrNull(originalIndex), id)
    }
    if (songs.isEmpty()) return null

    return if (restoredCurrentIndex >= 0) {
        RestoredQueue(songs, restoredCurrentIndex, state.positionMs.coerceAtLeast(0L), origins)
    } else {
        RestoredQueue(songs, nextSurvivingIndex.coerceAtLeast(0), 0L, origins)
    }
}
