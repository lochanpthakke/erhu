package com.lochanpthakke.empee3.core.sound.model

@Suppress("MagicNumber", "EqualsWithHashCodeExist")
data class EqPreset(
    val id: String,
    val name: String,
    val bandGains: FloatArray,
    val isCustom: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqPreset) return false

        return id == other.id &&
            name == other.name &&
            bandGains.contentEquals(other.bandGains) &&
            isCustom == other.isCustom
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + bandGains.contentHashCode()
        result = 31 * result + isCustom.hashCode()
        return result
    }

    companion object {
        val FLAT =
            EqPreset(
                id = "preset_flat",
                name = "Flat",
                bandGains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            )

        val BASS_BOOSTER =
            EqPreset(
                id = "preset_bass_booster",
                name = "Bass Booster",
                bandGains = floatArrayOf(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f),
            )

        val TREBLE_BOOSTER =
            EqPreset(
                id = "preset_treble_booster",
                name = "Treble Booster",
                bandGains = floatArrayOf(0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f),
            )

        val ROCK =
            EqPreset(
                id = "preset_rock",
                name = "Rock",
                bandGains = floatArrayOf(5f, 3f, -1f, -2f, 1f, 3f, 5f, 6f),
            )

        val POP =
            EqPreset(
                id = "preset_pop",
                name = "Pop",
                bandGains = floatArrayOf(-1f, 1f, 3f, 4f, 4f, 3f, 1f, -1f),
            )

        val JAZZ =
            EqPreset(
                id = "preset_jazz",
                name = "Jazz",
                bandGains = floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 2f, 3f),
            )

        val VOCAL =
            EqPreset(
                id = "preset_vocal",
                name = "Vocal",
                bandGains = floatArrayOf(-2f, -1f, 2f, 5f, 4f, 2f, 0f, -2f),
            )

        val ACOUSTIC =
            EqPreset(
                id = "preset_acoustic",
                name = "Acoustic",
                bandGains = floatArrayOf(4f, 3f, 1f, 2f, 2f, 3f, 3f, 2f),
            )

        val DEFAULT_PRESETS =
            listOf(
                FLAT,
                BASS_BOOSTER,
                TREBLE_BOOSTER,
                ROCK,
                POP,
                JAZZ,
                VOCAL,
                ACOUSTIC,
            )
    }
}
