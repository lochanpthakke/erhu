package com.lochanpthakke.empee3.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.datastore.VisualizerSettingsPersistence
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.VisualizerMode
import com.lochanpthakke.empee3.core.model.VisualizerSensitivity
import com.lochanpthakke.empee3.core.model.VisualizerSettings
import com.lochanpthakke.empee3.core.model.VisualizerSmoothing
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import com.lochanpthakke.empee3.core.sound.visualizer.PlaybackAudioTap
import com.lochanpthakke.empee3.core.sound.visualizer.PlaybackClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Visualizer preferences, plus what the visualizer reads from playback (read-only: it never
 * changes playback, the EQ or the audio). The audio tap is only collected while a visualizer
 * screen is visible.
 */
@HiltViewModel
class VisualizerViewModel
    @Inject
    constructor(
        val audioTap: PlaybackAudioTap,
        private val persistence: VisualizerSettingsPersistence,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        val settings: StateFlow<VisualizerSettings> =
            persistence.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VisualizerSettings())

        /** Playback as the visualizer sees it right now (call on the main thread). */
        fun clock(): PlaybackClock {
            val state = playbackRepository.playbackState.value
            return PlaybackClock(
                isPlaying = state.status == PlaybackStatus.PLAYING,
                positionMs = playbackRepository.currentPositionMs(),
                entryKey = state.currentIndex to state.currentSong?.id,
            )
        }

        fun setMode(mode: VisualizerMode) = update { it.copy(mode = mode) }

        fun setSensitivity(sensitivity: VisualizerSensitivity) = update { it.copy(sensitivity = sensitivity) }

        fun setSmoothing(smoothing: VisualizerSmoothing) = update { it.copy(smoothing = smoothing) }

        fun setReduceMotion(reduce: Boolean) = update { it.copy(reduceMotion = reduce) }

        private fun update(change: (VisualizerSettings) -> VisualizerSettings) {
            val next = change(settings.value)
            viewModelScope.launch { persistence.save(next) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
