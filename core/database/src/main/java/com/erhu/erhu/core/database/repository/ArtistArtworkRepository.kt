package com.erhu.erhu.core.database.repository

import androidx.room.withTransaction
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.dao.ArtistDao
import com.erhu.erhu.core.database.dao.LibraryModelDao
import com.erhu.erhu.core.database.entity.ArtistArtworkEntity
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.model.Artist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A decision of the automatic artwork lookup. A failed lookup is not a decision and is never recorded. */
sealed interface AutomaticArtwork {
    data class Found(
        val url: String,
        val providerArtistId: Long,
    ) : AutomaticArtwork

    data object NotFound : AutomaticArtwork
}

/**
 * Artist profile artwork: custom first, then automatic, then the placeholder.
 *
 * `artist_artwork` is the source of truth (kept across scans, by artist key); `artists.artwork_uri`
 * is a copy for display, re-synced in the same transaction as every write. Automatic writes never
 * replace a custom image, and custom images are only changed by [setCustomArtwork]/[clearCustomArtwork].
 */
interface ArtistArtworkRepository {
    /** Artists the automatic lookup should (re)decide now. */
    suspend fun artistsNeedingAutomaticLookup(now: Long): AppResult<List<Artist>>

    /** Records an automatic decision; false (nothing written) when the artist has a custom image. */
    suspend fun recordAutomaticArtwork(
        artistKey: String,
        artwork: AutomaticArtwork,
    ): AppResult<Boolean>

    /** Keys of the artists that have a custom image. */
    fun observeCustomArtworkKeys(): Flow<Set<String>>

    suspend fun setCustomArtwork(
        artistId: Long,
        imageBytes: ByteArray,
    ): AppResult<Unit>

    /** Deletes the custom image; the artist then has no artwork until the automatic lookup decides again. */
    suspend fun clearCustomArtwork(artistId: Long): AppResult<Unit>
}

class ArtistArtworkRepositoryImpl
    @Inject
    constructor(
        private val database: ErhuDatabase,
        private val artistDao: ArtistDao,
        private val libraryModelDao: LibraryModelDao,
        private val store: ArtistArtworkStore,
        private val timeSource: TimeSource,
    ) : ArtistArtworkRepository {
        override suspend fun artistsNeedingAutomaticLookup(now: Long): AppResult<List<Artist>> =
            appResultOf(mapError = { AppError.DatabaseError("Could not read artist artwork", it) }) {
                val stored = libraryModelDao.getArtworkByKey().associateBy { it.artistKey }
                artistDao
                    .getAllArtists()
                    .first()
                    // Unresolved credits ("Tyler/ The Creator") are not one artist name: no profile photo.
                    .filter { it.artistKey.isNotEmpty() && !it.isUnresolved }
                    .filter { ArtistArtworkCache.needsAutomaticLookup(stored[it.artistKey], now) }
                    .map { it.toDomainModel() }
            }

        override suspend fun recordAutomaticArtwork(
            artistKey: String,
            artwork: AutomaticArtwork,
        ): AppResult<Boolean> =
            appResultOf(mapError = { AppError.DatabaseError("Could not save artist artwork", it) }) {
                database.withTransaction { writeAutomatic(artistKey, artwork) }
            }

        private suspend fun writeAutomatic(
            artistKey: String,
            artwork: AutomaticArtwork,
        ): Boolean {
            val current = libraryModelDao.getArtwork(artistKey)
            if (current != null && current.source != ArtistArtworkEntity.SOURCE_AUTO) return false
            val row =
                when (artwork) {
                    is AutomaticArtwork.Found ->
                        ArtistArtworkEntity(
                            artistKey = artistKey,
                            artworkUri = artwork.url,
                            updatedAt = timeSource.now(),
                            source = ArtistArtworkEntity.SOURCE_AUTO,
                            providerArtistId = artwork.providerArtistId,
                            matcherVersion = ArtistArtworkCache.MATCHER_VERSION,
                        )
                    AutomaticArtwork.NotFound ->
                        ArtistArtworkEntity(
                            artistKey = artistKey,
                            artworkUri = ArtistArtworkCache.NOT_FOUND,
                            updatedAt = timeSource.now(),
                            source = ArtistArtworkEntity.SOURCE_AUTO,
                            providerArtistId = null,
                            matcherVersion = ArtistArtworkCache.MATCHER_VERSION,
                        )
                }
            libraryModelDao.upsertArtwork(row)
            artistDao.syncArtwork(artistKey)
            return true
        }

        override fun observeCustomArtworkKeys(): Flow<Set<String>> = libraryModelDao.observeCustomArtworkKeys().map { it.toSet() }

        override suspend fun setCustomArtwork(
            artistId: Long,
            imageBytes: ByteArray,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not save the artist image", it) }) {
                val key = artistKeyOf(artistId)
                val now = timeSource.now()
                val uri = store.save(artistId, imageBytes, now)
                var previous: ArtistArtworkEntity? = null
                var saved = false
                try {
                    previous =
                        database.withTransaction {
                            val current = libraryModelDao.getArtwork(key)
                            libraryModelDao.upsertArtwork(
                                ArtistArtworkEntity(
                                    artistKey = key,
                                    artworkUri = uri,
                                    updatedAt = now,
                                    source = ArtistArtworkEntity.SOURCE_CUSTOM,
                                    providerArtistId = null,
                                    matcherVersion = 0,
                                ),
                            )
                            artistDao.syncArtwork(key)
                            current
                        }
                    saved = true
                } finally {
                    // The new file is only kept once the row that points at it is committed.
                    if (!saved) store.delete(uri)
                }
                // Only a replaced custom file is ours to delete; an automatic url is left alone by the store anyway.
                if (previous?.source == ArtistArtworkEntity.SOURCE_CUSTOM && previous.artworkUri != uri) store.delete(previous.artworkUri)
            }

        override suspend fun clearCustomArtwork(artistId: Long): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not remove the artist image", it) }) {
                val key = artistKeyOf(artistId)
                val removed =
                    database.withTransaction {
                        val current = libraryModelDao.getArtwork(key)
                        if (current?.source != ArtistArtworkEntity.SOURCE_CUSTOM) return@withTransaction null
                        libraryModelDao.deleteCustomArtwork(key)
                        artistDao.syncArtwork(key)
                        current
                    }
                store.delete(removed?.artworkUri)
            }

        private suspend fun artistKeyOf(artistId: Long): String =
            artistDao.getArtistById(artistId)?.artistKey?.takeIf { it.isNotEmpty() }
                ?: error("Artist $artistId is not in the library")
    }
