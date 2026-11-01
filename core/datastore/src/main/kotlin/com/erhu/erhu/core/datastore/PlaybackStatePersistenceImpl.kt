package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.PersistedPlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaybackStatePersistenceImpl
    @Inject
    constructor(
        private val dataStore: DataStore<PlaybackStateProto>,
    ) : PlaybackStatePersistence {
        override suspend fun save(state: PersistedPlaybackState): AppResult<Unit> =
            appResultOf {
                // Merge, don't replace: theme settings live in the same proto.
                dataStore.updateData { current -> state.applyTo(current) }
                Unit
            }

        override fun observe(): Flow<PersistedPlaybackState> = dataStore.data.map { it.toDomain() }
    }
