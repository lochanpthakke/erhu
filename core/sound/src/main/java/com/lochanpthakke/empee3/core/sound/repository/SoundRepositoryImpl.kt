@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.sound.repository

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.onError
import com.lochanpthakke.empee3.core.datastore.SoundSettingsPersistence
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import com.lochanpthakke.empee3.core.sound.dsp.FilterType
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectCapabilities
import com.lochanpthakke.empee3.core.sound.effects.AudioEffectManager
import com.lochanpthakke.empee3.core.sound.model.BandConfig
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
                    val known =
                        _soundSettings.value.customPresets
                            .map { it.id }
                            .toSet()
                    _soundSettings.update { current ->
                        current.copy(customPresets = saved.customPresets.filter { it.id !in known } + current.customPresets)
                    }
                } else {
                    applyToPipeline(saved)
                    _soundSettings.value = saved
                }
            }
            restoreFinished = true
            if (changedBeforeRestore) requestSave()
        }

        private fun applyToPipeline(settings: SoundSettings) {
            customEqAudioProcessor.configureParametricState(
                gainsDb = settings.currentBandGains,
                frequenciesHz = settings.currentBandFrequencies,
                qs = settings.currentBandQs,
                types = settings.currentFilterTypes,
                activeCount = settings.activeBandCount,
            )
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
            if (bandIndex !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            customEqAudioProcessor.setBandGain(bandIndex, gainDb)
            _soundSettings.update { settings ->
                val updatedBands = settings.bands.toMutableList()
                val current = updatedBands[bandIndex]
                updatedBands[bandIndex] = current.copy(gainDb = gainDb.coerceIn(-15f, 15f))
                settings.copy(bands = updatedBands)
            }
            onUserChange()
        }

        override fun setBandFrequency(
            bandIndex: Int,
            freqHz: Float,
        ) {
            val currentGain =
                _soundSettings.value.bands
                    .getOrNull(bandIndex)
                    ?.gainDb ?: 0f
            setBandFrequencyAndGain(bandIndex, freqHz, currentGain)
        }

        override fun setBandQ(
            bandIndex: Int,
            q: Float,
        ) {
            if (bandIndex !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            customEqAudioProcessor.setBandQ(bandIndex, q)
            _soundSettings.update { settings ->
                val updatedBands = settings.bands.toMutableList()
                val current = updatedBands[bandIndex]
                updatedBands[bandIndex] = current.copy(q = q.coerceIn(CustomEqAudioProcessor.MIN_Q, CustomEqAudioProcessor.MAX_Q))
                settings.copy(bands = updatedBands)
            }
            onUserChange()
        }

        override fun setFilterType(
            bandIndex: Int,
            type: FilterType,
        ) {
            if (bandIndex !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            customEqAudioProcessor.setFilterType(bandIndex, type)
            _soundSettings.update { settings ->
                val updatedBands = settings.bands.toMutableList()
                val current = updatedBands[bandIndex]
                updatedBands[bandIndex] = current.copy(filterType = type)
                settings.copy(bands = updatedBands)
            }
            onUserChange()
        }

        override fun setActiveBandCount(count: Int) {
            val safeCount = count.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, CustomEqAudioProcessor.BAND_COUNT)
            customEqAudioProcessor.setActiveBandCount(safeCount)
            _soundSettings.update { settings ->
                val nextSelected = settings.selectedBandIndex.coerceIn(0, safeCount - 1)
                settings.copy(activeBandCount = safeCount, selectedBandIndex = nextSelected)
            }
            onUserChange()
        }

        override fun setSelectedBandIndex(index: Int) {
            _soundSettings.update { settings ->
                val safeIndex = index.coerceIn(0, settings.activeBandCount - 1)
                settings.copy(selectedBandIndex = safeIndex)
            }
        }

        override fun setBandFrequencyAndGain(
            bandIndex: Int,
            freqHz: Float,
            gainDb: Float,
        ) {
            if (bandIndex !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            _soundSettings.update { settings ->
                val bands = settings.bands.toMutableList()
                val targetFreq = freqHz.coerceIn(CustomEqAudioProcessor.MIN_FREQ_HZ, CustomEqAudioProcessor.MAX_FREQ_HZ)
                val targetGain = gainDb.coerceIn(-15f, 15f)

                bands[bandIndex] = bands[bandIndex].copy(frequencyHz = targetFreq, gainDb = targetGain)

                val minRatio = 1.05f

                // Push higher bands (scooch right)
                for (i in (bandIndex + 1) until settings.activeBandCount) {
                    val minAllowed = (bands[i - 1].frequencyHz * minRatio).coerceAtMost(CustomEqAudioProcessor.MAX_FREQ_HZ)
                    if (bands[i].frequencyHz < minAllowed) {
                        bands[i] = bands[i].copy(frequencyHz = minAllowed)
                    }
                }

                // Push lower bands (scooch left)
                for (i in (bandIndex - 1) downTo 0) {
                    val maxAllowed = (bands[i + 1].frequencyHz / minRatio).coerceAtLeast(CustomEqAudioProcessor.MIN_FREQ_HZ)
                    if (bands[i].frequencyHz > maxAllowed) {
                        bands[i] = bands[i].copy(frequencyHz = maxAllowed)
                    }
                }

                val updated = settings.copy(bands = bands)
                customEqAudioProcessor.configureParametricState(
                    gainsDb = updated.currentBandGains,
                    frequenciesHz = updated.currentBandFrequencies,
                    qs = updated.currentBandQs,
                    types = updated.currentFilterTypes,
                    activeCount = updated.activeBandCount,
                )
                updated
            }
            onUserChange()
        }

        override fun applyPreset(presetId: String) {
            val targetPreset = _soundSettings.value.allPresets.find { it.id == presetId } ?: return
            _soundSettings.update { settings ->
                val updatedBands =
                    List(CustomEqAudioProcessor.BAND_COUNT) { i ->
                        BandConfig(
                            frequencyHz = targetPreset.bandFrequencies.getOrElse(i) { CustomEqAudioProcessor.BAND_FREQUENCIES[i] },
                            gainDb = targetPreset.bandGains.getOrElse(i) { 0f },
                            q = targetPreset.bandQs.getOrElse(i) { CustomEqAudioProcessor.DEFAULT_Q },
                            filterType = targetPreset.filterTypes.getOrElse(i) { FilterType.PEAKING },
                        )
                    }
                val updated =
                    settings.copy(
                        activePresetId = presetId,
                        bands = updatedBands,
                    )
                customEqAudioProcessor.configureParametricState(
                    gainsDb = updated.currentBandGains,
                    frequenciesHz = updated.currentBandFrequencies,
                    qs = updated.currentBandQs,
                    types = updated.currentFilterTypes,
                    activeCount = updated.activeBandCount,
                )
                updated
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
                val currentSettings = _soundSettings.value
                val newPreset =
                    EqPreset(
                        id = "custom_${UUID.randomUUID()}",
                        name = name.trim(),
                        bandGains = currentSettings.currentBandGains,
                        bandFrequencies = currentSettings.currentBandFrequencies,
                        bandQs = currentSettings.currentBandQs,
                        filterTypes = currentSettings.currentFilterTypes,
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
