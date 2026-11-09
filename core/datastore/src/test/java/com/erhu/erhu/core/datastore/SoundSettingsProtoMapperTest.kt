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
    fun audioUtilitiesRoundTripAndLegacyMessagesKeepNeutralDefaults() {
        val utilities = com.erhu.erhu.core.model.AudioUtilities(
            mono = true, balance = -0.4f, preampDb = -3f, bassDb = 6f, limiter = true,
            replayGain = com.erhu.erhu.core.model.ReplayGainMode.ALBUM,
            speed = 1.25f, pitch = 0.9f, skipSilence = true, sleepDeadlineMs = 1234567, sleepAtTrackEnd = true,
        )
        val bytes = settings.copy(utilities = utilities).toProto().toByteArray()
        val restored = com.erhu.erhu.core.datastore.proto.SoundSettingsProto.parseFrom(bytes).toDomain()
        assertEquals(utilities, restored.utilities)
        val legacy = com.erhu.erhu.core.datastore.proto.SoundSettingsProto.newBuilder().setEqEnabled(true).build().toDomain()
        assertEquals(com.erhu.erhu.core.model.AudioUtilities(), legacy.utilities)
    }

    @Test
    fun invalidUtilityNumbersAndUnknownModesAreSafelySanitized() {
        val corrupt = com.erhu.erhu.core.datastore.proto.SoundSettingsProto.newBuilder()
            .setPlaybackSpeed(Float.NaN).setPlaybackPitch(900f).setBalance(Float.POSITIVE_INFINITY)
            .setPreampDb(-100f).setBassDb(900f).setReplayGainMode("future-mode").build().toDomain().utilities
        assertEquals(1f, corrupt.speed, 0f); assertEquals(2f, corrupt.pitch, 0f)
        assertEquals(0f, corrupt.balance, 0f); assertEquals(-24f, corrupt.preampDb, 0f)
        assertEquals(12f, corrupt.bassDb, 0f)
        assertEquals(com.erhu.erhu.core.model.ReplayGainMode.OFF, corrupt.replayGain)
    }

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
