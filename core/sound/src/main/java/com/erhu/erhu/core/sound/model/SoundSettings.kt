package com.erhu.erhu.core.sound.model

import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType

data class BandConfig(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float = CustomEqAudioProcessor.DEFAULT_Q,
    val filterType: FilterType = FilterType.PEAKING,
    /** A band switched off keeps its settings but does nothing. */
    val enabled: Boolean = true,
    /** dB per octave of a low or high pass: 12, 24 or 48. */
    val slope: Int = 12,
) {
    fun toSpec(): com.erhu.erhu.core.sound.dsp.BandSpec =
        com.erhu.erhu.core.sound.dsp
            .BandSpec(filterType, frequencyHz, gainDb, q, slope, enabled)
}

/** Graphic: 8 fixed bands, gain only, the classic presets. Parametric: 4-8 freely shaped bands. */
enum class EqMode { GRAPHIC, PARAMETRIC }

@Suppress("MagicNumber", "EqualsWithHashCodeExist")
data class SoundSettings(
    val isEqEnabled: Boolean = true,
    val bands: List<BandConfig> = defaultBands(),
    val activeBandCount: Int = CustomEqAudioProcessor.BAND_COUNT,
    val selectedBandIndex: Int = 0,
    val activePresetId: String = "preset_p_flat",
    val customPresets: List<EqPreset> = emptyList(),
    val mode: EqMode = EqMode.PARAMETRIC,
    /** The Graphic mode's 8 gains, kept apart from the parametric bands so switching never loses either. */
    val graphicGains: List<Float> = List(CustomEqAudioProcessor.BAND_COUNT) { 0f },
    val graphicPresetId: String = EqPreset.FLAT.id,
    val utilities: com.erhu.erhu.core.model.AudioUtilities =
        com.erhu.erhu.core.model
            .AudioUtilities(),
) {
    val currentBandGains: FloatArray
        get() = FloatArray(bands.size) { bands[it].gainDb }

    val currentBandFrequencies: FloatArray
        get() = FloatArray(bands.size) { bands[it].frequencyHz }

    val currentBandQs: FloatArray
        get() = FloatArray(bands.size) { bands[it].q }

    val currentFilterTypes: Array<FilterType>
        get() = Array(bands.size) { bands[it].filterType }

    val allPresets: List<EqPreset>
        get() = EqPreset.DEFAULT_PRESETS + EqPreset.PARAMETRIC_PRESETS + customPresets

    /** The presets of the mode on screen. */
    val presetsForMode: List<EqPreset>
        get() =
            if (mode == EqMode.GRAPHIC) {
                EqPreset.DEFAULT_PRESETS + customPresets.filter { it.mode == EqMode.GRAPHIC }
            } else {
                EqPreset.PARAMETRIC_PRESETS + customPresets.filter { it.mode == EqMode.PARAMETRIC }
            }

    val presetIdForMode: String
        get() = if (mode == EqMode.GRAPHIC) graphicPresetId else activePresetId

    /** The bands the processor applies in the current mode. */
    fun effectiveBands(): List<com.erhu.erhu.core.sound.dsp.BandSpec> =
        if (mode == EqMode.GRAPHIC) {
            List(CustomEqAudioProcessor.BAND_COUNT) { i ->
                com.erhu.erhu.core.sound.dsp.BandSpec(
                    FilterType.PEAKING,
                    CustomEqAudioProcessor.BAND_FREQUENCIES[i],
                    graphicGains.getOrElse(i) { 0f },
                    CustomEqAudioProcessor.DEFAULT_Q,
                )
            }
        } else {
            bands.take(activeBandCount).map { it.toSpec() }
        }

    companion object {
        fun defaultBands(): List<BandConfig> =
            List(CustomEqAudioProcessor.BAND_COUNT) { i ->
                BandConfig(
                    frequencyHz = CustomEqAudioProcessor.BAND_FREQUENCIES[i],
                    gainDb = 0f,
                    q = CustomEqAudioProcessor.DEFAULT_Q,
                    filterType = FilterType.PEAKING,
                )
            }
    }
}
