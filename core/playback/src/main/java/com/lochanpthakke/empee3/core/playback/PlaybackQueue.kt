package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Queue mutations on top of the Media3 playlist. The player's own playlist is
 * the single source of truth; this class only adds bounds checking.
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
}
