package com.erhu.erhu.core.sound.model

import com.erhu.erhu.core.sound.dsp.CustomEqAudioProcessor
import com.erhu.erhu.core.sound.dsp.FilterType

@Suppress("MagicNumber", "EqualsWithHashCodeExist")
data class EqPreset(
    val id: String,
    val name: String,
    val bandGains: FloatArray,
    val bandFrequencies: FloatArray = CustomEqAudioProcessor.BAND_FREQUENCIES.copyOf(),
    val bandQs: FloatArray = FloatArray(CustomEqAudioProcessor.BAND_COUNT) { CustomEqAudioProcessor.DEFAULT_Q },
    val filterTypes: Array<FilterType> = Array(CustomEqAudioProcessor.BAND_COUNT) { FilterType.PEAKING },
    val isCustom: Boolean = false,
    /** Graphic presets set the 8 fixed bands' gains; parametric ones set whole bands. */
    val mode: EqMode = EqMode.GRAPHIC,
    val bandCount: Int = CustomEqAudioProcessor.BAND_COUNT,
    val bandEnabled: BooleanArray = BooleanArray(CustomEqAudioProcessor.BAND_COUNT) { true },
    val slopes: IntArray = IntArray(CustomEqAudioProcessor.BAND_COUNT) { 12 },
) {
    /** The parametric bands this preset sets (all 8 entries; [bandCount] of them are active). */
    fun parametricBands(): List<BandConfig> =
        List(CustomEqAudioProcessor.BAND_COUNT) { i ->
            BandConfig(
                frequencyHz = bandFrequencies.getOrElse(i) { CustomEqAudioProcessor.BAND_FREQUENCIES[i] },
                gainDb = bandGains.getOrElse(i) { 0f },
                q = bandQs.getOrElse(i) { CustomEqAudioProcessor.DEFAULT_Q },
                filterType = filterTypes.getOrElse(i) { FilterType.PEAKING },
                enabled = bandEnabled.getOrElse(i) { true },
                slope = slopes.getOrElse(i) { 12 },
            )
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqPreset) return false

        return id == other.id &&
            name == other.name &&
            bandGains.contentEquals(other.bandGains) &&
            bandFrequencies.contentEquals(other.bandFrequencies) &&
            bandQs.contentEquals(other.bandQs) &&
            filterTypes.contentEquals(other.filterTypes) &&
            isCustom == other.isCustom &&
            mode == other.mode &&
            bandCount == other.bandCount &&
            bandEnabled.contentEquals(other.bandEnabled) &&
            slopes.contentEquals(other.slopes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + bandGains.contentHashCode()
        result = 31 * result + bandFrequencies.contentHashCode()
        result = 31 * result + bandQs.contentHashCode()
        result = 31 * result + filterTypes.contentHashCode()
        result = 31 * result + isCustom.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + bandCount
        result = 31 * result + bandEnabled.contentHashCode()
        result = 31 * result + slopes.contentHashCode()
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

        /** A parametric preset from its bands; the remaining slots keep their default, flat bands. */
        private fun parametric(
            id: String,
            name: String,
            vararg bands: BandConfig,
        ): EqPreset {
            val all = SoundSettings.defaultBands().toMutableList()
            bands.forEachIndexed { i, band -> all[i] = band }
            return EqPreset(
                id = id,
                name = name,
                bandGains = FloatArray(all.size) { all[it].gainDb },
                bandFrequencies = FloatArray(all.size) { all[it].frequencyHz },
                bandQs = FloatArray(all.size) { all[it].q },
                filterTypes = Array(all.size) { all[it].filterType },
                mode = EqMode.PARAMETRIC,
                bandCount = bands.size.coerceIn(CustomEqAudioProcessor.MIN_BAND_COUNT, CustomEqAudioProcessor.BAND_COUNT),
                bandEnabled = BooleanArray(all.size) { all[it].enabled },
                slopes = IntArray(all.size) { all[it].slope },
            )
        }

        private fun peak(
            hz: Float,
            db: Float,
            q: Float = 1.0f,
        ) = BandConfig(hz, db, q, FilterType.PEAKING)

        private fun lowShelf(
            hz: Float,
            db: Float,
        ) = BandConfig(hz, db, 0.7f, FilterType.LOW_SHELF)

        private fun highShelf(
            hz: Float,
            db: Float,
        ) = BandConfig(hz, db, 0.7f, FilterType.HIGH_SHELF)

        private fun highPass(
            hz: Float,
            slope: Int,
        ) = BandConfig(hz, 0f, 0.707f, FilterType.HIGH_PASS, slope = slope)

        val PARAMETRIC_FLAT = parametric("preset_p_flat", "Flat", peak(100f, 0f), peak(400f, 0f), peak(2500f, 0f), peak(8000f, 0f))

        val PARAMETRIC_PRESETS =
            listOf(
                PARAMETRIC_FLAT,
                parametric(
                    "preset_p_warm_bass",
                    "Warm Bass",
                    highPass(25f, 24),
                    lowShelf(110f, 5f),
                    peak(300f, -1.5f, 1.2f),
                    peak(3500f, 0f),
                ),
                parametric(
                    "preset_p_vocal_presence",
                    "Vocal Presence",
                    highPass(80f, 12),
                    peak(250f, -2f, 1.0f),
                    peak(3000f, 3f, 1.2f),
                    highShelf(10000f, 1.5f),
                ),
                parametric(
                    "preset_p_air",
                    "Air & Sparkle",
                    peak(250f, -1f, 0.9f),
                    peak(1000f, 0f),
                    peak(5000f, 1f, 1.5f),
                    highShelf(11000f, 4f),
                ),
                parametric(
                    "preset_p_loudness",
                    "Loudness",
                    lowShelf(90f, 5f),
                    peak(1000f, -2f, 0.8f),
                    peak(3000f, 0f),
                    highShelf(9000f, 4f),
                ),
                parametric(
                    "preset_p_de_harsh",
                    "De-harsh",
                    peak(200f, 0f),
                    peak(1000f, 0f),
                    peak(3500f, -3f, 2.0f),
                    peak(7500f, -2.5f, 3.0f),
                ),
                parametric(
                    "preset_p_rumble_cut",
                    "Rumble & Hiss Cut",
                    highPass(40f, 48),
                    peak(400f, 0f),
                    peak(2500f, 0f),
                    BandConfig(16000f, 0f, 0.707f, FilterType.LOW_PASS, slope = 12),
                ),
            )
    }
}
