package com.erhu.erhu.core.playback

import com.erhu.erhu.core.model.QueueHistoryEntry
import com.erhu.erhu.core.model.Song

/**
 * Tracks played during this listening session, fed by the player's own callbacks.
 *
 * A track is recorded once each time it becomes the current item AND actually plays:
 * skipping through tracks while paused, seeking, queue edits and other state updates
 * that do not change the current item add nothing. Playing the same song again (repeat,
 * or picking it again) adds a new entry with its own id. Kept in memory only, newest first,
 * up to [capacity] entries.
 */
internal class SessionHistory(
    private val now: () -> Long,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private var pending: Song? = null
    private var nextId = 1L

    var entries: List<QueueHistoryEntry> = emptyList()
        private set

    /** A new item became current ([song] null: none). Returns true when [entries] changed. */
    fun onItemBecameCurrent(
        song: Song?,
        isPlaying: Boolean,
    ): Boolean {
        pending = song
        return if (isPlaying) onPlaybackStarted() else false
    }

    /** Playback started or resumed. Returns true when [entries] changed. */
    fun onPlaybackStarted(): Boolean {
        val song = pending ?: return false
        pending = null
        entries = (listOf(QueueHistoryEntry(nextId++, song, now())) + entries).take(capacity)
        return true
    }

    fun find(entryId: Long): QueueHistoryEntry? = entries.find { it.entryId == entryId }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
