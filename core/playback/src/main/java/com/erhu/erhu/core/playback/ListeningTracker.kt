package com.erhu.erhu.core.playback

/**
 * Turns player callbacks into listening events for the Sound Capsule statistics.
 *
 * A play is one start of a song that was actually listened to for at least half of
 * its length. Every time the player moves to a song (a new queue, a skip, the queue
 * advancing, Repeat One looping) a new listen of that song begins; the time the player
 * reports it is playing is added up across pauses, and the listen counts once, the
 * moment it reaches [PLAYED_FRACTION] of the song's duration. It is real playing time,
 * not the playback position: seeking ahead does not make a song count, and pause /
 * resume, seeking, audio-focus resume or a configuration change never start a new
 * listen. A song skipped before half of it was heard does not count. When the queue
 * ends, playing the song again is a new listen.
 *
 * Listening time is the same playing time, recorded when the player stops playing,
 * moves to another song, or at a [checkpoint].
 *
 * Plain state machine without Android types, driven on the player's thread. Durations
 * of 0 or less are unknown: such a listen cannot count until the duration is known.
 */
class ListeningTracker(
    private val elapsedRealtime: () -> Long,
    private val onPlayCounted: (songId: Long) -> Unit,
    private val onListened: (songId: Long, durationMs: Long) -> Unit,
) {
    private var songId: Long? = null
    private var durationMs = 0L

    /** Playing time of the current listen, without the open segment. */
    private var heardMs = 0L

    /** The current listen has counted (or there is nothing to count). */
    private var counted = true

    /** When playback of the current song started, while it is playing. */
    private var segmentStart: Long? = null

    /** The player moved to [songId] (null: no current item): a new listen begins. */
    fun onItemChanged(
        songId: Long?,
        durationMs: Long,
        isPlaying: Boolean,
    ) {
        closeSegment()
        this.songId = songId
        this.durationMs = durationMs
        heardMs = 0L
        counted = songId == null
        if (isPlaying) openSegment()
    }

    fun onPlayingChanged(
        isPlaying: Boolean,
        durationMs: Long,
    ) {
        updateDuration(durationMs)
        if (isPlaying) openSegment() else closeSegment()
    }

    /** While playing (the position poll): counts the listen as soon as it reaches half the song. */
    fun onProgress(durationMs: Long) {
        updateDuration(durationMs)
        val open = segmentStart?.let { elapsedRealtime() - it } ?: 0L
        countIfHeard(heardMs + open)
    }

    /** The queue played to its end: playing the song again is a new listen. */
    fun onEnded() {
        closeSegment()
        heardMs = 0L
        counted = songId == null
    }

    /** Records the listening time so far (bounds what a killed process can lose). */
    fun checkpoint() {
        if (segmentStart == null) return
        closeSegment()
        openSegment()
    }

    private fun updateDuration(durationMs: Long) {
        if (durationMs > 0L) this.durationMs = durationMs
    }

    private fun openSegment() {
        if (songId == null || segmentStart != null) return
        segmentStart = elapsedRealtime()
    }

    /** A segment is only ever open for a current song (see [openSegment]). */
    private fun closeSegment() {
        val start = segmentStart ?: return
        segmentStart = null
        val id = songId
        val segment = elapsedRealtime() - start
        if (id != null && segment > 0L) {
            heardMs += segment
            onListened(id, segment)
            countIfHeard(heardMs)
        }
    }

    private fun countIfHeard(heard: Long) {
        val id = songId ?: return
        if (counted || durationMs <= 0L) return
        if (heard >= durationMs * PLAYED_FRACTION) {
            counted = true
            onPlayCounted(id)
        }
    }

    companion object {
        /** Share of a song that must actually be heard for the listen to count as a play. */
        const val PLAYED_FRACTION = 0.5
    }
}
