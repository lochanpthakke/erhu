package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.database.dao.TrackBookmarkDao
import com.lochanpthakke.empee3.core.database.entity.TrackBookmarkEntity
import com.lochanpthakke.empee3.core.model.TrackBookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Timestamp bookmarks of tracks, persisted in the library database (kept across scans and restarts). */
@Singleton
class TrackBookmarkRepository
    @Inject
    constructor(
        private val dao: TrackBookmarkDao,
        private val timeSource: TimeSource,
    ) {
        fun observeBookmarks(songId: Long): Flow<List<TrackBookmark>> = dao.observeForSong(songId).map { rows -> rows.map { it.toModel() } }

        suspend fun addBookmark(
            songId: Long,
            positionMs: Long,
            label: String = "",
        ): AppResult<Long> =
            appResultOf(mapError = { AppError.DatabaseError("Could not save the bookmark", it) }) {
                dao.insert(
                    TrackBookmarkEntity(
                        songId = songId,
                        positionMs = positionMs.coerceAtLeast(0L),
                        label = label.trim().take(MAX_LABEL_LENGTH),
                        createdAt = timeSource.now(),
                    ),
                )
            }

        suspend fun renameBookmark(
            id: Long,
            label: String,
        ): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not rename the bookmark", it) }) {
                dao.rename(id, label.trim().take(MAX_LABEL_LENGTH))
            }

        suspend fun deleteBookmark(id: Long): AppResult<Unit> =
            appResultOf(mapError = { AppError.DatabaseError("Could not delete the bookmark", it) }) {
                dao.delete(id)
            }

        private fun TrackBookmarkEntity.toModel() =
            TrackBookmark(id = id, songId = songId, positionMs = positionMs, label = label, createdAt = createdAt)

        companion object {
            const val MAX_LABEL_LENGTH = 60
        }
    }
