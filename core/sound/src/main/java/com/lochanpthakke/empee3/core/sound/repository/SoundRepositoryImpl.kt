@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectCapabilities
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectManager
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions", "TooGenericExceptionThrown")
class SoundRepositoryImpl
    @Inject
    constructor(
        private val customEqAudioProcessor: CustomEqAudioProcessor,
        private val effectManager: AudioEffectManager,
    ) : SoundRepository {
        private val _soundSettings = MutableStateFlow(SoundSettings())
        override val soundSettings: StateFlow<SoundSettings> = _soundSettings.asStateFlow()

        private val _capabilities = MutableStateFlow(AudioEffectCapabilities())
        override val capabilities: StateFlow<AudioEffectCapabilities> = _capabilities.asStateFlow()

        override fun attachAudioSession(audioSessionId: Int) {
            val caps = effectManager.attachToAudioSession(audioSessionId)
            _capabilities.value = caps
        }

        override fun setEqEnabled(enabled: Boolean) {
            customEqAudioProcessor.setEqEnabled(enabled)
            _soundSettings.update { it.copy(isEqEnabled = enabled) }
        }

        override fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            customEqAudioProcessor.setBandGain(bandIndex, gainDb)
            val currentGains = customEqAudioProcessor.getBandGains()
            _soundSettings.update { it.copy(currentBandGains = currentGains) }
        }

        override fun applyPreset(presetId: String) {
            val targetPreset = _soundSettings.value.allPresets.find { it.id == presetId } ?: return
            customEqAudioProcessor.setBandGains(targetPreset.bandGains)
            _soundSettings.update {
                it.copy(
                    activePresetId = presetId,
                    currentBandGains = targetPreset.bandGains.copyOf(),
                )
            }
        }

        override fun resetToFlat() {
            applyPreset(EqPreset.FLAT.id)
        }

        override suspend fun saveCustomPreset(name: String): AppResult<EqPreset> =
            appResultOf {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Preset name cannot be blank")
                }
                val newPreset =
                    EqPreset(
                        id = "custom_${UUID.randomUUID()}",
                        name = name.trim(),
                        bandGains = customEqAudioProcessor.getBandGains(),
                        isCustom = true,
                    )
                _soundSettings.update {
                    it.copy(
                        customPresets = it.customPresets + newPreset,
                        activePresetId = newPreset.id,
                    )
                }
                newPreset
            }

        override suspend fun deleteCustomPreset(presetId: String): AppResult<Unit> =
            appResultOf {
                if (_soundSettings.value.customPresets.none { it.id == presetId }) {
                    throw IllegalArgumentException("Preset not found: $presetId")
                }

                _soundSettings.update {
                    val updatedCustoms = it.customPresets.filterNot { p -> p.id == presetId }
                    val nextActiveId = if (it.activePresetId == presetId) EqPreset.FLAT.id else it.activePresetId
                    it.copy(customPresets = updatedCustoms, activePresetId = nextActiveId)
                }

                if (_soundSettings.value.activePresetId == EqPreset.FLAT.id) {
                    applyPreset(EqPreset.FLAT.id)
                }
            }

        override fun setBassBoost(
            enabled: Boolean,
            strength: Short,
        ) {
            effectManager.setBassBoost(enabled, strength)
            _soundSettings.update {
                it.copy(isBassBoostEnabled = enabled, bassBoostStrength = strength)
            }
        }

        override fun setVirtualizer(
            enabled: Boolean,
            strength: Short,
        ) {
            effectManager.setVirtualizer(enabled, strength)
            _soundSettings.update {
                it.copy(isVirtualizerEnabled = enabled, virtualizerStrength = strength)
            }
        }

        override fun setLoudnessEnhancer(
            enabled: Boolean,
            gainMb: Int,
        ) {
            effectManager.setLoudnessEnhancer(enabled, gainMb)
            _soundSettings.update {
                it.copy(isLoudnessEnabled = enabled, loudnessGainMb = gainMb)
            }
        }
    }
