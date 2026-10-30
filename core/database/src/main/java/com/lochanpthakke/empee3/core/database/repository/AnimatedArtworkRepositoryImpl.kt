package com.lochanpthakke.empee3.core.database.repository

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.getOrNull
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
import com.lochanpthakke.empee3.core.network.apple.ArtworkCompressor
import com.lochanpthakke.empee3.core.network.apple.EditorialVideoUrls
import com.lochanpthakke.empee3.core.network.apple.HlsPlaylistParser
import com.lochanpthakke.empee3.core.network.apple.HlsVariantStream
import com.lochanpthakke.empee3.core.network.apple.ResolveOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimatedArtworkRepositoryImpl
    @Inject
    constructor(
        private val dao: AnimatedArtworkDao,
        private val networkDataSource: AnimatedArtworkNetworkDataSource,
        private val fileStore: ArtworkFileStore,
        private val dataStore: DataStore<PlaybackStateProto>,
        private val dispatchers: DispatcherProvider,
        private val compressor: ArtworkCompressor = ArtworkCompressor(dispatchers),
    ) : AnimatedArtworkRepository {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        private val lifecycle = Mutex()
        private val activeJobs = ConcurrentHashMap<Pair<Long, AnimatedArtOrientation>, Deferred<AppResult<LocalAnimatedArtwork>>>()
        private val compressionController =
            ArtworkCompressionController(
                scope,
                dispatchers,
                lifecycle,
                hasDownload = { albumId -> activeJobs.keys.any { it.first == albumId } },
                source = { albumId ->
                    dao.getByAlbumId(albumId)?.squareFileName?.let { name ->
                        File(fileStore.baseDir, name).takeIf {
                            it.isFile && it.canonicalFile.parentFile == fileStore.baseDir.canonicalFile
                        }
                    }
                },
                engine = compressor,
                recoverStaging = {
                    // These files can only be uncommitted compression outputs. The controller
                    // holds lifecycle and skips recovery while any compression job is alive.
                    fileStore.baseDir
                        .listFiles { file ->
                            file.isFile && file.name.startsWith(".artwork-compress-") && file.name.endsWith(".mp4.tmp")
                        }?.forEach { it.delete() }
                },
            )
        override val compression: ArtworkCompressionOperations = compressionController

        /** Downloadable covers found this session, so reopening an album asks Apple nothing. */
        private val offers = ConcurrentHashMap<Long, LiveCoverCheck.Downloadable>()

        override fun observeAnimatedArtwork(albumId: Long): Flow<LocalAnimatedArtwork?> =
            dao.observeByAlbumId(albumId).map { entity ->
                entity?.toDomainModel(fileStore)
            }

        override suspend fun resolveAlbum(
            albumArtist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<ResolveOutcome> = networkDataSource.resolveAlbum(albumArtist, album, trackCount)

        override suspend fun fetchMasterUrls(
            catalogAlbumId: String,
            storefront: String,
        ): AppResult<EditorialVideoUrls> = networkDataSource.fetchMasterUrls(catalogAlbumId, storefront)

        override suspend fun fetchVariantsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> =
            networkDataSource.fetchVariantStreamsForMasterUrl(masterUrl)

        override suspend fun downloadAndSaveVariant(
            albumId: Long,
            albumArtist: String,
            album: String,
            catalogAlbumId: String,
            storefront: String,
            orientation: AnimatedArtOrientation,
            variantUrl: String,
        ): AppResult<LocalAnimatedArtwork> =
            singleDownload(albumId, orientation) {
                withContext(dispatchers.io) {
                    val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
                    val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))
                    val now = System.currentTimeMillis()

                    val entity = dao.getByAlbumId(albumId)
                    val updatingEntity =
                        (
                            entity ?: AnimatedArtworkEntity(
                                albumId = albumId,
                                normalizedArtist = normalizedArtist,
                                normalizedAlbum = normalizedAlbum,
                                status = AnimatedArtStatus.DOWNLOADING.name,
                                updatedAtEpochMs = now,
                            )
                        ).copy(
                            albumId = albumId,
                            catalogAlbumId = catalogAlbumId,
                            storefront = storefront,
                            status = AnimatedArtStatus.DOWNLOADING.name,
                            updatedAtEpochMs = now,
                        )
                    dao.upsert(updatingEntity)

                    val destFile =
                        when (orientation) {
                            AnimatedArtOrientation.SQUARE_1_1 -> fileStore.getSquareFile(albumId, album, albumArtist)
                            AnimatedArtOrientation.TALL_3_4 -> fileStore.getTallFile(albumId, album, albumArtist)
                        }
                    val tmpFile = File(fileStore.baseDir, "${albumId}_${orientation.name.lowercase()}.mp4.tmp")

                    val dlResult = networkDataSource.downloadVariantToFile(variantUrl, destFile, tmpFile)
                    if (dlResult is AppResult.Success) {
                        val finalEntity =
                            updatingEntity.copy(
                                hasSquare = if (orientation == AnimatedArtOrientation.SQUARE_1_1) true else updatingEntity.hasSquare,
                                hasTall = if (orientation == AnimatedArtOrientation.TALL_3_4) true else updatingEntity.hasTall,
                                squareFileName =
                                    if (orientation ==
                                        AnimatedArtOrientation.SQUARE_1_1
                                    ) {
                                        destFile.name
                                    } else {
                                        updatingEntity.squareFileName
                                    },
                                tallFileName =
                                    if (orientation ==
                                        AnimatedArtOrientation.TALL_3_4
                                    ) {
                                        destFile.name
                                    } else {
                                        updatingEntity.tallFileName
                                    },
                                status = AnimatedArtStatus.AVAILABLE.name,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        dao.upsert(finalEntity)
                        AppResult.Success(finalEntity.toDomainModel(fileStore))
                    } else {
                        val finalEntity =
                            updatingEntity.copy(
                                status = updatingEntity.savedOrFailed(fileStore),
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        dao.upsert(finalEntity)
                        AppResult.Error((dlResult as AppResult.Error).error)
                    }
                }
            }

        override suspend fun fetchAndStoreArtwork(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
            orientation: AnimatedArtOrientation,
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> =
            singleDownload(albumId, orientation) {
                performFetchAndStore(albumId, albumArtist, album, trackCount, orientation, forceRefresh)
            }

        override suspend fun fetchAndStoreArtworkByUrl(
            albumId: Long,
            albumArtist: String,
            album: String,
            catalogAlbumId: String,
            storefront: String,
            orientation: AnimatedArtOrientation,
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> =
            singleDownload(albumId, orientation) {
                performFetchAndStoreDirect(albumId, albumArtist, album, catalogAlbumId, storefront, orientation, forceRefresh)
            }

        /**
         * Runs [download] once per album and orientation: callers arriving while it runs (repeated
         * taps) wait for the same result. It runs in the repository's scope, so leaving a screen does
         * not stop it; [cancelDownload] does, and then nothing partial is kept.
         */
        private suspend fun singleDownload(
            albumId: Long,
            orientation: AnimatedArtOrientation,
            download: suspend () -> AppResult<LocalAnimatedArtwork>,
        ): AppResult<LocalAnimatedArtwork> {
            val key = Pair(albumId, orientation)
            val job =
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        download()
                    } catch (e: CancellationException) {
                        withContext(NonCancellable) { forgetCancelledDownload(albumId, orientation) }
                        throw e
                    }
                }
            job.invokeOnCompletion { activeJobs.remove(key, job) }
            val running =
                lifecycle.withLock {
                    if (compressionController.isRunning(albumId)) {
                        job.cancel()
                        return AppResult.Error(AppError.ValidationError("This cover is being compressed. Try again when it finishes."))
                    }
                    activeJobs.putIfAbsent(key, job).also { if (it == null) job.start() }
                }
            if (running != null) {
                job.cancel()
                return running.await()
            }
            return job.await()
        }

        override fun cancelDownload(albumId: Long) {
            compressionController.cancel(albumId)
            activeJobs.filterKeys { it.first == albumId }.values.forEach { it.cancel() }
        }

        /** After a cancelled download: its temporary file goes, and the album's cover is offered again. */
        private suspend fun forgetCancelledDownload(
            albumId: Long,
            orientation: AnimatedArtOrientation,
        ) {
            File(fileStore.baseDir, "${albumId}_${orientation.name.lowercase()}.mp4.tmp").delete()
            val entity = dao.getByAlbumId(albumId) ?: return
            if (parseStatus(entity.status) != AnimatedArtStatus.DOWNLOADING) return
            val status =
                when {
                    entity.savedOrFailed(fileStore) == AnimatedArtStatus.AVAILABLE.name -> AnimatedArtStatus.AVAILABLE
                    entity.catalogAlbumId != null -> AnimatedArtStatus.FOUND
                    else -> AnimatedArtStatus.UNCHECKED
                }
            dao.upsert(entity.copy(status = status.name, updatedAtEpochMs = System.currentTimeMillis()))
        }

        override suspend fun checkLiveCover(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<LiveCoverCheck> =
            withContext(dispatchers.io) {
                appResultOf {
                    val preferred = observePreferredOrientation().first()
                    val entity = dao.getByAlbumId(albumId)
                    savedCover(entity, preferred)
                        ?: LiveCoverCheck.None.takeIf { entity != null && isRememberedAsNone(entity) }
                        ?: offers[albumId]
                        ?: lookUpLiveCover(albumId, albumArtist, album, trackCount, entity, preferred)
                }
            }

        /** The saved file of [entity], in [preferred] orientation if there is one, else the other. */
        private fun savedCover(
            entity: AnimatedArtworkEntity?,
            preferred: AnimatedArtOrientation,
        ): LiveCoverCheck.Downloaded? {
            entity ?: return null
            val saved =
                listOf(
                    AnimatedArtOrientation.SQUARE_1_1 to entity.squareFileName,
                    AnimatedArtOrientation.TALL_3_4 to entity.tallFileName,
                ).sortedByDescending { it.first == preferred }
                    .mapNotNull { (orientation, name) -> name?.let { orientation to File(fileStore.baseDir, it) } }
                    .firstOrNull { (_, file) -> file.isFile }
            return saved?.let { (orientation, file) -> LiveCoverCheck.Downloaded(orientation, file.absolutePath, file.length()) }
        }

        /** No match or no motion cover, checked recently enough to trust (the fetcher's own limits). */
        private fun isRememberedAsNone(entity: AnimatedArtworkEntity): Boolean {
            val age = System.currentTimeMillis() - entity.updatedAtEpochMs
            return when (parseStatus(entity.status)) {
                AnimatedArtStatus.NO_MATCH -> age < AnimatedArtworkConfig.TTL_NO_MATCH_MS
                AnimatedArtStatus.NO_ANIMATION -> age < AnimatedArtworkConfig.TTL_NO_ANIMATION_MS
                else -> false
            }
        }

        /**
         * Asks Apple whether the album has a motion cover (the album it was matched to before, else a
         * new match), then the phone-sized variant's resolution and size. Downloads nothing. Throws
         * on network failure, so nothing is remembered then.
         */
        private suspend fun lookUpLiveCover(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
            entity: AnimatedArtworkEntity?,
            preferred: AnimatedArtOrientation,
        ): LiveCoverCheck {
            val match = (matchFor(entity, albumArtist, album, trackCount) as? ResolveOutcome.Matched)
            val base =
                entity ?: AnimatedArtworkEntity(
                    albumId = albumId,
                    normalizedArtist = AlbumTagNormalizer.fold(albumArtist),
                    normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album)),
                    status = AnimatedArtStatus.UNCHECKED.name,
                    updatedAtEpochMs = 0L,
                )
            val matched =
                match?.let {
                    base.copy(
                        catalogAlbumId = it.catalogAlbumId,
                        storefront = it.storefront,
                        hasSquare = it.squareMasterUrl != null,
                        hasTall = it.tallMasterUrl != null,
                    )
                }
            val found =
                match?.let {
                    listOf(
                        AnimatedArtOrientation.SQUARE_1_1 to it.squareMasterUrl,
                        AnimatedArtOrientation.TALL_3_4 to it.tallMasterUrl,
                    ).sortedByDescending { (orientation, _) -> orientation == preferred }
                        .firstNotNullOfOrNull { (orientation, url) -> url?.let { orientation to it } }
                }
            return when {
                matched == null -> LiveCoverCheck.None.also { remember(base, AnimatedArtStatus.NO_MATCH) }
                found == null -> LiveCoverCheck.None.also { remember(matched, AnimatedArtStatus.NO_ANIMATION) }
                else ->
                    describe(found.first, found.second).also {
                        remember(matched, AnimatedArtStatus.FOUND)
                        offers[albumId] = it
                    }
            }
        }

        /** Apple's answer for the album: the edition it was matched to before, else a new match. */
        private suspend fun matchFor(
            entity: AnimatedArtworkEntity?,
            albumArtist: String,
            album: String,
            trackCount: Int?,
        ): ResolveOutcome {
            val catalogAlbumId = entity?.catalogAlbumId
            val storefront = entity?.storefront
            return if (catalogAlbumId != null && storefront != null) {
                val urls = networkDataSource.fetchMasterUrls(catalogAlbumId, storefront).orThrow()
                ResolveOutcome.Matched(catalogAlbumId, storefront, urls.squareMasterUrl, urls.tallMasterUrl)
            } else {
                networkDataSource.resolveAlbum(albumArtist, album, trackCount).orThrow()
            }
        }

        /** The variant a download would fetch (see AnimatedArtworkNetworkDataSourceImpl.downloadToFile): its resolution and size. */
        private suspend fun describe(
            orientation: AnimatedArtOrientation,
            masterUrl: String,
        ): LiveCoverCheck.Downloadable {
            val hlsVariants =
                networkDataSource
                    .fetchVariantStreamsForMasterUrl(masterUrl)
                    .getOrNull()
                    .orEmpty()

            val prefResolution = observePreferredResolution().first()
            val targetShortEdge =
                when (prefResolution) {
                    "Maximum" -> Int.MAX_VALUE
                    "720p" -> 720
                    "480p" -> 480
                    "360p" -> 360
                    else -> 1080
                }

            val chosenHlsVariant =
                HlsPlaylistParser.displayVariant(hlsVariants, targetShortEdge)
                    ?: hlsVariants.firstOrNull()

            val coverVariants =
                hlsVariants.map { hls ->
                    val bytes = networkDataSource.fetchVariantByteSize(hls.variantUrl).getOrNull()
                    LiveCoverVariant(
                        resolutionText = hls.resolutionText,
                        width = hls.width,
                        height = hls.height,
                        bytes = bytes,
                        estimatedSizeMb = hls.estimatedSizeMb,
                        variantUrl = hls.variantUrl,
                        bandwidth = hls.bandwidth,
                    )
                }

            val chosenBytes = chosenHlsVariant?.let { networkDataSource.fetchVariantByteSize(it.variantUrl).getOrNull() }
            val selectedCoverVariant =
                chosenHlsVariant?.let { hls ->
                    coverVariants.find { it.variantUrl == hls.variantUrl } ?: LiveCoverVariant(
                        resolutionText = hls.resolutionText,
                        width = hls.width,
                        height = hls.height,
                        bytes = chosenBytes,
                        estimatedSizeMb = hls.estimatedSizeMb,
                        variantUrl = hls.variantUrl,
                        bandwidth = hls.bandwidth,
                    )
                }

            return LiveCoverCheck.Downloadable(
                orientation = orientation,
                width = selectedCoverVariant?.width ?: chosenHlsVariant?.width,
                height = selectedCoverVariant?.height ?: chosenHlsVariant?.height,
                bytes = selectedCoverVariant?.bytes ?: chosenBytes,
                variants = coverVariants,
                selectedVariant = selectedCoverVariant,
            )
        }

        /** Stores what a check found, unless a download for the album is running (it owns the row then). */
        private suspend fun remember(
            entity: AnimatedArtworkEntity,
            status: AnimatedArtStatus,
        ) {
            if (dao.getByAlbumId(entity.albumId)?.status == AnimatedArtStatus.DOWNLOADING.name) return
            dao.upsert(entity.copy(status = status.name, updatedAtEpochMs = System.currentTimeMillis()))
        }

        private fun <T> AppResult<T>.orThrow(): T =
            when (this) {
                is AppResult.Success -> data
                is AppResult.Error -> throw Exception(error.message, error.cause)
            }

        private suspend fun performFetchAndStore(
            albumId: Long,
            albumArtist: String,
            album: String,
            trackCount: Int?,
            orientation: AnimatedArtOrientation,
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> =
            withContext(dispatchers.io) {
                val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
                val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))

                val entity = dao.getByAlbumId(albumId) ?: dao.getByNormalizedKey(normalizedArtist, normalizedAlbum)

                val now = System.currentTimeMillis()

                if (!forceRefresh && entity != null) {
                    val status = parseStatus(entity.status)
                    val fileExists =
                        when (orientation) {
                            AnimatedArtOrientation.SQUARE_1_1 ->
                                entity.squareFileName != null &&
                                    fileStore.getSquareFile(albumId, album, albumArtist).exists()
                            AnimatedArtOrientation.TALL_3_4 ->
                                entity.tallFileName != null &&
                                    fileStore.getTallFile(albumId, album, albumArtist).exists()
                        }

                    if (fileExists) {
                        return@withContext AppResult.Success(entity.toDomainModel(fileStore))
                    }

                    if (status == AnimatedArtStatus.NO_ANIMATION &&
                        (now - entity.updatedAtEpochMs) < AnimatedArtworkConfig.TTL_NO_ANIMATION_MS
                    ) {
                        return@withContext AppResult.Success(entity.toDomainModel(fileStore))
                    }

                    if (status == AnimatedArtStatus.NO_MATCH && (now - entity.updatedAtEpochMs) < AnimatedArtworkConfig.TTL_NO_MATCH_MS) {
                        return@withContext AppResult.Success(entity.toDomainModel(fileStore))
                    }
                }

                val updatingEntity =
                    (
                        entity ?: AnimatedArtworkEntity(
                            albumId = albumId,
                            normalizedArtist = normalizedArtist,
                            normalizedAlbum = normalizedAlbum,
                            status = AnimatedArtStatus.DOWNLOADING.name,
                            updatedAtEpochMs = now,
                        )
                    ).copy(
                        albumId = albumId,
                        status = AnimatedArtStatus.DOWNLOADING.name,
                        updatedAtEpochMs = now,
                    )
                dao.upsert(updatingEntity)

                val catalogAlbumId = updatingEntity.catalogAlbumId
                val storefront = updatingEntity.storefront

                // A refresh matches the album again: the remembered edition may be the wrong one (e.g. a
                // clean edition without the motion cover, from before editions were compared).
                if (!forceRefresh && catalogAlbumId != null && storefront != null) {
                    return@withContext performFetchAndStoreDirect(
                        albumId,
                        albumArtist,
                        album,
                        catalogAlbumId,
                        storefront,
                        orientation,
                        forceRefresh,
                    )
                }

                val resolveResult = networkDataSource.resolveAlbum(albumArtist, album, trackCount)
                when (resolveResult) {
                    is AppResult.Success -> {
                        when (val outcome = resolveResult.data) {
                            is ResolveOutcome.NoMatch -> {
                                val finalEntity =
                                    updatingEntity.copy(
                                        status = AnimatedArtStatus.NO_MATCH.name,
                                        updatedAtEpochMs = System.currentTimeMillis(),
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
                                    forceRefresh = forceRefresh,
                                )
                            }
                        }
                    }
                    is AppResult.Error -> {
                        val finalEntity =
                            updatingEntity.copy(
                                status = updatingEntity.savedOrFailed(fileStore),
                                updatedAtEpochMs = System.currentTimeMillis(),
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
            forceRefresh: Boolean,
        ): AppResult<LocalAnimatedArtwork> =
            withContext(dispatchers.io) {
                val normalizedArtist = AlbumTagNormalizer.fold(albumArtist)
                val normalizedAlbum = AlbumTagNormalizer.fold(AlbumTagNormalizer.cleanAlbumTitle(album))

                val now = System.currentTimeMillis()
                val entity = dao.getByAlbumId(albumId)

                val updatingEntity =
                    (
                        entity ?: AnimatedArtworkEntity(
                            albumId = albumId,
                            normalizedArtist = normalizedArtist,
                            normalizedAlbum = normalizedAlbum,
                            status = AnimatedArtStatus.DOWNLOADING.name,
                            updatedAtEpochMs = now,
                        )
                    ).copy(
                        albumId = albumId,
                        catalogAlbumId = catalogAlbumId,
                        storefront = storefront,
                        status = AnimatedArtStatus.DOWNLOADING.name,
                        updatedAtEpochMs = now,
                    )
                dao.upsert(updatingEntity)

                val destFile =
                    when (orientation) {
                        AnimatedArtOrientation.SQUARE_1_1 -> fileStore.getSquareFile(albumId, album, albumArtist)
                        AnimatedArtOrientation.TALL_3_4 -> fileStore.getTallFile(albumId, album, albumArtist)
                    }
                val tmpFile = File(fileStore.baseDir, "${albumId}_${orientation.name.lowercase()}.mp4.tmp")

                val masterUrlsResult = networkDataSource.fetchMasterUrls(catalogAlbumId, storefront)
                if (masterUrlsResult is AppResult.Success) {
                    val targetUrl =
                        when (orientation) {
                            AnimatedArtOrientation.SQUARE_1_1 -> masterUrlsResult.data.squareMasterUrl
                            AnimatedArtOrientation.TALL_3_4 -> masterUrlsResult.data.tallMasterUrl
                        }
                    val hasSquare = masterUrlsResult.data.squareMasterUrl != null
                    val hasTall = masterUrlsResult.data.tallMasterUrl != null

                    if (targetUrl == null) {
                        val finalEntity =
                            updatingEntity.copy(
                                hasSquare = hasSquare,
                                hasTall = hasTall,
                                status = AnimatedArtStatus.NO_ANIMATION.name,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            )
                        dao.upsert(finalEntity)
                        AppResult.Success(finalEntity.toDomainModel(fileStore))
                    } else {
                        val dlResult = networkDataSource.downloadToFile(targetUrl, destFile, tmpFile)
                        if (dlResult is AppResult.Success) {
                            val finalEntity =
                                updatingEntity.copy(
                                    hasSquare = hasSquare,
                                    hasTall = hasTall,
                                    squareFileName =
                                        if (orientation ==
                                            AnimatedArtOrientation.SQUARE_1_1
                                        ) {
                                            destFile.name
                                        } else {
                                            updatingEntity.squareFileName
                                        },
                                    tallFileName =
                                        if (orientation ==
                                            AnimatedArtOrientation.TALL_3_4
                                        ) {
                                            destFile.name
                                        } else {
                                            updatingEntity.tallFileName
                                        },
                                    status = AnimatedArtStatus.AVAILABLE.name,
                                    updatedAtEpochMs = System.currentTimeMillis(),
                                )
                            dao.upsert(finalEntity)
                            AppResult.Success(finalEntity.toDomainModel(fileStore))
                        } else {
                            val finalEntity =
                                updatingEntity.copy(
                                    status = updatingEntity.savedOrFailed(fileStore),
                                    updatedAtEpochMs = System.currentTimeMillis(),
                                )
                            dao.upsert(finalEntity)
                            AppResult.Error((dlResult as AppResult.Error).error)
                        }
                    }
                } else {
                    val finalEntity =
                        updatingEntity.copy(
                            status = updatingEntity.savedOrFailed(fileStore),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                    dao.upsert(finalEntity)
                    AppResult.Error((masterUrlsResult as AppResult.Error).error)
                }
            }

        override suspend fun deleteArtwork(albumId: Long) =
            withContext(dispatchers.io) {
                lifecycle.withLock {
                    compressionController.cancelAndJoin(albumId)
                    activeJobs
                        .filterKeys { it.first == albumId }
                        .values
                        .toList()
                        .forEach { it.cancelAndJoin() }
                    val entity = dao.getByAlbumId(albumId)
                    val albumTitle = entity?.normalizedAlbum
                    val artist = entity?.normalizedArtist

                    val squareFile = fileStore.getSquareFile(albumId, albumTitle, artist)
                    val tallFile = fileStore.getTallFile(albumId, albumTitle, artist)
                    if (squareFile.exists()) squareFile.delete()
                    if (tallFile.exists()) tallFile.delete()
                    // The files as saved: their names keep the album's capitals, which the normalized names above lose.
                    listOfNotNull(entity?.squareFileName, entity?.tallFileName).forEach { File(fileStore.baseDir, it).delete() }
                    dao.deleteByAlbumId(albumId)
                }
            }

        override suspend fun deleteAllArtwork() =
            withContext(dispatchers.io) {
                lifecycle.withLock {
                    compressionController.cancelAllAndJoin()
                    activeJobs.values.toList().forEach { it.cancelAndJoin() }
                    fileStore.deleteAll()
                    dao.deleteAll()
                }
            }

        override suspend fun totalStorageBytes(): Long =
            withContext(dispatchers.io) {
                fileStore.totalBytes()
            }

        override fun observePreferredOrientation(): Flow<AnimatedArtOrientation> =
            dataStore.data.map { proto ->
                when (proto.animatedArtOrientation) {
                    AnimatedArtOrientationProto.SQUARE_1_1 -> AnimatedArtOrientation.SQUARE_1_1
                    AnimatedArtOrientationProto.TALL_3_4 -> AnimatedArtOrientation.TALL_3_4
                    else -> AnimatedArtOrientation.SQUARE_1_1
                }
            }

        override suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation) {
            dataStore.updateData { proto ->
                proto
                    .toBuilder()
                    .setAnimatedArtOrientation(
                        when (orientation) {
                            AnimatedArtOrientation.SQUARE_1_1 -> AnimatedArtOrientationProto.SQUARE_1_1
                            AnimatedArtOrientation.TALL_3_4 -> AnimatedArtOrientationProto.TALL_3_4
                        },
                    ).build()
            }
        }

        override fun observePreferredResolution(): Flow<String> =
            dataStore.data.map { proto ->
                proto.preferredLiveCoverResolution.ifBlank { "1080p" }
            }

        override suspend fun setPreferredResolution(resolution: String) {
            dataStore.updateData { proto ->
                proto
                    .toBuilder()
                    .setPreferredLiveCoverResolution(resolution)
                    .build()
            }
        }

        override suspend fun cleanupOnStartup() =
            withContext(dispatchers.io) {
                lifecycle.withLock {
                    if (activeJobs.isEmpty() && !compressionController.isAnyRunning()) {
                        dao.resetDownloading()
                        fileStore.cleanTempFiles()
                    }
                }
            }

        private fun parseStatus(status: String): AnimatedArtStatus =
            try {
                AnimatedArtStatus.valueOf(status)
            } catch (e: Exception) {
                AnimatedArtStatus.UNCHECKED
            }

        private fun AnimatedArtworkEntity.toDomainModel(fileStore: ArtworkFileStore): LocalAnimatedArtwork {
            val squarePath =
                if (squareFileName != null) {
                    val file = File(fileStore.baseDir, squareFileName)
                    if (file.exists()) file.absolutePath else null
                } else {
                    null
                }

            val tallPath =
                if (tallFileName != null) {
                    val file = File(fileStore.baseDir, tallFileName)
                    if (file.exists()) file.absolutePath else null
                } else {
                    null
                }

            return LocalAnimatedArtwork(
                albumId = albumId,
                squareFilePath = squarePath,
                tallFilePath = tallPath,
                hasSquare = hasSquare,
                hasTall = hasTall,
                status = parseStatus(status),
                catalogAlbumId = catalogAlbumId,
                storefront = storefront,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }
    }

private fun AnimatedArtworkEntity.savedOrFailed(fileStore: ArtworkFileStore): String =
    if (listOfNotNull(squareFileName, tallFileName).any { File(fileStore.baseDir, it).isFile }) {
        AnimatedArtStatus.AVAILABLE.name
    } else {
        AnimatedArtStatus.FAILED.name
    }
