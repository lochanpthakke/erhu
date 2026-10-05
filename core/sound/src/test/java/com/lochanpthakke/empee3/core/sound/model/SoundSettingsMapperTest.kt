package com.lochanpthakke.empee3.core.sound.model

import com.lochanpthakke.empee3.core.model.PersistedEqPreset
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import com.lochanpthakke.empee3.core.sound.dsp.CustomEqAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSettingsMapperTest {
    private val custom = EqPreset("custom_a", "Mine", floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f), isCustom = true)

    private val sampleGains = floatArrayOf(3f, 2f, 1f, 0f, -1f, -2f, -3f, 4f)

    private val settings =
        SoundSettings(
            isEqEnabled = false,
            bands = List(8) { i -> BandConfig(CustomEqAudioProcessor.BAND_FREQUENCIES[i], sampleGains[i]) },
            activePresetId = custom.id,
            customPresets = listOf(custom),
            isBassBoostEnabled = true,
            bassBoostStrength = 700,
            isVirtualizerEnabled = true,
            virtualizerStrength = 300,
            isLoudnessEnabled = true,
            loudnessGainMb = 500,
        )

    @Test
    fun roundTrip_isLossless() {
        assertEquals(settings, settings.toPersisted().toSoundSettings())
    }

    @Test
    fun defaults_roundTripToDefaults() {
        assertEquals(SoundSettings(), SoundSettings().toPersisted().toSoundSettings())
    }

    private fun persisted() = settings.toPersisted()

    @Test
    fun wrongBandCount_fallsBackToFlat() {
        val restored = persisted().copy(bandGains = listOf(1f, 2f)).toSoundSettings()
        assertArrayEquals(EqPreset.FLAT.bandGains, restored.currentBandGains, 0f)
    }

    @Test
    fun unknownActivePreset_fallsBackToFlat() {
        assertEquals(EqPreset.FLAT.id, persisted().copy(activePresetId = "custom_deleted").toSoundSettings().activePresetId)
    }

    @Test
    fun builtInActivePreset_isKept() {
        assertEquals(EqPreset.ROCK.id, persisted().copy(activePresetId = EqPreset.ROCK.id).toSoundSettings().activePresetId)
    }

    @Test
    fun malformedCustomPresets_areDropped() {
        val restored =
            persisted()
                .copy(
                    customPresets =
                        listOf(
                            PersistedEqPreset("custom_ok", "Ok", List(8) { 1f }),
                            PersistedEqPreset("custom_short", "Short", listOf(1f)),
                            PersistedEqPreset("", "No id", List(8) { 1f }),
                        ),
                    activePresetId = "custom_ok",
                ).toSoundSettings()
        assertEquals(listOf("custom_ok"), restored.customPresets.map { it.id })
        assertEquals("custom_ok", restored.activePresetId)
        assertEquals(true, restored.customPresets.single().isCustom)
    }

    @Test
    fun outOfRangeEffectStrengths_areClamped() {
        val restored =
            persisted()
                .copy(bassBoostStrength = 50_000, virtualizerStrength = -5, loudnessGainMb = 99_999)
                .toSoundSettings()
        assertEquals(1000.toShort(), restored.bassBoostStrength)
        assertEquals(0.toShort(), restored.virtualizerStrength)
        assertEquals(1000, restored.loudnessGainMb)
    }
}
