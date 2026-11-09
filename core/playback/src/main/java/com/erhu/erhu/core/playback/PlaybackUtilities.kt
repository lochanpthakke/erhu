/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Future Android releases from 1.0.18: proprietary terms in LICENSE.
 * Historical GPL grants are preserved in LICENSES/GPL-3.0-historical.txt.
 */

package com.erhu.erhu.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.erhu.erhu.core.model.AudioUtilities
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.ReplayGainData
import com.erhu.erhu.core.sound.repository.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Uses the same session for Settings, widgets, lock screen and background playback. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackUtilities(
    private val player: Player,
    private val sound: SoundRepository,
    private val eq: CustomEqAudioProcessor,
    private val scope: CoroutineScope,
    private val transportChanged: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var previous = AudioUtilities()
    private var deadline = 0L
    private var timer: Job? = null
    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_TRACKS_CHANGED)) {
                    eq.setReplayGain(replayGainFromTracks(player.currentTracks))
                }
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    sound.soundSettings.value.utilities.sleepAtTrackEnd
                ) {
                    clearTimer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && sound.soundSettings.value.utilities.sleepAtTrackEnd) clearTimer()
            }
        }

    fun start() {
        player.addListener(listener)
        scope.launch {
            sound.soundSettings.map { it.utilities }.distinctUntilChanged().collect { u ->
                val parameters = PlaybackParameters(u.speed, u.pitch)
                if (player.playbackParameters != parameters) player.playbackParameters = parameters
                (player as? ExoPlayer)?.let {
                    if (it.skipSilenceEnabled != u.skipSilence) it.skipSilenceEnabled = u.skipSilence
                    if (it.pauseAtEndOfMediaItems != u.sleepAtTrackEnd) it.setPauseAtEndOfMediaItems(u.sleepAtTrackEnd)
                }
                if (previous.speed != u.speed ||
                    previous.pitch != u.pitch ||
                    previous.skipSilence != u.skipSilence ||
                    previous.sleepAtTrackEnd != u.sleepAtTrackEnd
                ) {
                    transportChanged()
                }
                previous = u
                if (deadline != u.sleepDeadlineMs) {
                    deadline = u.sleepDeadlineMs
                    timer?.cancel()
                    if (deadline > 0) {
                        val expected = deadline
                        timer =
                            scope.launch {
                                delay(remainingSleepMillis(expected, now()))
                                if (sound.soundSettings.value.utilities.sleepDeadlineMs == expected) {
                                    player.pause()
                                    clearTimer()
                                }
                            }
                    }
                }
            }
        }
    }

    private fun clearTimer() =
        sound.setUtilities(
            sound.soundSettings.value.utilities
                .copy(sleepDeadlineMs = 0, sleepAtTrackEnd = false),
        )
}

internal fun remainingSleepMillis(
    deadline: Long,
    now: Long,
): Long = (deadline - now).coerceAtLeast(0)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun replayGainFromTracks(tracks: Tracks): ReplayGainData {
    val tags = mutableMapOf<String, String>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val metadata = group.getTrackFormat(i).metadata ?: continue
            for (j in 0 until metadata.length()) {
                when (val entry = metadata[j]) {
                    is VorbisComment -> tags[entry.key] = entry.value
                    is TextInformationFrame -> entry.description?.let { tags[it] = entry.values.firstOrNull().orEmpty() }
                    is InternalFrame -> tags[entry.description] = entry.text
                }
            }
        }
    }
    return ReplayGainData.fromTags(tags)
}
