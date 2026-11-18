package com.erhu.erhu.core.database.repository

import com.erhu.erhu.core.database.dao.RemoteLibraryDao
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
import com.erhu.erhu.core.database.entity.RemoteSourceEntity
import com.erhu.erhu.core.database.entity.RemoteTrackEntity
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.RemoteSource
import com.erhu.erhu.core.model.RemoteTrack
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connected remote libraries and the remote tracks erhu knows by id. Browsing itself is live from
 * the provider; a track is stored here only once it is turned into a [Song] (queued, played,
 * favourited), so its id survives restarts.
 */
@Singleton
class RemoteLibraryRepository
    @Inject
    constructor(
        private val dao: RemoteLibraryDao,
        private val timeSource: TimeSource,
    ) : com.erhu.erhu.core.model.RemoteSourceLookup {
        /** For the player's loading threads, which cannot suspend. Never call on the main thread. */
        override fun source(id: Long): RemoteSource? = kotlinx.coroutines.runBlocking { getSource(id) }

        fun observeSources(): Flow<List<RemoteSource>> = dao.observeSources().map { rows -> rows.mapNotNull { it.toDomainModel() } }

        suspend fun getSource(id: Long): RemoteSource? = dao.getSource(id)?.toDomainModel()

        /** Returns the new source's id. Secrets are stored separately, keyed by this id. */
        suspend fun addSource(source: RemoteSource): Long =
            dao.insertSource(
                RemoteSourceEntity(
                    provider = source.provider.key,
                    name = source.name,
                    baseUrl = source.baseUrl,
                    account = source.account,
                    allowInsecure = source.allowInsecure,
                    addedAt = timeSource.now(),
                ),
            )

        /** Forgets the source and every track of it. The caller also deletes its stored secret. */
        suspend fun removeSource(id: Long) = dao.deleteSource(id)

        /** Playable songs for [tracks] of [source], in the same order, each with a stable local id. */
        suspend fun toSongs(
            source: RemoteSource,
            tracks: List<RemoteTrack>,
        ): List<Song> {
            val now = timeSource.now()
            return dao
                .remember(
                    tracks.map {
                        RemoteTrackEntity(
                            sourceId = source.id,
                            provider = source.provider.key,
                            remoteId = it.remoteId,
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                            trackNumber = it.trackNumber,
                            discNumber = it.discNumber,
                            mimeType = it.mimeType,
                            artworkKey = it.artworkKey,
                            isFavorite = it.isFavorite,
                            genre = it.genre,
                            year = it.year,
                            updatedAt = now,
                        )
                    },
                ).mapNotNull { it.toSong() }
        }

        suspend fun getLastLocation(sourceId: Long): String? = dao.getLastLocation(sourceId)

        suspend fun setLastLocation(
            sourceId: Long,
            location: String?,
        ) = dao.setLastLocation(sourceId, location)

        /** Stores tags read from the file and returns [song] with them applied. */
        suspend fun applyTags(
            song: Song,
            title: String?,
            artist: String?,
            album: String?,
            durationMs: Long,
        ): Song {
            val updated =
                song.copy(
                    title = title?.takeIf { it.isNotBlank() } ?: song.title,
                    artist = artist?.takeIf { it.isNotBlank() } ?: song.artist,
                    album = album?.takeIf { it.isNotBlank() } ?: song.album,
                    durationMs = durationMs.takeIf { it > 0 } ?: song.durationMs,
                )
            dao.setTags(song.id, updated.title, updated.artist, updated.album, updated.durationMs)
            return updated
        }

        /** Remote folders whose audio is shown in the Library. */
        fun observeLibraryFolders(): Flow<List<RemoteLibraryFolderEntity>> = dao.observeLibraryFolders()

        suspend fun getLibraryFolders(): List<RemoteLibraryFolderEntity> = dao.getLibraryFolders()

        /** Adds [folderId] of [sourceId] to the Library and returns its row (the existing one when already added). */
        suspend fun addLibraryFolder(
            sourceId: Long,
            folderId: String,
            driveId: String?,
            name: String,
        ): RemoteLibraryFolderEntity {
            dao.insertLibraryFolder(
                RemoteLibraryFolderEntity(sourceId = sourceId, folderId = folderId, driveId = driveId, name = name, addedAt = timeSource.now()),
            )
            return checkNotNull(dao.findLibraryFolder(sourceId, folderId))
        }

        suspend fun removeLibraryFolder(id: Long) = dao.deleteLibraryFolder(id)

        /** [tracks] (song id to the folder directly holding it) become the folder's whole Library content. */
        suspend fun setLibraryFolderTracks(
            folderId: Long,
            tracks: Map<Long, String>,
        ) {
            dao.replaceLibraryFolderTracks(folderId, tracks)
            dao.setLibraryFolderSynced(folderId, timeSource.now())
        }

        /** The stored rows of [songIds] (what a sync has already read about them). */
        suspend fun getTracks(songIds: List<Long>): Map<Long, RemoteTrackEntity> =
            songIds.chunked(DETAILS_CHUNK).flatMap { dao.getTracksByIds(it) }.associateBy { it.id }

        /** Stores what the details pass found; the track is not looked at again until [RemoteDetails.VERSION] changes. */
        suspend fun applyDetails(
            songId: Long,
            genre: String?,
            year: Int,
            lyrics: String?,
        ) = dao.setDetails(songId, genre?.takeIf { it.isNotBlank() }, year, lyrics?.takeIf { it.isNotBlank() }, com.erhu.erhu.core.database.entity.RemoteDetails.VERSION)

        fun observeIsFavorite(songId: Long): Flow<Boolean> = dao.observeFavorite(songId).map { it == true }

        suspend fun setFavorite(
            songId: Long,
            favorite: Boolean,
        ) {
            dao.setFavorite(songId, favorite)
            dao.setLibraryFavorite(songId, favorite)
        }

        suspend fun setUnavailable(
            songId: Long,
            unavailable: Boolean,
        ) = dao.setUnavailable(songId, unavailable)
    }

internal fun RemoteSourceEntity.toDomainModel(): RemoteSource? {
    val provider = RemoteProvider.fromKey(provider) ?: return null
    return RemoteSource(id, provider, name, baseUrl, account, allowInsecure)
}

/** Null only for a row written by a provider this build does not know. */
internal fun RemoteTrackEntity.toSong(): Song? {
    val provider = RemoteProvider.fromKey(provider) ?: return null
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = RemoteRef(provider, sourceId, remoteId).streamUri(),
        artworkUri = artworkKey?.let { RemoteRef.artworkUri(provider, sourceId, it) },
        isFavorite = isFavorite,
        trackNumber = trackNumber,
        discNumber = discNumber,
        mimeType = mimeType,
    )
}

/** Stay below SQLite's bound-variable limit. */
private const val DETAILS_CHUNK = 500
