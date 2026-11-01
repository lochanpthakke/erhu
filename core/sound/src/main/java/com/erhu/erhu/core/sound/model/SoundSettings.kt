package com.erhu.erhu.core.sound.model

import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType

data class BandConfig(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float = CustomEqAudioProcessor.DEFAULT_Q,
    val filterType: FilterType = FilterType.PEAKING,
)

@Suppress("MagicNumber", "EqualsWithHashCodeExist")
data class SoundSettings(
    val isEqEnabled: Boolean = true,
    val bands: List<BandConfig> = defaultBands(),
    val activeBandCount: Int = CustomEqAudioProcessor.BAND_COUNT,
    val selectedBandIndex: Int = 0,
    val activePresetId: String = EqPreset.FLAT.id,
    val customPresets: List<EqPreset> = emptyList(),
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
        get() = EqPreset.DEFAULT_PRESETS + customPresets

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
