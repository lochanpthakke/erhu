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
)

data class PersistedEqPreset(
    val id: String,
    val name: String,
    val bandGains: List<Float>,
)
