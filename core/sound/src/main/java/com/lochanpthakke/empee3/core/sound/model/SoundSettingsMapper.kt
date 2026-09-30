package com.lochanpthakke.empee3.core.sound.model

import com.lochanpthakke.empee3.core.model.PersistedEqPreset
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor

private const val MAX_EFFECT_STRENGTH = 1000

fun SoundSettings.toPersisted(): PersistedSoundSettings =
    PersistedSoundSettings(
        eqEnabled = isEqEnabled,
        bandGains = currentBandGains.toList(),
        activePresetId = activePresetId,
        customPresets = customPresets.map { PersistedEqPreset(it.id, it.name, it.bandGains.toList()) },
        bassBoostEnabled = isBassBoostEnabled,
        bassBoostStrength = bassBoostStrength.toInt(),
        virtualizerEnabled = isVirtualizerEnabled,
        virtualizerStrength = virtualizerStrength.toInt(),
        loudnessEnabled = isLoudnessEnabled,
        loudnessGainMb = loudnessGainMb,
    )

/**
 * Converts a stored snapshot back to [SoundSettings], repairing anything that
 * could not have been produced by the app (wrong band count, out-of-range
 * strengths, a preset id that no longer exists) instead of trusting it.
 */
fun PersistedSoundSettings.toSoundSettings(): SoundSettings {
    val customs =
        customPresets
            .filter { it.bandGains.size == CustomEqAudioProcessor.BAND_COUNT && it.id.isNotBlank() }
            .map { EqPreset(id = it.id, name = it.name, bandGains = it.bandGains.toFloatArray(), isCustom = true) }
    val gains =
        if (bandGains.size == CustomEqAudioProcessor.BAND_COUNT) bandGains.toFloatArray() else EqPreset.FLAT.bandGains.copyOf()
    val knownPresetIds = (EqPreset.DEFAULT_PRESETS + customs).map { it.id }.toSet()
    return SoundSettings(
        isEqEnabled = eqEnabled,
        currentBandGains = gains,
        activePresetId = activePresetId.takeIf { it in knownPresetIds } ?: EqPreset.FLAT.id,
        customPresets = customs,
        isBassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength.coerceIn(0, MAX_EFFECT_STRENGTH).toShort(),
        isVirtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength.coerceIn(0, MAX_EFFECT_STRENGTH).toShort(),
        isLoudnessEnabled = loudnessEnabled,
        loudnessGainMb = loudnessGainMb.coerceIn(0, MAX_EFFECT_STRENGTH),
    )
}
