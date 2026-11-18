package com.erhu.erhu.core.database.scanner

import androidx.room.withTransaction
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.dao.AlbumDao
import com.erhu.erhu.core.database.dao.ArtistDao
import com.erhu.erhu.core.database.dao.LibraryModelDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.entity.AlbumArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.AlbumEntity
import com.erhu.erhu.core.database.entity.ArtistEntity
import com.erhu.erhu.core.database.entity.SongArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.entity.UnverifiedCreditSegmentEntity
import com.erhu.erhu.core.database.library.LibraryModel
import javax.inject.Inject

data class WriteSummary(
    val upserted: Int,
    val removed: Int,
)

data class LibraryEntities(
    val songs: List<SongEntity>,
    val crossRefs: List<SongArtistCrossRefEntity>,
    val albums: List<AlbumEntity>,
    val albumArtists: List<AlbumArtistCrossRefEntity>,
    val artists: List<ArtistEntity>,
    val unverified: List<UnverifiedCreditSegmentEntity>,
)

/** Maps one built model onto entities; album year/artwork come from the album's first track. */
internal fun LibraryModel.toEntities(
    scannedSongs: List<SongEntity>,
    artworkByKey: Map<String, String>,
): LibraryEntities {
    val songById = scannedSongs.associateBy { it.id }
    val assignedSongs =
        scannedSongs.map { song ->
            this.songs[song.id]?.let { song.copy(albumId = it.albumId, artistId = it.primaryArtistId) } ?: song
        }
    val albumEntities =
        albums.map { album ->
            val first =
                album.songIds
                    .mapNotNull(songById::get)
                    .minWithOrNull(compareBy({ it.discNumber }, { it.trackNumber }, { it.id }))
            AlbumEntity(
                id = album.id,
                title = album.title,
                artist = album.displayArtist,
                artistId = album.leadArtistId,
                songCount = album.songIds.size,
                year = first?.year ?: 0,
                artworkUri = first?.artworkUri,
                isCompilation = album.isCompilation,
                albumArtistInconsistent = album.albumArtistInconsistent,
                groupingKey = album.groupingKey,
            )
        }
    return LibraryEntities(
        songs = assignedSongs,
        crossRefs =
            credits.map {
                SongArtistCrossRefEntity(
                    songId = it.songId,
                    artistId = it.artistId,
                    role = it.role.name,
                    position = it.position,
                    creditText = it.creditText,
                    isUnresolved = it.isUnresolved,
                )
            },
        albums = albumEntities,
        albumArtists = albumArtists.map { AlbumArtistCrossRefEntity(it.albumId, it.artistId, it.position) },
        artists =
            artists.map {
                ArtistEntity(
                    id = it.id,
                    name = it.name,
                    songCount = it.songCount,
                    albumCount = it.albumCount,
                    artworkUri = artworkByKey[it.key],
                    artistKey = it.key,
                    isUnresolved = it.isUnresolved,
                )
            },
        unverified = unverifiedSegments.map { UnverifiedCreditSegmentEntity(it.songId, it.artistId, it.segmentKey, it.segmentIndex) },
    )
}

/** Writes one scan atomically: derived tables are replaced; favorites, playlists, artwork and aliases are kept. */
class LibraryWriter
    @Inject
    constructor(
        private val database: ErhuDatabase,
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val libraryModelDao: LibraryModelDao,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) {
        private val prefs by lazy { context.getSharedPreferences(WRITER_PREFS, android.content.Context.MODE_PRIVATE) }

        suspend fun write(entities: LibraryEntities): WriteSummary =
            database.withTransaction {
                val existing = songDao.getAllSongsOnce()
                val plan = planLibrarySync(scanned = entities.songs, existing = existing)
                plan.deletedIds.chunked(CHUNK).forEach { songDao.deleteSongsByIds(it) }
                // Every write wakes every screen watching the songs table; rows that came out identical are skipped.
                val existingById = existing.associateBy { it.id }
                val changed = plan.upserts.filter { it != existingById[it.id] }
                changed.chunked(CHUNK).forEach { songDao.upsertAll(it) }
                // Albums, artists and credits are rebuilt only when the computed model differs from the last
                // one written (and the tables still hold it); a launch with no library change writes nothing.
                val fingerprint = derivedFingerprint(entities)
                val unchanged =
                    prefs.getString(FINGERPRINT_KEY, null) == fingerprint &&
                        albumDao.count() == entities.albums.size &&
                        artistDao.count() == entities.artists.size
                if (unchanged) return@withTransaction WriteSummary(upserted = changed.size, removed = plan.deletedIds.size)
                val crossRefs = database.songArtistCrossRefDao()
                crossRefs.deleteAll()
                entities.crossRefs.chunked(CHUNK).forEach { crossRefs.upsertAll(it) }
                albumDao.deleteAll()
                artistDao.deleteAll()
                libraryModelDao.deleteAlbumArtists()
                libraryModelDao.deleteUnverifiedSegments()
                entities.albums.chunked(CHUNK).forEach { albumDao.upsertAll(it) }
                entities.artists.chunked(CHUNK).forEach { artistDao.upsertAll(it) }
                // Inside this transaction: artwork set (e.g. a custom image) after the scan read it is not undone.
                artistDao.syncAllArtwork()
                entities.albumArtists.chunked(CHUNK).forEach { libraryModelDao.insertAlbumArtists(it) }
                entities.unverified.chunked(CHUNK).forEach { libraryModelDao.insertUnverifiedSegments(it) }
                prefs.edit().putString(FINGERPRINT_KEY, fingerprint).apply()
                WriteSummary(upserted = changed.size, removed = plan.deletedIds.size)
            }

        private companion object {
            /** Stay well below SQLite's 999 bound-variable limit on API < 30. */
            const val CHUNK = 500
            const val WRITER_PREFS = "library_writer"
            const val FINGERPRINT_KEY = "derived_fingerprint"
        }
    }

/**
 * A fingerprint of everything derived from a scan (albums, artists, credits, album artists, unverified
 * segments), from the entities' own values: equal fingerprints mean the same rows would be written.
 */
internal fun derivedFingerprint(entities: LibraryEntities): String =
    listOf(entities.albums, entities.artists, entities.crossRefs, entities.albumArtists, entities.unverified)
        .joinToString("/") { "${it.size}:${it.hashCode()}:${it.toString().hashCode()}" }
