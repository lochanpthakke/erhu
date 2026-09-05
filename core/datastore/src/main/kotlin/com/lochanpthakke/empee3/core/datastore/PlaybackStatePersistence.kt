package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import kotlinx.coroutines.flow.Flow

/**
 * Persists and observes [PersistedPlaybackState] across app restarts.
 *
 * Proto types are contained entirely within the implementation — consumers
 * (including future [com.lochanpthakke.empee3.core.playback] callers) only
 * ever see domain types.
 */
interface PlaybackStatePersistence {
    suspend fun save(state: PersistedPlaybackState): AppResult<Unit>

    fun observe(): Flow<PersistedPlaybackState>
}
