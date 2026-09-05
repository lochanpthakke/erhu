package com.lochanpthakke.empee3.core.model

/**
 * Rehydrates persisted song IDs into playable [Song] instances.
 *
 * Stubbed in Phase 2 — no implementation exists yet. Wired to real Room
 * queries once the library layer (Phase 3) is in place.
 */
interface SongLookup {
    suspend fun getSongsByIds(ids: List<Long>): List<Song>
}
