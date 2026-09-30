package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.model.PersistedEqPreset
import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import com.lochanpthakke.empee3.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The theme choice is stored as an id in the shared proto and resolved with
 * BuiltInThemes.resolve at startup (ThemeRepositoryImpl). These tests pin that
 * the Glass id survives a "restart" and is not clobbered by the other writers.
 */
class ThemeSelectionPersistenceTest {
    private fun stored(themeId: String): PlaybackStateProto =
        PlaybackStateProto
            .newBuilder()
            .setAppThemeId(themeId)
            .build()

    /** What the proto looks like after being written to disk and read back. */
    private fun PlaybackStateProto.afterRestart(): PlaybackStateProto = PlaybackStateProto.parseFrom(toByteArray())

    @Test
    fun glassSelection_isRestoredAfterRestart() {
        val restored = stored("glassmorphism").afterRestart()
        val theme = BuiltInThemes.resolve(restored.appThemeId).theme
        assertSame(BuiltInThemes.GLASSMORPHISM, theme)
        assertNotNull(theme.glass)
    }

    @Test
    fun switchingThemes_persistsTheLatestChoice() {
        var proto = stored("glassmorphism")
        proto = proto.toBuilder().setAppThemeId("default_dark").build().afterRestart()
        assertSame(BuiltInThemes.DEFAULT_DARK, BuiltInThemes.resolve(proto.appThemeId).theme)
        proto = proto.toBuilder().setAppThemeId("glassmorphism").build().afterRestart()
        assertSame(BuiltInThemes.GLASSMORPHISM, BuiltInThemes.resolve(proto.appThemeId).theme)
    }

    @Test
    fun playbackAndSoundSaves_keepTheGlassSelection() {
        val withPlayback = PersistedPlaybackState(listOf(1L), 0, 10L, false, RepeatMode.OFF).applyTo(stored("glassmorphism"))
        val withSound =
            PersistedSoundSettings(
                eqEnabled = true,
                bandGains = List(8) { 0f },
                activePresetId = "preset_flat",
                customPresets = emptyList<PersistedEqPreset>(),
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
                loudnessEnabled = false,
                loudnessGainMb = 0,
            ).applyTo(withPlayback)

        assertEquals("glassmorphism", withSound.afterRestart().appThemeId)
    }
}
