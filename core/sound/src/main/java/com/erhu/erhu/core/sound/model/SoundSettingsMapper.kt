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
                    mode = it.mode.name,
                    bandCount = it.bandCount,
                    bandEnabled = it.bandEnabled.toList(),
                    bandSlopes = it.slopes.toList(),
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
        utilities = utilities.sanitized(),
        eqMode = mode.name,
        graphicGains = graphicGains,
        graphicPresetId = graphicPresetId,
        bandEnabled = bands.map { it.enabled },
        bandSlopes = bands.map { it.slope },
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
                    bandGains = p.bandGains.map { it.coerceIn(MIN_GAIN, MAX_GAIN) }.toFloatArray(),
                    bandFrequencies = freqs,
                    bandQs = qs,
                    filterTypes = types,
                    isCustom = true,
                    // Presets saved before the modes existed hold whole bands: they are parametric.
                    mode = parseMode(p.mode, EqMode.PARAMETRIC),
                    bandCount = p.bandCount.takeIf { it > 0 }?.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, BANDS) ?: BANDS,
                    bandEnabled = BooleanArray(BANDS) { p.bandEnabled.getOrElse(it) { true } },
                    slopes = IntArray(BANDS) { p.bandSlopes.getOrElse(it) { DEFAULT_SLOPE } },
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
                frequencyHz = freqs[i].coerceIn(CustomEqAudioProcessor.MIN_FREQ_HZ, CustomEqAudioProcessor.MAX_FREQ_HZ),
                gainDb = gains[i].coerceIn(MIN_GAIN, MAX_GAIN),
                q = qs[i].coerceIn(CustomEqAudioProcessor.MIN_Q, CustomEqAudioProcessor.MAX_Q),
                filterType = types[i],
                enabled = bandEnabled.getOrElse(i) { true },
                slope = bandSlopes.getOrElse(i) { DEFAULT_SLOPE }.takeIf { it in SLOPES } ?: DEFAULT_SLOPE,
            )
        }

    // Before the two modes there was one EQ, with the classic presets applied to its bands. Those
    // bands stay the Parametric setup unchanged; the Graphic setup starts from the classic preset
    // that was chosen (if any), so both modes begin where the user left off.
    val savedMode = parseMode(eqMode, EqMode.PARAMETRIC)
    val classicPreset = EqPreset.DEFAULT_PRESETS.firstOrNull { it.id == activePresetId }
    val graphic =
        when {
            graphicGains.size == BANDS -> graphicGains.map { it.coerceIn(MIN_GAIN, MAX_GAIN) }
            classicPreset != null -> classicPreset.bandGains.toList()
            else -> List(BANDS) { 0f }
        }
    val graphicPresets = EqPreset.DEFAULT_PRESETS + customs.filter { it.mode == EqMode.GRAPHIC }
    val graphicPreset =
        graphicPresetId.takeIf { id -> graphicPresets.any { it.id == id } }
            ?: classicPreset?.id.takeIf { eqMode.isEmpty() }
            ?: EqPreset.FLAT.id.takeIf { graphic.all { it == 0f } }
            ?: ""
    val parametricPresetIds = (EqPreset.PARAMETRIC_PRESETS + customs.filter { it.mode == EqMode.PARAMETRIC }).map { it.id }.toSet()

    val safeActiveBandCount = activeBandCount.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, CustomEqAudioProcessor.BAND_COUNT)
    val safeSelectedBandIndex = selectedBandIndex.coerceIn(0, safeActiveBandCount - 1)

    return SoundSettings(
        isEqEnabled = eqEnabled,
        bands = bandsList,
        activeBandCount = safeActiveBandCount,
        selectedBandIndex = safeSelectedBandIndex,
        // The parametric preset in use; a classic preset id from before the modes no longer applies here.
        activePresetId = activePresetId.takeIf { it in parametricPresetIds } ?: "",
        customPresets = customs,
        utilities = utilities.sanitized(),
        mode = savedMode,
        graphicGains = graphic,
        graphicPresetId = graphicPreset,
    )
}

private fun parseMode(
    name: String,
    fallback: EqMode,
): EqMode = EqMode.entries.firstOrNull { it.name == name } ?: fallback

private const val BANDS = CustomEqAudioProcessor.BAND_COUNT
private const val DEFAULT_SLOPE = 12
private val SLOPES = listOf(12, 24, 48)
private const val MIN_GAIN = CustomEqAudioProcessor.MIN_GAIN_DB
private const val MAX_GAIN = CustomEqAudioProcessor.MAX_GAIN_DB

private fun parseFilterType(name: String): FilterType =
    try {
        FilterType.valueOf(name)
    } catch (_: Exception) {
        FilterType.PEAKING
    }
