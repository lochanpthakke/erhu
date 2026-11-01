package com.erhu.erhu.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.random.Random

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

    /** Removes the entry at [index] and returns that exact item (metadata included) for undo. */
    fun removeAt(index: Int): MediaItem {
        require(index in 0 until player.mediaItemCount) { "Invalid queue index: $index" }
        val item = player.getMediaItemAt(index)
        player.removeMediaItem(index)
        return item
    }

    /**
     * Puts [item] back at [index] (clamped to the current queue). Inserting before or after
     * the current item never changes which item is playing or its position.
     */
    fun insertAt(
        index: Int,
        item: MediaItem,
    ) {
        player.addMediaItem(index.coerceIn(0, player.mediaItemCount), item)
    }

    /**
     * Shuffles only the entries after the current one, in the player's own playlist. The current
     * item and everything before it (already played) keep their places, so playback is not
     * interrupted. Returns false when fewer than two entries are upcoming.
     */
    fun shuffleUpcoming(random: Random = Random.Default): Boolean {
        val count = player.mediaItemCount
        val current = player.currentMediaItemIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
        val items = List(count) { player.getMediaItemAt(it) }
        val upcoming = shuffledUpcoming(items, current, random) ?: return false
        player.replaceMediaItems(current + 1, count, upcoming)
        return true
    }

    /**
     * Replaces everything after the current item with [items] (Smart Radio's picks). The current
     * item and the played part keep their places, so playback is not interrupted. On an empty
     * queue [items] become the queue.
     */
    fun replaceUpcoming(items: List<MediaItem>) {
        val count = player.mediaItemCount
        if (count == 0) {
            player.setMediaItems(items)
            return
        }
        player.replaceMediaItems(upcomingStart(count, player.currentMediaItemIndex), count, items)
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
