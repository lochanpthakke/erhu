package com.lochanpthakke.empee3.core.datastore

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.model.CollectionRef
import com.lochanpthakke.empee3.core.model.GlobalTransitionSettings
import com.lochanpthakke.empee3.core.model.TransitionChoice
import com.lochanpthakke.empee3.core.model.TransitionConfig
import com.lochanpthakke.empee3.core.model.TransitionPairKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Transition settings at every level (global, album, playlist, pair), kept across restarts in the
 * app's settings store. Writes happen when the user saves, never per drag.
 */
interface TransitionSettingsPersistence {
    fun observe(): Flow<TransitionConfig>

    suspend fun saveGlobal(settings: GlobalTransitionSettings): AppResult<Unit>

    /** [TransitionChoice.Inherit] removes the collection's entry. */
    suspend fun saveCollection(
        collection: CollectionRef,
        choice: TransitionChoice,
    ): AppResult<Unit>

    /** [TransitionChoice.Inherit] removes the pair's entry. */
    suspend fun savePair(
        pair: TransitionPairKey,
        choice: TransitionChoice,
    ): AppResult<Unit>
}

class TransitionSettingsPersistenceImpl(
    private val dataStore: DataStore<PlaybackStateProto>,
) : TransitionSettingsPersistence {
    override fun observe(): Flow<TransitionConfig> = dataStore.data.map { it.toTransitionConfig() }.distinctUntilChanged()

    // Merge, don't replace: playback, theme and sound state live in the same proto.
    override suspend fun saveGlobal(settings: GlobalTransitionSettings): AppResult<Unit> =
        appResultOf {
            dataStore.updateData { settings.applyTo(it) }
            Unit
        }

    override suspend fun saveCollection(
        collection: CollectionRef,
        choice: TransitionChoice,
    ): AppResult<Unit> =
        appResultOf {
            dataStore.updateData { choice.applyToCollection(collection, it) }
            Unit
        }

    override suspend fun savePair(
        pair: TransitionPairKey,
        choice: TransitionChoice,
    ): AppResult<Unit> =
        appResultOf {
            dataStore.updateData { choice.applyToPair(pair, it) }
            Unit
        }
}
