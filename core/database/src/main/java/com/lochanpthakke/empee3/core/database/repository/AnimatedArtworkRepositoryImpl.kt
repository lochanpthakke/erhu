package com.lochanpthakke.empee3.core.database.repository

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.database.dao.AnimatedArtworkDao
import com.lochanpthakke.empee3.core.database.entity.AnimatedArtworkEntity
import com.lochanpthakke.empee3.core.datastore.proto.AnimatedArtOrientationProto
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.AnimatedArtStatus
import com.lochanpthakke.empee3.core.model.LocalAnimatedArtwork
import com.lochanpthakke.empee3.core.network.apple.AlbumTagNormalizer
import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkConfig
import com.lochanpthakke.empee3.core.network.apple.AnimatedArtworkNetworkDataSource
import com.lochanpthakke.empee3.core.network.apple.EditorialVideoUrls
import com.lochanpthakke.empee3.core.network.apple.HlsVariantStream
import com.lochanpthakke.empee3.core.network.apple.ResolveOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimatedArtworkRepositoryImpl @Inject constructor(
    private val dao: AnimatedArtworkDao,
    private val networkDataSource: AnimatedArtworkNetworkDataSource,
    private val fileStore: ArtworkFileStore,
    private val dataStore: DataStore<PlaybackStateProto>,
    private val dispatchers: DispatcherProvider,
) : AnimatedArtworkRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val activeJobs = ConcurrentHashMap<Pair<Long, AnimatedArtOrientation>, Deferred<AppResult<LocalAnimatedArtwork>>>()

    override fun observeAnimatedArtwork(albumId: Long): Flow<LocalAnimatedArtwork?> {
        return dao.observeByAlbumId(albumId).map { entity ->
            entity?.toDomainModel(fileStore)
        }
    }

    override suspend fun resolveAlbum(albumArtist: String, album: String, trackCount: Int?): AppResult<ResolveOutcome> {
        return networkDataSource.resolveAlbum(albumArtist, album, trackCount)
    }

    override suspend fun fetchMasterUrls(catalogAlbumId: String, storefront: String): AppResult<EditorialVideoUrls> {
        return networkDataSource.fetchMasterUrls(catalogAlbumId, storefront)
    }

    override suspend fun fetchVariantsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> {
        return networkDataSource.fetchVariantStreamsForMasterUrl(masterUrl)
    }

    override suspend fun downloadAndSaveVariant(
        albumId: Long,
        albumArtist: String,
        album: String,
        catalogAlbumId: String,
        storefront: String,
        orientation: AnimatedArtOrientation,
        variantUrl: String
    ): AppResult<LocalAnimatedArtwork> = withContext(dispatchers.io) {
        val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
        val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))
        val now = System.currentTimeMillis()

        val entity = dao.getByAlbumId(albumId)
        val updatingEntity = (entity ?: AnimatedArtworkEntity(
            albumId = albumId,
            normalizedArtist = normalizedArtist,
            normalizedAlbum = normalizedAlbum,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )).copy(
            albumId = albumId,
            catalogAlbumId = catalogAlbumId,
            storefront = storefront,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )
        dao.upsert(updatingEntity)

        val destFile = when (orientation) {
            AnimatedArtOrientation.SQUARE_1_1 -> fileStore.getSquareFile(albumId, album, albumArtist)
            AnimatedArtOrientation.TALL_3_4 -> fileStore.getTallFile(albumId, album, albumArtist)
        }
        val tmpFile = File(fileStore.baseDir, "${albumId}_${orientation.name.lowercase()}.mp4.tmp")

        val dlResult = networkDataSource.downloadVariantToFile(variantUrl, destFile, tmpFile)
        if (dlResult is AppResult.Success) {
            val finalEntity = updatingEntity.copy(
                hasSquare = if (orientation == AnimatedArtOrientation.SQUARE_1_1) true else updatingEntity.hasSquare,
                hasTall = if (orientation == AnimatedArtOrientation.TALL_3_4) true else updatingEntity.hasTall,
                squareFileName = if (orientation == AnimatedArtOrientation.SQUARE_1_1) destFile.name else updatingEntity.squareFileName,
                tallFileName = if (orientation == AnimatedArtOrientation.TALL_3_4) destFile.name else updatingEntity.tallFileName,
                status = AnimatedArtStatus.AVAILABLE.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            dao.upsert(finalEntity)
            AppResult.Success(finalEntity.toDomainModel(fileStore))
        } else {
            val finalEntity = updatingEntity.copy(
                status = AnimatedArtStatus.FAILED.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            dao.upsert(finalEntity)
            AppResult.Error((dlResult as AppResult.Error).error)
        }
    }

    override suspend fun fetchAndStoreArtwork(
        albumId: Long,
        albumArtist: String,
        album: String,
        trackCount: Int?,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean
    ): AppResult<LocalAnimatedArtwork> {
        val key = Pair(albumId, orientation)

        val existingJob = activeJobs[key]
        if (existingJob != null) {
            return existingJob.await()
        }

        val deferred = scope.async {
            try {
                performFetchAndStore(albumId, albumArtist, album, trackCount, orientation, forceRefresh)
            } finally {
                activeJobs.remove(key)
            }
        }

        activeJobs[key] = deferred
        return deferred.await()
    }

    override suspend fun fetchAndStoreArtworkByUrl(
        albumId: Long,
        albumArtist: String,
        album: String,
        catalogAlbumId: String,
        storefront: String,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean
    ): AppResult<LocalAnimatedArtwork> {
        val key = Pair(albumId, orientation)

        val existingJob = activeJobs[key]
        if (existingJob != null) {
            return existingJob.await()
        }

        val deferred = scope.async {
            try {
                performFetchAndStoreDirect(albumId, albumArtist, album, catalogAlbumId, storefront, orientation, forceRefresh)
            } finally {
                activeJobs.remove(key)
            }
        }

        activeJobs[key] = deferred
        return deferred.await()
    }

    private suspend fun performFetchAndStore(
        albumId: Long,
        albumArtist: String,
        album: String,
        trackCount: Int?,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean
    ): AppResult<LocalAnimatedArtwork> = withContext(dispatchers.io) {
        val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
        val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))

        val entity = dao.getByAlbumId(albumId) ?: dao.getByNormalizedKey(normalizedArtist, normalizedAlbum)

        val now = System.currentTimeMillis()

        if (!forceRefresh && entity != null) {
            val status = parseStatus(entity.status)
            val fileExists = when (orientation) {
                AnimatedArtOrientation.SQUARE_1_1 -> entity.squareFileName != null && fileStore.getSquareFile(albumId, album, albumArtist).exists()
                AnimatedArtOrientation.TALL_3_4 -> entity.tallFileName != null && fileStore.getTallFile(albumId, album, albumArtist).exists()
            }

            if (fileExists) {
                return@withContext AppResult.Success(entity.toDomainModel(fileStore))
            }

            if (status == AnimatedArtStatus.NO_ANIMATION && (now - entity.updatedAtEpochMs) < AnimatedArtworkConfig.TTL_NO_ANIMATION_MS) {
                return@withContext AppResult.Success(entity.toDomainModel(fileStore))
            }

            if (status == AnimatedArtStatus.NO_MATCH && (now - entity.updatedAtEpochMs) < AnimatedArtworkConfig.TTL_NO_MATCH_MS) {
                return@withContext AppResult.Success(entity.toDomainModel(fileStore))
            }
        }

        val updatingEntity = (entity ?: AnimatedArtworkEntity(
            albumId = albumId,
            normalizedArtist = normalizedArtist,
            normalizedAlbum = normalizedAlbum,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )).copy(
            albumId = albumId,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )
        dao.upsert(updatingEntity)

        val catalogAlbumId = updatingEntity.catalogAlbumId
        val storefront = updatingEntity.storefront

        if (catalogAlbumId != null && storefront != null) {
            return@withContext performFetchAndStoreDirect(albumId, albumArtist, album, catalogAlbumId, storefront, orientation, forceRefresh)
        }

        val resolveResult = networkDataSource.resolveAlbum(albumArtist, album, trackCount)
        when (resolveResult) {
            is AppResult.Success -> {
                when (val outcome = resolveResult.data) {
                    is ResolveOutcome.NoMatch -> {
                        val finalEntity = updatingEntity.copy(
                            status = AnimatedArtStatus.NO_MATCH.name,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                        dao.upsert(finalEntity)
                        AppResult.Success(finalEntity.toDomainModel(fileStore))
                    }
                    is ResolveOutcome.Matched -> {
                        performFetchAndStoreDirect(
                            albumId = albumId,
                            albumArtist = albumArtist,
                            album = album,
                            catalogAlbumId = outcome.catalogAlbumId,
                            storefront = outcome.storefront,
                            orientation = orientation,
                            forceRefresh = forceRefresh
                        )
                    }
                }
            }
            is AppResult.Error -> {
                val finalEntity = updatingEntity.copy(
                    status = AnimatedArtStatus.FAILED.name,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.upsert(finalEntity)
                AppResult.Error(resolveResult.error)
            }
        }
    }

    private suspend fun performFetchAndStoreDirect(
        albumId: Long,
        albumArtist: String,
        album: String,
        catalogAlbumId: String,
        storefront: String,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean
    ): AppResult<LocalAnimatedArtwork> = withContext(dispatchers.io) {
        val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
        val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))

        val now = System.currentTimeMillis()
        val entity = dao.getByAlbumId(albumId)

        val updatingEntity = (entity ?: AnimatedArtworkEntity(
            albumId = albumId,
            normalizedArtist = normalizedArtist,
            normalizedAlbum = normalizedAlbum,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )).copy(
            albumId = albumId,
            catalogAlbumId = catalogAlbumId,
            storefront = storefront,
            status = AnimatedArtStatus.DOWNLOADING.name,
            updatedAtEpochMs = now
        )
        dao.upsert(updatingEntity)

        val destFile = when (orientation) {
            AnimatedArtOrientation.SQUARE_1_1 -> fileStore.getSquareFile(albumId, album, albumArtist)
            AnimatedArtOrientation.TALL_3_4 -> fileStore.getTallFile(albumId, album, albumArtist)
        }
        val tmpFile = File(fileStore.baseDir, "${albumId}_${orientation.name.lowercase()}.mp4.tmp")

        val masterUrlsResult = networkDataSource.fetchMasterUrls(catalogAlbumId, storefront)
        if (masterUrlsResult is AppResult.Success) {
            val targetUrl = when (orientation) {
                AnimatedArtOrientation.SQUARE_1_1 -> masterUrlsResult.data.squareMasterUrl
                AnimatedArtOrientation.TALL_3_4 -> masterUrlsResult.data.tallMasterUrl
            }
            val hasSquare = masterUrlsResult.data.squareMasterUrl != null
            val hasTall = masterUrlsResult.data.tallMasterUrl != null

            if (targetUrl == null) {
                val finalEntity = updatingEntity.copy(
                    hasSquare = hasSquare,
                    hasTall = hasTall,
                    status = AnimatedArtStatus.NO_ANIMATION.name,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.upsert(finalEntity)
                AppResult.Success(finalEntity.toDomainModel(fileStore))
            } else {
                val dlResult = networkDataSource.downloadToFile(targetUrl, destFile, tmpFile)
                if (dlResult is AppResult.Success) {
                    val finalEntity = updatingEntity.copy(
                        hasSquare = hasSquare,
                        hasTall = hasTall,
                        squareFileName = if (orientation == AnimatedArtOrientation.SQUARE_1_1) destFile.name else updatingEntity.squareFileName,
                        tallFileName = if (orientation == AnimatedArtOrientation.TALL_3_4) destFile.name else updatingEntity.tallFileName,
                        status = AnimatedArtStatus.AVAILABLE.name,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    dao.upsert(finalEntity)
                    AppResult.Success(finalEntity.toDomainModel(fileStore))
                } else {
                    val finalEntity = updatingEntity.copy(
                        status = AnimatedArtStatus.FAILED.name,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    dao.upsert(finalEntity)
                    AppResult.Error((dlResult as AppResult.Error).error)
                }
            }
        } else {
            val finalEntity = updatingEntity.copy(
                status = AnimatedArtStatus.FAILED.name,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            dao.upsert(finalEntity)
            AppResult.Error((masterUrlsResult as AppResult.Error).error)
        }
    }

    override suspend fun deleteArtwork(albumId: Long) = withContext(dispatchers.io) {
        val entity = dao.getByAlbumId(albumId)
        val albumTitle = entity?.normalizedAlbum
        val artist = entity?.normalizedArtist

        val squareFile = fileStore.getSquareFile(albumId, albumTitle, artist)
        val tallFile = fileStore.getTallFile(albumId, albumTitle, artist)
        if (squareFile.exists()) squareFile.delete()
        if (tallFile.exists()) tallFile.delete()
        dao.deleteByAlbumId(albumId)
    }

    override suspend fun deleteAllArtwork() = withContext(dispatchers.io) {
        fileStore.deleteAll()
        dao.deleteAll()
    }

    override suspend fun totalStorageBytes(): Long = withContext(dispatchers.io) {
        fileStore.totalBytes()
    }

    override fun observePreferredOrientation(): Flow<AnimatedArtOrientation> {
        return dataStore.data.map { proto ->
            when (proto.animatedArtOrientation) {
                AnimatedArtOrientationProto.SQUARE_1_1 -> AnimatedArtOrientation.SQUARE_1_1
                AnimatedArtOrientationProto.TALL_3_4 -> AnimatedArtOrientation.TALL_3_4
                else -> AnimatedArtOrientation.SQUARE_1_1
            }
        }
    }

    override suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation) {
        dataStore.updateData { proto ->
            proto.toBuilder()
                .setAnimatedArtOrientation(
                    when (orientation) {
                        AnimatedArtOrientation.SQUARE_1_1 -> AnimatedArtOrientationProto.SQUARE_1_1
                        AnimatedArtOrientation.TALL_3_4 -> AnimatedArtOrientationProto.TALL_3_4
                    }
                )
                .build()
        }
    }

    override suspend fun cleanupOnStartup() = withContext(dispatchers.io) {
        dao.resetDownloading()
        fileStore.cleanTempFiles()
    }

    private fun parseStatus(status: String): AnimatedArtStatus {
        return try {
            AnimatedArtStatus.valueOf(status)
        } catch (e: Exception) {
            AnimatedArtStatus.UNCHECKED
        }
    }

    private fun AnimatedArtworkEntity.toDomainModel(fileStore: ArtworkFileStore): LocalAnimatedArtwork {
        val squarePath = if (squareFileName != null) {
            val file = File(fileStore.baseDir, squareFileName)
            if (file.exists()) file.absolutePath else null
        } else null

        val tallPath = if (tallFileName != null) {
            val file = File(fileStore.baseDir, tallFileName)
            if (file.exists()) file.absolutePath else null
        } else null

        return LocalAnimatedArtwork(
            albumId = albumId,
            squareFilePath = squarePath,
            tallFilePath = tallPath,
            hasSquare = hasSquare,
            hasTall = hasTall,
            status = parseStatus(status),
            catalogAlbumId = catalogAlbumId,
            storefront = storefront,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }
}
