package com.lochanpthakke.empee3.core.sound.model

@Suppress("MagicNumber", "EqualsWithHashCodeExist")
data class SoundSettings(
    val isEqEnabled: Boolean = true,
    val currentBandGains: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    val activePresetId: String = EqPreset.FLAT.id,
    val customPresets: List<EqPreset> = emptyList(),
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    val isVirtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0,
    val isLoudnessEnabled: Boolean = false,
    val loudnessGainMb: Int = 0,
) {
    val allPresets: List<EqPreset>
        get() = EqPreset.DEFAULT_PRESETS + customPresets

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoundSettings) return false

        return isEqEnabled == other.isEqEnabled &&
            currentBandGains.contentEquals(other.currentBandGains) &&
            activePresetId == other.activePresetId &&
            customPresets == other.customPresets &&
            isBassBoostEnabled == other.isBassBoostEnabled &&
            bassBoostStrength == other.bassBoostStrength &&
            isVirtualizerEnabled == other.isVirtualizerEnabled &&
            virtualizerStrength == other.virtualizerStrength &&
            isLoudnessEnabled == other.isLoudnessEnabled &&
            loudnessGainMb == other.loudnessGainMb
    }

    override fun hashCode(): Int {
        var result = isEqEnabled.hashCode()
        result = 31 * result + currentBandGains.contentHashCode()
        result = 31 * result + activePresetId.hashCode()
        result = 31 * result + customPresets.hashCode()
        result = 31 * result + isBassBoostEnabled.hashCode()
        result = 31 * result + bassBoostStrength.hashCode()
        result = 31 * result + isVirtualizerEnabled.hashCode()
        result = 31 * result + virtualizerStrength.hashCode()
        result = 31 * result + isLoudnessEnabled.hashCode()
        result = 31 * result + loudnessGainMb.hashCode()
        return result
    }
}
