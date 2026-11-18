@file:Suppress("detekt:All")

package com.erhu.erhu.core.sound.repository

import com.erhu.erhu.core.common.dispatcher.DispatcherProvider
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.common.result.onError
import com.erhu.erhu.core.datastore.SoundSettingsPersistence
import com.erhu.erhu.core.sound.dsp.BandFilter
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType
import com.erhu.erhu.core.sound.dsp.applySettings
import com.erhu.erhu.core.sound.model.BandConfig
import com.erhu.erhu.core.sound.model.EqMode
import com.erhu.erhu.core.sound.model.EqPreset
import com.erhu.erhu.core.sound.model.SoundSettings
import com.erhu.erhu.core.sound.model.toPersisted
import com.erhu.erhu.core.sound.model.toSoundSettings
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
        private val persistence: SoundSettingsPersistence,
        dispatchers: DispatcherProvider,
        private val logger: Logger,
    ) : SoundRepository {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

        private val _soundSettings = MutableStateFlow(SoundSettings())
        override val soundSettings: StateFlow<SoundSettings> = _soundSettings.asStateFlow()

        private var restoreFinished = false
        private var changedBeforeRestore = false
        private var utilitiesChangedBeforeRestore = false
        private var eqChangedBeforeRestore = false
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
                        val base = if (eqChangedBeforeRestore) current else saved
                        base.copy(
                            utilities = if (utilitiesChangedBeforeRestore) current.utilities else saved.utilities,
                            customPresets = saved.customPresets.filter { it.id !in known } + current.customPresets,
                        )
                    }
                    applyToPipeline(_soundSettings.value)
                } else {
                    applyToPipeline(saved)
                    _soundSettings.value = saved
                }
            }
            restoreFinished = true
            if (changedBeforeRestore) requestSave()
        }

        private fun applyToPipeline(settings: SoundSettings) {
            customEqAudioProcessor.applySettings(settings)
        }

        private fun onUserChange(utilitiesOnly: Boolean = false) {
            if (restoreFinished) {
                requestSave()
            } else {
                changedBeforeRestore = true
                if (utilitiesOnly) utilitiesChangedBeforeRestore = true else eqChangedBeforeRestore = true
            }
        }

        private fun requestSave() {
            pendingSave.value = _soundSettings.value
        }

        override fun setUtilities(value: com.erhu.erhu.core.model.AudioUtilities) {
            val clean = value.sanitized()
            _soundSettings.update { it.copy(utilities = clean) }
            customEqAudioProcessor.setUtilities(clean)
            onUserChange(utilitiesOnly = true)
        }

        override fun eqRuntimeState() = customEqAudioProcessor.runtimeState()

        override fun attachAudioSession(audioSessionId: Int) {
            // No-op (previously attached OEM AudioEffect instances)
        }

        override fun setEqEnabled(enabled: Boolean) {
            customEqAudioProcessor.setEqEnabled(enabled)
            _soundSettings.update { it.copy(isEqEnabled = enabled) }
            onUserChange()
        }

        /** Changes the parametric band [index] and applies the result. */
        private fun updateBand(
            index: Int,
            change: (BandConfig) -> BandConfig,
        ) {
            if (index !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            update { settings ->
                val bands = settings.bands.toMutableList()
                bands[index] = change(bands[index])
                settings.copy(bands = bands, activePresetId = "")
            }
        }

        /** Every EQ change goes through here: the new settings reach the processor (which fades them in). */
        private fun update(change: (SoundSettings) -> SoundSettings) {
            _soundSettings.update(change)
            applyToPipeline(_soundSettings.value)
            onUserChange()
        }

        override fun setMode(mode: EqMode) = update { it.copy(mode = mode) }

        override fun setGraphicGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            if (bandIndex !in 0 until CustomEqAudioProcessor.BAND_COUNT) return
            update { settings ->
                val gains = settings.graphicGains.toMutableList()
                gains[bandIndex] = gainDb.coerceIn(CustomEqAudioProcessor.MIN_GAIN_DB, CustomEqAudioProcessor.MAX_GAIN_DB)
                settings.copy(graphicGains = gains, graphicPresetId = "")
            }
        }

        override fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) = updateBand(bandIndex) { it.copy(gainDb = gainDb.coerceIn(CustomEqAudioProcessor.MIN_GAIN_DB, CustomEqAudioProcessor.MAX_GAIN_DB)) }

        override fun setBandFrequency(
            bandIndex: Int,
            freqHz: Float,
        ) = updateBand(bandIndex) { it.copy(frequencyHz = freqHz.coerceIn(CustomEqAudioProcessor.MIN_FREQ_HZ, CustomEqAudioProcessor.MAX_FREQ_HZ)) }

        override fun setBandQ(
            bandIndex: Int,
            q: Float,
        ) = updateBand(bandIndex) { it.copy(q = q.coerceIn(CustomEqAudioProcessor.MIN_Q, CustomEqAudioProcessor.MAX_Q)) }

        override fun setFilterType(
            bandIndex: Int,
            type: FilterType,
        ) = updateBand(bandIndex) { band ->
            // A pass filter starts as a gentle Butterworth slope; a notch starts narrow.
            when {
                type == band.filterType -> band
                type.isPass -> band.copy(filterType = type, q = PASS_Q, slope = band.slope.takeIf { it in BandFilter.SLOPES } ?: 12)
                type == FilterType.NOTCH -> band.copy(filterType = type, q = NOTCH_Q)
                else -> band.copy(filterType = type)
            }
        }

        override fun setBandEnabled(
            bandIndex: Int,
            enabled: Boolean,
        ) = updateBand(bandIndex) { it.copy(enabled = enabled) }

        override fun setBandSlope(
            bandIndex: Int,
            slope: Int,
        ) = updateBand(bandIndex) { it.copy(slope = slope.takeIf { s -> s in BandFilter.SLOPES } ?: 12) }

        override fun resetBand(bandIndex: Int) = updateBand(bandIndex) { SoundSettings.defaultBands()[bandIndex] }

        override fun setActiveBandCount(count: Int) {
            val safeCount = count.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, CustomEqAudioProcessor.BAND_COUNT)
            update { settings ->
                settings.copy(
                    activeBandCount = safeCount,
                    selectedBandIndex = settings.selectedBandIndex.coerceIn(0, safeCount - 1),
                    activePresetId = "",
                )
            }
        }

        override fun setSelectedBandIndex(index: Int) {
            _soundSettings.update { settings ->
                val safeIndex = index.coerceIn(0, settings.activeBandCount - 1)
                settings.copy(selectedBandIndex = safeIndex)
            }
        }

        /** A drag on the curve: frequency and (for gain filters) gain together. Bands may cross freely. */
        override fun setBandFrequencyAndGain(
            bandIndex: Int,
            freqHz: Float,
            gainDb: Float,
        ) = updateBand(bandIndex) { band ->
            band.copy(
                frequencyHz = freqHz.coerceIn(CustomEqAudioProcessor.MIN_FREQ_HZ, CustomEqAudioProcessor.MAX_FREQ_HZ),
                gainDb = if (band.filterType.hasGain) gainDb.coerceIn(CustomEqAudioProcessor.MIN_GAIN_DB, CustomEqAudioProcessor.MAX_GAIN_DB) else band.gainDb,
            )
        }

        override fun applyPreset(presetId: String) {
            val preset = _soundSettings.value.allPresets.find { it.id == presetId } ?: return
            update { settings ->
                if (preset.mode == EqMode.GRAPHIC) {
                    settings.copy(graphicGains = preset.bandGains.toList(), graphicPresetId = preset.id)
                } else {
                    settings.copy(
                        bands = preset.parametricBands(),
                        activeBandCount = preset.bandCount,
                        selectedBandIndex = settings.selectedBandIndex.coerceIn(0, preset.bandCount - 1),
                        activePresetId = preset.id,
                    )
                }
            }
        }

        override fun resetToFlat() =
            applyPreset(if (_soundSettings.value.mode == EqMode.GRAPHIC) EqPreset.FLAT.id else EqPreset.PARAMETRIC_FLAT.id)

        override suspend fun saveCustomPreset(name: String): AppResult<EqPreset> =
            appResultOf {
                if (name.isBlank()) {
                    throw IllegalArgumentException("Preset name cannot be blank")
                }
                val current = _soundSettings.value
                val id = "custom_${UUID.randomUUID()}"
                val newPreset =
                    if (current.mode == EqMode.GRAPHIC) {
                        EqPreset(id = id, name = name.trim(), bandGains = current.graphicGains.toFloatArray(), isCustom = true, mode = EqMode.GRAPHIC)
                    } else {
                        EqPreset(
                            id = id,
                            name = name.trim(),
                            bandGains = current.currentBandGains,
                            bandFrequencies = current.currentBandFrequencies,
                            bandQs = current.currentBandQs,
                            filterTypes = current.currentFilterTypes,
                            isCustom = true,
                            mode = EqMode.PARAMETRIC,
                            bandCount = current.activeBandCount,
                            bandEnabled = BooleanArray(current.bands.size) { current.bands[it].enabled },
                            slopes = IntArray(current.bands.size) { current.bands[it].slope },
                        )
                    }
                _soundSettings.update {
                    val withPreset = it.copy(customPresets = it.customPresets + newPreset)
                    if (current.mode == EqMode.GRAPHIC) withPreset.copy(graphicPresetId = id) else withPreset.copy(activePresetId = id)
                }
                onUserChange()
                newPreset
            }

        override suspend fun renameCustomPreset(
            presetId: String,
            name: String,
        ): AppResult<Unit> =
            appResultOf {
                if (name.isBlank()) throw IllegalArgumentException("Preset name cannot be blank")
                if (_soundSettings.value.customPresets.none { it.id == presetId }) throw IllegalArgumentException("Preset not found: $presetId")
                _soundSettings.update { settings ->
                    settings.copy(customPresets = settings.customPresets.map { if (it.id == presetId) it.copy(name = name.trim()) else it })
                }
                onUserChange()
            }

        override suspend fun deleteCustomPreset(presetId: String): AppResult<Unit> =
            appResultOf {
                if (_soundSettings.value.customPresets.none { it.id == presetId }) {
                    throw IllegalArgumentException("Preset not found: $presetId")
                }
                // The bands stay as they are; only the preset (and its highlight) goes.
                _soundSettings.update {
                    it.copy(
                        customPresets = it.customPresets.filterNot { p -> p.id == presetId },
                        activePresetId = if (it.activePresetId == presetId) "" else it.activePresetId,
                        graphicPresetId = if (it.graphicPresetId == presetId) "" else it.graphicPresetId,
                    )
                }
                onUserChange()
            }

        companion object {
            private const val TAG = "SoundRepository"

            /** Slider drags fire many changes per second; write once they settle. */
            internal const val SAVE_DEBOUNCE_MS = 400L
            private const val PASS_Q = 0.707f
            private const val NOTCH_Q = 4f
        }
    }
