package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hiding a song from Smart Playlists is saved, undoable, and leaves every other setting alone. */
class HiddenTracksPersistenceTest {
    private class MemoryStore(
        initial: PlaybackStateProto,
    ) : DataStore<PlaybackStateProto> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<PlaybackStateProto> = state

        override suspend fun updateData(transform: suspend (t: PlaybackStateProto) -> PlaybackStateProto): PlaybackStateProto {
            // Through bytes, as on disk.
            state.value = PlaybackStateProto.parseFrom(transform(state.value).toByteArray())
            return state.value
        }
    }

    private val start =
        PlaybackStateProto
            .newBuilder()
            .setAppThemeId("default_dark")
            .addAllQueueSongIds(listOf(7L, 8L))
            .build()

    @Test
    fun hideAndUndo_roundTrip_withoutTouchingOtherState() =
        runBlocking {
            val store = MemoryStore(start)
            val hidden = HiddenTracksPersistenceImpl(store)
            hidden.setHidden(42, true)
            hidden.setHidden(7, true)
            assertEquals(setOf(42L, 7L), hidden.observe().first())
            hidden.setHidden(42, false)
            assertEquals(setOf(7L), hidden.observe().first())
            val saved = store.data.first()
            assertEquals("default_dark", saved.appThemeId)
            assertEquals(listOf(7L, 8L), saved.queueSongIdsList)
        }

    @Test
    fun hidingTwiceOrShowingAVisibleSong_changesNothing() =
        runBlocking {
            val store = MemoryStore(start)
            val hidden = HiddenTracksPersistenceImpl(store)
            hidden.setHidden(5, true)
            hidden.setHidden(5, true)
            hidden.setHidden(6, false)
            assertEquals(listOf(5L), store.data.first().hiddenFromRecommendationsList)
        }

    @Test
    fun oldSavedStateHasNothingHidden() =
        runBlocking {
            assertEquals(emptySet<Long>(), HiddenTracksPersistenceImpl(MemoryStore(start)).observe().first())
        }
}
