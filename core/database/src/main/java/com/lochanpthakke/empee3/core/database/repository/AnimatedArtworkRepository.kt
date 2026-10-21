package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.model.AnimatedArtOrientation
import com.lochanpthakke.empee3.core.model.LocalAnimatedArtwork
import kotlinx.coroutines.flow.Flow

interface AnimatedArtworkRepository {
    fun observeAnimatedArtwork(albumId: Long): Flow<LocalAnimatedArtwork?>
    suspend fun fetchAndStoreArtwork(
        albumId: Long,
        albumArtist: String,
        album: String,
        trackCount: Int?,
        orientation: AnimatedArtOrientation,
        forceRefresh: Boolean = false
    ): AppResult<LocalAnimatedArtwork>
    suspend fun deleteArtwork(albumId: Long)
    suspend fun deleteAllArtwork()
    suspend fun totalStorageBytes(): Long
    fun observePreferredOrientation(): Flow<AnimatedArtOrientation>
    suspend fun setPreferredOrientation(orientation: AnimatedArtOrientation)
    suspend fun cleanupOnStartup()
}
