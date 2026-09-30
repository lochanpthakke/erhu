package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.PersistedSoundSettings
import kotlinx.coroutines.flow.Flow

/**
 * Persists and observes [PersistedSoundSettings] across app restarts.
 *
 * [observe] emits null while the user has never changed a sound setting, so
 * callers can keep their built-in defaults instead of a zeroed-out snapshot.
 */
interface SoundSettingsPersistence {
    suspend fun save(settings: PersistedSoundSettings): AppResult<Unit>

    fun observe(): Flow<PersistedSoundSettings?>
}
