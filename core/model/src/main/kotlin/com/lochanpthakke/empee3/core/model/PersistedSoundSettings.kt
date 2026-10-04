package com.lochanpthakke.empee3.core.model

/**
 * Domain snapshot of the user's sound settings persisted across app restarts.
 *
 * Mirrors the persistable parts of the sound module's settings using plain
 * lists (no FloatArray) so equality is structural and the type stays free of
 * any sound/DSP dependency.
 */
data class PersistedSoundSettings(
    val eqEnabled: Boolean,
    val bandGains: List<Float>,
    val activePresetId: String,
    val customPresets: List<PersistedEqPreset>,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val loudnessEnabled: Boolean,
    val loudnessGainMb: Int,
    val bandFrequencies: List<Float> = emptyList(),
    val bandQs: List<Float> = emptyList(),
    val filterTypes: List<String> = emptyList(),
    val activeBandCount: Int = 8,
    val selectedBandIndex: Int = 0,
)

data class PersistedEqPreset(
    val id: String,
    val name: String,
    val bandGains: List<Float>,
    val bandFrequencies: List<Float> = emptyList(),
    val bandQs: List<Float> = emptyList(),
    val filterTypes: List<String> = emptyList(),
)
