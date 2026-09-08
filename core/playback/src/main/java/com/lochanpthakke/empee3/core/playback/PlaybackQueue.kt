package com.lochanpthakke.empee3.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal class PlaybackQueue(
    private val player: Player,
) {
    private val history: MutableList<MediaItem> = mutableListOf()

    fun addToQueue(mediaItem: MediaItem) {
        player.addMediaItem(mediaItem)
    }

    fun playNext(mediaItem: MediaItem) {
        val insertIndex = player.currentMediaItemIndex + 1
        player.addMediaItem(insertIndex, mediaItem)
    }

    fun reorder(
        from: Int,
        to: Int,
    ) {
        player.moveMediaItem(from, to)
    }

    fun removeAt(index: Int) {
        player.removeMediaItem(index)
    }

    fun pushHistory(mediaItem: MediaItem) {
        history.add(mediaItem)
    }

    fun popHistory(): MediaItem? {
        if (history.isEmpty()) return null
        return history.removeAt(history.lastIndex)
    }

    fun clearHistory() {
        history.clear()
    }
}
