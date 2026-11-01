package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.PersistedEqPreset
import com.erhu.erhu.core.model.PersistedPlaybackState
import com.erhu.erhu.core.model.PersistedSoundSettings
import com.erhu.erhu.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundSettingsProtoMapperTest {
    private val settings =
        PersistedSoundSettings(
            eqEnabled = false,
            bandGains = listOf(6f, 5f, 3f, 1f, 0f, -1.5f, -3f, 2f),
            activePresetId = "custom_1",
            customPresets = listOf(PersistedEqPreset("custom_1", "Car", listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f))),
            bassBoostEnabled = true,
            bassBoostStrength = 600,
            virtualizerEnabled = true,
            virtualizerStrength = 250,
            loudnessEnabled = true,
            loudnessGainMb = 400,
        )

    @Test
    fun roundTrip_preservesEveryField() {
        assertEquals(settings, settings.applyTo(PlaybackStateProto.getDefaultInstance()).toSoundSettingsOrNull())
    }

    @Test
    fun neverSaved_readsAsNull_soDefaultsAreKept() {
        assertNull(PlaybackStateProto.getDefaultInstance().toSoundSettingsOrNull())
        // A proto written by older app versions (theme/playback only) has no sound settings either.
        assertNull(
            PlaybackStateProto
                .newBuilder()
                .setAppThemeId("x")
                .build()
                .toSoundSettingsOrNull(),
        )
    }

    @Test
    fun savingSoundSettings_keepsPlaybackAndThemeFields() {
        val existing =
            PersistedPlaybackState(listOf(1L, 2L), 1, 5_000L, true, RepeatMode.ALL)
                .applyTo(PlaybackStateProto.getDefaultInstance())
                .toBuilder()
                .setAppThemeId("theme_x")
                .build()

        val merged = settings.applyTo(existing)

        assertEquals("theme_x", merged.appThemeId)
        assertEquals(listOf(1L, 2L), merged.queueSongIdsList)
        assertEquals(5_000L, merged.positionMs)
        assertEquals(settings, merged.toSoundSettingsOrNull())
    }

    @Test
    fun savingPlaybackState_keepsSoundSettings() {
        val withSound = settings.applyTo(PlaybackStateProto.getDefaultInstance())
        val merged = PersistedPlaybackState(listOf(9L), 0, 0L, false, RepeatMode.OFF).applyTo(withSound)
        assertEquals(settings, merged.toSoundSettingsOrNull())
    }

    @Test
    fun overwritingSettings_replacesListsInsteadOfAppending() {
        val first = settings.applyTo(PlaybackStateProto.getDefaultInstance())
        val second = settings.copy(bandGains = List(8) { 0f }, customPresets = emptyList()).applyTo(first)
        val restored = second.toSoundSettingsOrNull()!!
        assertEquals(List(8) { 0f }, restored.bandGains)
        assertEquals(emptyList<PersistedEqPreset>(), restored.customPresets)
    }
}
