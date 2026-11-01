package com.erhu.erhu.core.sound.model

import com.erhu.erhu.core.model.PersistedEqPreset
import com.erhu.erhu.core.model.PersistedSoundSettings
import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType

fun SoundSettings.toPersisted(): PersistedSoundSettings =
    PersistedSoundSettings(
        eqEnabled = isEqEnabled,
        bandGains = currentBandGains.toList(),
        activePresetId = activePresetId,
        customPresets =
            customPresets.map {
                PersistedEqPreset(
                    id = it.id,
                    name = it.name,
                    bandGains = it.bandGains.toList(),
                    bandFrequencies = it.bandFrequencies.toList(),
                    bandQs = it.bandQs.toList(),
                    filterTypes = it.filterTypes.map { type -> type.name },
                )
            },
        bassBoostEnabled = false,
        bassBoostStrength = 0,
        virtualizerEnabled = false,
        virtualizerStrength = 0,
        loudnessEnabled = false,
        loudnessGainMb = 0,
        bandFrequencies = currentBandFrequencies.toList(),
        bandQs = currentBandQs.toList(),
        filterTypes = currentFilterTypes.map { it.name },
        activeBandCount = activeBandCount,
        selectedBandIndex = selectedBandIndex,
    )

/**
 * Converts a stored snapshot back to [SoundSettings], repairing anything that
 * could not have been produced by the app.
 */
fun PersistedSoundSettings.toSoundSettings(): SoundSettings {
    val customs =
        customPresets
            .filter { it.bandGains.size == CustomEqAudioProcessor.BAND_COUNT && it.id.isNotBlank() }
            .map { p ->
                val freqs =
                    if (p.bandFrequencies.size ==
                        CustomEqAudioProcessor.BAND_COUNT
                    ) {
                        p.bandFrequencies.toFloatArray()
                    } else {
                        CustomEqAudioProcessor.BAND_FREQUENCIES.copyOf()
                    }
                val qs =
                    if (p.bandQs.size ==
                        CustomEqAudioProcessor.BAND_COUNT
                    ) {
                        p.bandQs.toFloatArray()
                    } else {
                        FloatArray(CustomEqAudioProcessor.BAND_COUNT) { CustomEqAudioProcessor.DEFAULT_Q }
                    }
                val types =
                    if (p.filterTypes.size ==
                        CustomEqAudioProcessor.BAND_COUNT
                    ) {
                        p.filterTypes.map { parseFilterType(it) }.toTypedArray()
                    } else {
                        Array(CustomEqAudioProcessor.BAND_COUNT) { FilterType.PEAKING }
                    }
                EqPreset(
                    id = p.id,
                    name = p.name,
                    bandGains = p.bandGains.toFloatArray(),
                    bandFrequencies = freqs,
                    bandQs = qs,
                    filterTypes = types,
                    isCustom = true,
                )
            }

    val gains = if (bandGains.size == CustomEqAudioProcessor.BAND_COUNT) bandGains else EqPreset.FLAT.bandGains.toList()
    val freqs =
        if (bandFrequencies.size ==
            CustomEqAudioProcessor.BAND_COUNT
        ) {
            bandFrequencies
        } else {
            CustomEqAudioProcessor.BAND_FREQUENCIES.toList()
        }
    val qs =
        if (bandQs.size ==
            CustomEqAudioProcessor.BAND_COUNT
        ) {
            bandQs
        } else {
            List(CustomEqAudioProcessor.BAND_COUNT) { CustomEqAudioProcessor.DEFAULT_Q }
        }
    val types =
        if (filterTypes.size ==
            CustomEqAudioProcessor.BAND_COUNT
        ) {
            filterTypes.map { parseFilterType(it) }
        } else {
            List(CustomEqAudioProcessor.BAND_COUNT) { FilterType.PEAKING }
        }

    val bandsList =
        List(CustomEqAudioProcessor.BAND_COUNT) { i ->
            BandConfig(
                frequencyHz = freqs[i],
                gainDb = gains[i],
                q = qs[i],
                filterType = types[i],
            )
        }

    val knownPresetIds = (EqPreset.DEFAULT_PRESETS + customs).map { it.id }.toSet()
    val safeActiveBandCount = activeBandCount.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, CustomEqAudioProcessor.BAND_COUNT)
    val safeSelectedBandIndex = selectedBandIndex.coerceIn(0, safeActiveBandCount - 1)

    return SoundSettings(
        isEqEnabled = eqEnabled,
        bands = bandsList,
        activeBandCount = safeActiveBandCount,
        selectedBandIndex = safeSelectedBandIndex,
        activePresetId = activePresetId.takeIf { it in knownPresetIds } ?: EqPreset.FLAT.id,
        customPresets = customs,
    )
}

private fun parseFilterType(name: String): FilterType =
    try {
        FilterType.valueOf(name)
    } catch (_: Exception) {
        FilterType.PEAKING
    }
