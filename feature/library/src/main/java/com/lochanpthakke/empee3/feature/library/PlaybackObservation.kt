package com.lochanpthakke.empee3.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository

/*
 * The playback position ticks four times per second. Composables that read the
 * full PlaybackState in composition recompose on every tick, so observation is
 * split with derivedStateOf: a reader is only invalidated when the value it
 * reads actually changes. Collecting the flow itself does not recompose the
 * caller; only reading `.value` in composition does.
 */

/**
 * Playback state without the position: changes only when the song, status,
 * queue, duration or shuffle/repeat mode change.
 */
@Composable
fun PlaybackRepository.collectStablePlaybackState(): State<PlaybackState> {
    val full = playbackState.collectAsState()
    return remember(full) { derivedStateOf { full.value.copy(positionMs = 0L) } }
}

/** Current playback position in ms. Read `.value` as late as possible (e.g. in a lambda). */
@Composable
fun PlaybackRepository.collectPlaybackPosition(): State<Long> {
    val full = playbackState.collectAsState()
    return remember(full) { derivedStateOf { full.value.positionMs } }
}
