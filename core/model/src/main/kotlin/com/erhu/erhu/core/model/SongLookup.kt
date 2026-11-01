package com.erhu.erhu.core.model

/**
 * Rehydrates persisted song IDs into playable [Song] instances.
 *
 * Implementations return only songs that still exist, in no guaranteed order.
 */
interface SongLookup {
    suspend fun getSongsByIds(ids: List<Long>): List<Song>
}
