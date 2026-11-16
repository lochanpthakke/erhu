package com.erhu.erhu.core.database.repository

import com.erhu.erhu.core.database.dao.CustomSongArtworkDao
import com.erhu.erhu.core.database.entity.CustomSongArtworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

interface CustomSongArtworkRepository {
    suspend fun setCustomArtwork(
        songId: Long,
        inputStream: InputStream,
    ): CustomSongArtworkEntity

    suspend fun removeCustomArtwork(songId: Long)

    fun observeCustomArtwork(songId: Long): Flow<CustomSongArtworkEntity?>

    suspend fun getCustomArtwork(songId: Long): CustomSongArtworkEntity?

    fun observeAllCustomArtwork(): Flow<Map<Long, CustomSongArtworkEntity>>
}

@Singleton
class CustomSongArtworkRepositoryImpl
    @Inject
    constructor(
        private val dao: CustomSongArtworkDao,
        private val store: CustomSongArtworkStore,
        private val timeSource: TimeSource,
    ) : CustomSongArtworkRepository {
        override suspend fun setCustomArtwork(
            songId: Long,
            inputStream: InputStream,
        ): CustomSongArtworkEntity =
            withContext(Dispatchers.IO) {
                val now = timeSource.now()
                val result = store.save(songId, inputStream, now)
                val entity =
                    CustomSongArtworkEntity(
                        songId = songId,
                        filePath = result.file.absolutePath,
                        mimeType = result.mimeType,
                        isAnimated = result.isAnimated,
                        updatedAtEpochMs = now,
                    )
                dao.upsert(entity)
                entity
            }

        override suspend fun removeCustomArtwork(songId: Long) =
            withContext(Dispatchers.IO) {
                val existing = dao.getCustomArtwork(songId)
                if (existing != null) {
                    store.delete(existing.filePath)
                    dao.delete(songId)
                }
                store.deleteExistingForSong(songId)
            }

        override fun observeCustomArtwork(songId: Long): Flow<CustomSongArtworkEntity?> =
            dao.observeCustomArtwork(songId).distinctUntilChanged()

        override suspend fun getCustomArtwork(songId: Long): CustomSongArtworkEntity? =
            dao.getCustomArtwork(songId)

        override fun observeAllCustomArtwork(): Flow<Map<Long, CustomSongArtworkEntity>> =
            dao.observeAllCustomArtwork().map { list -> list.associateBy { it.songId } }.distinctUntilChanged()
    }
