package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.model.PersistedPlaybackState
import kotlinx.coroutines.flow.Flow

/**
 * Persists and observes [PersistedPlaybackState] across app restarts.
 *
 * Proto types are contained entirely within the implementation — consumers
 * (including future [com.erhu.erhu.core.playback] callers) only
 * ever see domain types.
 */
interface PlaybackStatePersistence {
    suspend fun save(state: PersistedPlaybackState): AppResult<Unit>

    fun observe(): Flow<PersistedPlaybackState>
}
