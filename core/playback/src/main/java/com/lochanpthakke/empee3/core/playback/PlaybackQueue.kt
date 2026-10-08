package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Queue mutations on top of the Media3 playlist. The player's own playlist is
 * the single source of truth; this class only adds bounds checking and physical
 * queue shuffling for linear sequential playback.
 */
internal class PlaybackQueue(
    private val player: Player,
) {
    fun addToQueue(mediaItem: MediaItem) {
        player.addMediaItem(mediaItem)
    }

    fun playNext(mediaItem: MediaItem) {
        val insertIndex =
            if (player.mediaItemCount == 0) {
                0
            } else {
                (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            }
        player.addMediaItem(insertIndex, mediaItem)
    }

    fun reorder(
        from: Int,
        to: Int,
    ) {
        val count = player.mediaItemCount
        require(from in 0 until count && to in 0 until count) {
            "Invalid reorder indices: $from -> $to (queue size $count)"
        }
        player.moveMediaItem(from, to)
    }

    fun removeAt(index: Int) {
        require(index in 0 until player.mediaItemCount) { "Invalid queue index: $index" }
        player.removeMediaItem(index)
    }

    /**
     * Physically shuffles all other items in the queue around the currently playing item.
     * The current song becomes Index 0, and all other songs in the queue are randomized
     * sequentially after it ([0, U_1, U_2, U_3, ...]). This ensures linear playback (1 -> 2 -> 3)
     * without ExoPlayer random index jumping, and makes drag-and-drop reordering 100% functional.
     */
    fun shuffleFullQueueFromCurrent() {
        val count = player.mediaItemCount
        if (count <= 1) return
        val current = player.currentMediaItemIndex.coerceIn(0, count - 1)
        val currentItem = player.getMediaItemAt(current)

        val items = List(count) { player.getMediaItemAt(it) }
        val restShuffled = items.filterIndexed { index, _ -> index != current }.shuffled()

        val shuffledQueue = listOf(currentItem) + restShuffled
        player.setMediaItems(shuffledQueue, 0, player.currentPosition)
    }
}
