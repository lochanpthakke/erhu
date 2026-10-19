package com.lochanpthakke.empee3.core.datastore

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Songs hidden from Smart Playlists. Hiding only keeps a song out of the generated
 * recommendations: the file, the library and the user's own playlists are untouched, and
 * showing it again undoes it completely.
 */
interface HiddenTracksPersistence {
    fun observe(): Flow<Set<Long>>

    suspend fun setHidden(
        songId: Long,
        hidden: Boolean,
    ): AppResult<Unit>
}

class HiddenTracksPersistenceImpl(
    private val dataStore: DataStore<PlaybackStateProto>,
) : HiddenTracksPersistence {
    override fun observe(): Flow<Set<Long>> = dataStore.data.map { it.hiddenFromRecommendationsList.toSet() }.distinctUntilChanged()

    override suspend fun setHidden(
        songId: Long,
        hidden: Boolean,
    ): AppResult<Unit> =
        appResultOf {
            // Merge, don't replace: playback, theme and sound state live in the same proto.
            dataStore.updateData { state ->
                val ids = LinkedHashSet(state.hiddenFromRecommendationsList)
                val changed = if (hidden) ids.add(songId) else ids.remove(songId)
                if (!changed) {
                    state
                } else {
                    state
                        .toBuilder()
                        .clearHiddenFromRecommendations()
                        .addAllHiddenFromRecommendations(ids)
                        .build()
                }
            }
            Unit
        }
}
