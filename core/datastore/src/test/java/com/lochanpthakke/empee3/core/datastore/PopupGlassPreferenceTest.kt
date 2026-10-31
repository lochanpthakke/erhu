package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.repository.popupGlassOpacityOrDefault
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupGlassPreferenceTest {
    @Test
    fun absentPreferenceUsesMiddleDefault() {
        assertEquals(0.5f, PlaybackStateProto.getDefaultInstance().popupGlassOpacityOrDefault())
    }

    @Test
    fun bothEndpointsAndMiddleSurviveSerializationAndOtherSettingsWrites() {
        for (value in listOf(0f, 0.5f, 1f)) {
            val written = PlaybackStateProto.newBuilder().setPopupGlassOpacity(value).setAppThemeId("default_dark").build()
            val changed = written.toBuilder().setPositionMs(1234).setGlassEffectEnabled(false).build()
            val restored = PlaybackStateProto.parseFrom(changed.toByteArray())
            assertEquals(value, restored.popupGlassOpacityOrDefault())
            assertEquals("default_dark", restored.appThemeId)
        }
    }

    @Test
    fun malformedPreferencesAreNormalized() {
        for ((stored, expected) in listOf(-1f to 0f, 2f to 1f, Float.NaN to 0.5f, Float.POSITIVE_INFINITY to 0.5f)) {
            assertEquals(expected, PlaybackStateProto.newBuilder().setPopupGlassOpacity(stored).build().popupGlassOpacityOrDefault())
        }
    }
}
