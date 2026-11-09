package com.erhu.erhu.feature.settings.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType
import com.erhu.erhu.core.sound.model.SoundSettings
import com.erhu.erhu.core.sound.repository.SoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundViewModel
    @Inject
    constructor(
        private val soundRepository: SoundRepository,
        val customEqAudioProcessor: CustomEqAudioProcessor,
        private val transitions: com.erhu.erhu.core.playback.transition.TransitionSettingsRepository,
        playback: com.erhu.erhu.core.playback.repository.PlaybackRepository,
    ) : ViewModel() {
        val soundSettings: StateFlow<SoundSettings> = soundRepository.soundSettings

        val transitionConfig = transitions.config
        val playbackState = playback.playbackState

        fun updateUtilities(change: (com.erhu.erhu.core.model.AudioUtilities) -> com.erhu.erhu.core.model.AudioUtilities) {
            soundRepository.setUtilities(change(soundSettings.value.utilities).sanitized())
        }

        fun setTransitionMaster(enabled: Boolean) = transitions.setGlobal(transitionConfig.value.global.copy(masterEnabled = enabled))

        fun setDefaultCrossfade(enabled: Boolean) {
            val global = transitionConfig.value.global
            val choice =
                if (enabled) {
                    com.erhu.erhu.core.model.TransitionChoice
                        .Custom(
                            com.erhu.erhu.core.model
                                .TransitionSpec(),
                        )
                } else {
                    com.erhu.erhu.core.model.TransitionChoice.Off
                }
            transitions.setGlobal(global.copy(default = choice))
        }

        fun setCrossfadeDuration(ms: Int) {
            val global = transitionConfig.value.global
            val base =
                (global.default as? com.erhu.erhu.core.model.TransitionChoice.Custom)?.spec ?: com.erhu.erhu.core.model
                    .TransitionSpec()
            transitions.setGlobal(
                global.copy(
                    default =
                        com.erhu.erhu.core.model.TransitionChoice
                            .Custom(base.copy(durationMs = ms)),
                ),
            )
        }

        fun setSleepMinutes(minutes: Int) {
            require(minutes in 1..720)
            updateUtilities { it.copy(sleepDeadlineMs = System.currentTimeMillis() + minutes * 60_000L, sleepAtTrackEnd = false) }
        }

        fun setEqEnabled(enabled: Boolean) {
            soundRepository.setEqEnabled(enabled)
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            soundRepository.setBandGain(bandIndex, gainDb)
        }

        fun setBandFrequency(
            bandIndex: Int,
            freqHz: Float,
        ) {
            soundRepository.setBandFrequency(bandIndex, freqHz)
        }

        fun setBandQ(
            bandIndex: Int,
            q: Float,
        ) {
            soundRepository.setBandQ(bandIndex, q)
        }

        fun setFilterType(
            bandIndex: Int,
            type: FilterType,
        ) {
            soundRepository.setFilterType(bandIndex, type)
        }

        fun setActiveBandCount(count: Int) {
            soundRepository.setActiveBandCount(count)
        }

        fun setSelectedBandIndex(index: Int) {
            soundRepository.setSelectedBandIndex(index)
        }

        fun setBandFrequencyAndGain(
            bandIndex: Int,
            freqHz: Float,
            gainDb: Float,
        ) {
            soundRepository.setBandFrequencyAndGain(bandIndex, freqHz, gainDb)
        }

        fun applyPreset(presetId: String) {
            soundRepository.applyPreset(presetId)
        }

        fun resetToFlat() {
            soundRepository.resetToFlat()
        }

        fun saveCustomPreset(name: String) {
            viewModelScope.launch {
                soundRepository.saveCustomPreset(name)
            }
        }

        fun deleteCustomPreset(presetId: String) {
            viewModelScope.launch {
                soundRepository.deleteCustomPreset(presetId)
            }
        }
    }
