package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.repository.migratedThemeSelection
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.model.PersistedEqPreset
import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import com.lochanpthakke.empee3.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme choice and the Glass Effect are stored in the shared proto and read
 * at startup (ThemeRepositoryImpl). These tests pin that both survive a
 * "restart", are not clobbered by the other writers, and that selections of the
 * removed Glassmorphism theme migrate safely.
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
    fun systemDefaultSelection_isRestoredAfterRestart() {
        val restored = stored(BuiltInThemes.SYSTEM_DEFAULT_ID).afterRestart()
        assertEquals(BuiltInThemes.SYSTEM_DEFAULT_ID, restored.appThemeId)
    }

    @Test
    fun glassEffect_isOffWhenNeverSet_andPersistsBothWays() {
        assertFalse(stored("default_dark").afterRestart().glassEffectEnabled)
        val on = stored("default_dark").toBuilder().setGlassEffectEnabled(true).build().afterRestart()
        assertTrue(on.glassEffectEnabled)
        val off = on.toBuilder().setGlassEffectEnabled(false).build().afterRestart()
        assertTrue(off.hasGlassEffectEnabled())
        assertFalse(off.glassEffectEnabled)
    }

    @Test
    fun switchingThemes_persistsTheLatestChoice() {
        var proto = stored(BuiltInThemes.SYSTEM_DEFAULT_ID)
        proto = proto.toBuilder().setAppThemeId("default_dark").build().afterRestart()
        assertSame(BuiltInThemes.DEFAULT_DARK, BuiltInThemes.resolve(proto.appThemeId).theme)
        proto = proto.toBuilder().setAppThemeId("cyberpunk_tech").build().afterRestart()
        assertSame(BuiltInThemes.CYBERPUNK_TECH, BuiltInThemes.resolve(proto.appThemeId).theme)
    }

    @Test
    fun playbackAndSoundSaves_keepThemeAndGlassEffect() {
        val start = stored(BuiltInThemes.SYSTEM_DEFAULT_ID).toBuilder().setGlassEffectEnabled(true).build()
        val withPlayback = PersistedPlaybackState(listOf(1L), 0, 10L, false, RepeatMode.OFF).applyTo(start)
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

        val restored = withSound.afterRestart()
        assertEquals(BuiltInThemes.SYSTEM_DEFAULT_ID, restored.appThemeId)
        assertTrue(restored.glassEffectEnabled)
    }

    // ---- Migration from the removed Glassmorphism theme

    @Test
    fun legacyGlassmorphism_migratesToSystemDefault_withGlassOn() {
        val legacy =
            stored(BuiltInThemes.LEGACY_GLASSMORPHISM_ID)
                .toBuilder()
                .setWidgetThemeId(BuiltInThemes.LEGACY_GLASSMORPHISM_ID)
                .setLastVerifiedStableThemeId(BuiltInThemes.LEGACY_GLASSMORPHISM_ID)
                .setLibraryLayoutMode("LIST")
                .build()
        val migrated = assertNotNull(legacy.migratedThemeSelection())
        assertEquals(BuiltInThemes.SYSTEM_DEFAULT_ID, migrated.appThemeId)
        assertTrue(migrated.glassEffectEnabled)
        assertEquals(BuiltInThemes.DEFAULT_DARK.id, migrated.widgetThemeId)
        assertEquals(BuiltInThemes.SYSTEM_DEFAULT_ID, migrated.lastVerifiedStableThemeId)
        // Unrelated settings are kept.
        assertEquals("LIST", migrated.libraryLayoutMode)
    }

    @Test
    fun migration_keepsAnExplicitGlassChoice_andIsANoOpOtherwise() {
        val legacyGlassOff =
            stored(BuiltInThemes.LEGACY_GLASSMORPHISM_ID).toBuilder().setGlassEffectEnabled(false).build()
        assertFalse(legacyGlassOff.migratedThemeSelection()!!.glassEffectEnabled)
        assertNull(stored("cyberpunk_tech").migratedThemeSelection())
        assertNull(stored(BuiltInThemes.SYSTEM_DEFAULT_ID).migratedThemeSelection())
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
