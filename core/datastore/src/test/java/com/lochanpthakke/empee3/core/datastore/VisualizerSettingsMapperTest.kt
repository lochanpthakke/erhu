package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.proto.VisualizerSettingsProto
import com.lochanpthakke.empee3.core.model.VisualizerMode
import com.lochanpthakke.empee3.core.model.VisualizerSensitivity
import com.lochanpthakke.empee3.core.model.VisualizerSettings
import com.lochanpthakke.empee3.core.model.VisualizerSmoothing
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerSettingsMapperTest {
    @Test
    fun `preferences round-trip and leave the rest of the settings alone`() {
        val start =
            PlaybackStateProto
                .newBuilder()
                .setAppThemeId("dark")
                .addQueueSongIds(7)
                .setGlassEffectEnabled(true)
                .build()
        val settings =
            VisualizerSettings(
                mode = VisualizerMode.OSCILLOSCOPE,
                sensitivity = VisualizerSensitivity.HIGH,
                smoothing = VisualizerSmoothing.SMOOTH,
                reduceMotion = true,
            )
        val saved = settings.applyTo(start)
        assertEquals(settings, saved.toVisualizerSettings())
        assertEquals("dark", saved.appThemeId)
        assertEquals(listOf(7L), saved.queueSongIdsList)
        assertEquals(true, saved.glassEffectEnabled)
        // Survives serialization (what DataStore writes to disk).
        assertEquals(settings, PlaybackStateProto.parseFrom(saved.toByteArray()).toVisualizerSettings())
    }

    @Test
    fun `missing or unknown values fall back to the defaults`() {
        assertEquals(VisualizerSettings(), PlaybackStateProto.getDefaultInstance().toVisualizerSettings())
        val odd =
            PlaybackStateProto
                .newBuilder()
                .setVisualizerSettings(VisualizerSettingsProto.newBuilder().setMode("LASER_SHOW").setSensitivity("").setReduceMotion(true))
                .build()
        assertEquals(VisualizerSettings(reduceMotion = true), odd.toVisualizerSettings())
    }
}
