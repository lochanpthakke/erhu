@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.repository

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.onError
import com.lochanpthakke.empee3.core.datastore.SoundSettingsPersistence
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectCapabilities
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectManager
import com.lochanpthakke.empee3.core.sound.model.EqPreset
import com.lochanpthakke.empee3.core.sound.model.SoundSettings
import com.lochanpthakke.empee3.core.sound.model.toPersisted
import com.lochanpthakke.empee3.core.sound.model.toSoundSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the sound settings and pushes them into the audio pipeline.
 *
 * Every setter updates the EQ processor / audio effects synchronously, so a
 * change is audible immediately; persistence happens afterwards, debounced.
 *
 * Startup: the saved settings are read once and applied. Nothing is written
 * until that restore has finished, so the built-in defaults can never
 * overwrite what the user saved. If the user changes something before the
 * restore completes, that change wins (saved custom presets are still kept).
 * All state changes run on the main dispatcher, so restore and setters never
 * interleave.
 */
@Singleton
@Suppress("TooManyFunctions", "TooGenericExceptionThrown")
@OptIn(FlowPreview::class)
class SoundRepositoryImpl
    @Inject
    constructor(
        private val customEqAudioProcessor: CustomEqAudioProcessor,
        private val effectManager: AudioEffectManager,
        private val persistence: SoundSettingsPersistence,
        dispatchers: DispatcherProvider,
        private val logger: Logger,
    ) : SoundRepository {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

        private val _soundSettings = MutableStateFlow(SoundSettings())
        override val soundSettings: StateFlow<SoundSettings> = _soundSettings.asStateFlow()

        private val _capabilities = MutableStateFlow(AudioEffectCapabilities())
        override val capabilities: StateFlow<AudioEffectCapabilities> = _capabilities.asStateFlow()

        private var restoreFinished = false
        private var changedBeforeRestore = false
        private val pendingSave = MutableStateFlow<SoundSettings?>(null)

        init {
            scope.launch { restoreSavedSettings() }
            scope.launch {
                pendingSave
                    .filterNotNull()
                    .debounce(SAVE_DEBOUNCE_MS)
                    .collect { settings ->
                        persistence.save(settings.toPersisted()).onError {
                            logger.w(TAG, "Failed to save sound settings: ${it.message}", it.cause)
                        }
                    }
            }
        }

        private suspend fun restoreSavedSettings() {
            val saved =
                try {
                    persistence.observe().first()?.toSoundSettings()
                } catch (e: Exception) {
                    logger.w(TAG, "Could not read saved sound settings, using defaults: ${e.message}", e)
                    null
                }
            if (saved != null) {
                if (changedBeforeRestore) {
                    // Keep the user's fresh edits but don't lose presets they saved earlier.
                    val known =
                        _soundSettings.value.customPresets
                            .map { it.id }
                            .toSet()
                    _soundSettings.update { current ->
                        current.copy(customPresets = saved.customPresets.filter { it.id !in known } + current.customPresets)
                    }
                } else {
                    applyToPipeline(saved)
                    _soundSettings.value = saved.copy(currentBandGains = customEqAudioProcessor.getBandGains())
                }
            }
            restoreFinished = true
            if (changedBeforeRestore) requestSave()
        }

        /** Pushes every persisted value into the processor and the system effects. */
        private fun applyToPipeline(settings: SoundSettings) {
            customEqAudioProcessor.setBandGains(settings.currentBandGains)
            customEqAudioProcessor.setEqEnabled(settings.isEqEnabled)
            effectManager.setBassBoost(settings.isBassBoostEnabled, settings.bassBoostStrength)
            effectManager.setVirtualizer(settings.isVirtualizerEnabled, settings.virtualizerStrength)
            effectManager.setLoudnessEnhancer(settings.isLoudnessEnabled, settings.loudnessGainMb)
        }

        private fun onUserChange() {
            if (restoreFinished) requestSave() else changedBeforeRestore = true
        }

        private fun requestSave() {
            pendingSave.value = _soundSettings.value
        }

        override fun attachAudioSession(audioSessionId: Int) {
            val caps = effectManager.attachToAudioSession(audioSessionId)
            _capabilities.value = caps
        }

        override fun setEqEnabled(enabled: Boolean) {
            customEqAudioProcessor.setEqEnabled(enabled)
            _soundSettings.update { it.copy(isEqEnabled = enabled) }
            onUserChange()
        }

        override fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            customEqAudioProcessor.setBandGain(bandIndex, gainDb)
            val currentGains = customEqAudioProcessor.getBandGains()
            _soundSettings.update { it.copy(currentBandGains = currentGains) }
            onUserChange()
        }

        override fun applyPreset(presetId: String) {
            val targetPreset = _soundSettings.value.allPresets.find { it.id == presetId } ?: return
            customEqAudioProcessor.setBandGains(targetPreset.bandGains)
            _soundSettings.update {
                it.copy(
                    activePresetId = presetId,
                    currentBandGains = customEqAudioProcessor.getBandGains(),
                )
            }
            onUserChange()
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
                onUserChange()
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
                onUserChange()
            }

        override fun setBassBoost(
            enabled: Boolean,
            strength: Short,
        ) {
            effectManager.setBassBoost(enabled, strength)
            _soundSettings.update {
                it.copy(isBassBoostEnabled = enabled, bassBoostStrength = strength)
            }
            onUserChange()
        }

        override fun setVirtualizer(
            enabled: Boolean,
            strength: Short,
        ) {
            effectManager.setVirtualizer(enabled, strength)
            _soundSettings.update {
                it.copy(isVirtualizerEnabled = enabled, virtualizerStrength = strength)
            }
            onUserChange()
        }

        override fun setLoudnessEnhancer(
            enabled: Boolean,
            gainMb: Int,
        ) {
            effectManager.setLoudnessEnhancer(enabled, gainMb)
            _soundSettings.update {
                it.copy(isLoudnessEnabled = enabled, loudnessGainMb = gainMb)
            }
            onUserChange()
        }

        companion object {
            private const val TAG = "SoundRepository"

            /** Slider drags fire many changes per second; write once they settle. */
            internal const val SAVE_DEBOUNCE_MS = 400L
        }
    }
