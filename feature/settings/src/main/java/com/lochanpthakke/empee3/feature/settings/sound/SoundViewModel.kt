package com.lochanpthakke.empee3.feature.settings.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectCapabilities
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import com.lochanpthakke.empee3.core.sound.repository.SoundRepository
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
    ) : ViewModel() {
        val soundSettings: StateFlow<SoundSettings> = soundRepository.soundSettings
        val capabilities: StateFlow<AudioEffectCapabilities> = soundRepository.capabilities

        fun setEqEnabled(enabled: Boolean) {
            soundRepository.setEqEnabled(enabled)
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            soundRepository.setBandGain(bandIndex, gainDb)
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

        fun setBassBoost(
            enabled: Boolean,
            strength: Short,
        ) {
            soundRepository.setBassBoost(enabled, strength)
        }

        fun setVirtualizer(
            enabled: Boolean,
            strength: Short,
        ) {
            soundRepository.setVirtualizer(enabled, strength)
        }

        fun setLoudnessEnhancer(
            enabled: Boolean,
            gainMb: Int,
        ) {
            soundRepository.setLoudnessEnhancer(enabled, gainMb)
        }
    }
