package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.PersistedPlaybackState
import com.erhu.erhu.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateProtoMapperTest {
    private val state =
        PersistedPlaybackState(
            queueSongIds = listOf(10L, 20L, 30L),
            currentIndex = 1,
            positionMs = 12_345L,
            shuffleModeEnabled = true,
            repeatMode = RepeatMode.ONE,
        )

    @Test
    fun roundTrip_preservesAllPlaybackFields() {
        assertEquals(state, state.toProto().toDomain())
    }

    @Test
    fun applyTo_keepsThemeSettingsInTheSharedProto() {
        val existing =
            PlaybackStateProto
                .newBuilder()
                .setAppThemeId("theme_x")
                .setWidgetThemeId("widget_y")
                .setLibraryLayoutMode("LIST")
                .setThemeRollbackFlag(true)
                .addAllQueueSongIds(listOf(1L, 2L))
                .build()

        val merged = state.applyTo(existing)

        assertEquals("theme_x", merged.appThemeId)
        assertEquals("widget_y", merged.widgetThemeId)
        assertEquals("LIST", merged.libraryLayoutMode)
        assertTrue(merged.themeRollbackFlag)
        // The old queue is replaced, not appended to.
        assertEquals(listOf(10L, 20L, 30L), merged.queueSongIdsList)
        assertEquals(state, merged.toDomain())
    }

    @Test
    fun repeatModes_mapBothWays() {
        RepeatMode.entries.forEach { mode -> assertEquals(mode, mode.toProto().toDomain()) }
    }
}
