package com.lochanpthakke.empee3.core.datastore

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SoundSettingsPersistenceImpl(
    private val dataStore: DataStore<PlaybackStateProto>,
) : SoundSettingsPersistence {
    override suspend fun save(settings: PersistedSoundSettings): AppResult<Unit> =
        appResultOf {
            // Merge, don't replace: playback and theme state live in the same proto.
            dataStore.updateData { current -> settings.applyTo(current) }
            Unit
        }

    override fun observe(): Flow<PersistedSoundSettings?> = dataStore.data.map { it.toSoundSettingsOrNull() }
}
