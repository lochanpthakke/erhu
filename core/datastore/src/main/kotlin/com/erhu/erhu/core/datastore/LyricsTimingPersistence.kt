package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.datastore.proto.LyricsTimingAdjustmentProto
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.LyricsTimingAdjustment
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.LyricsTimingOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface LyricsTimingPersistence {
    fun observe(): Flow<Map<String, LyricsTimingAdjustment>>

    suspend fun save(
        context: LyricsTimingContext,
        offsetMs: Long,
    ): AppResult<Unit>
}

class LyricsTimingPersistenceImpl(
    private val store: DataStore<PlaybackStateProto>,
    private val now: () -> Long = System::currentTimeMillis,
) : LyricsTimingPersistence {
    override fun observe(): Flow<Map<String, LyricsTimingAdjustment>> =
        store.data
            .map { it.lyricsTimingMap }
            .distinctUntilChanged()
            .map { records ->
                records
                    .mapNotNull { (key, item) ->
                        val adjustment =
                            LyricsTimingAdjustment(
                                item.songKey,
                                item.sourceKey,
                                item.label,
                                LyricsTimingOffset.clamp(item.offsetMs),
                                item.savedAtMs,
                            )
                        if (item.songKey.isBlank() || item.sourceKey.isBlank() || key != adjustment.id) null else key to adjustment
                    }.toMap()
            }.distinctUntilChanged()

    override suspend fun save(
        context: LyricsTimingContext,
        offsetMs: Long,
    ): AppResult<Unit> =
        appResultOf(
            mapError = { AppError.IoError("Couldn't save lyrics timing. Check available storage and try again.", it) },
        ) {
            require(context.songKey.isNotBlank() && context.sourceKey.isNotBlank())
            val adjustment =
                LyricsTimingAdjustmentProto
                    .newBuilder()
                    .setSongKey(context.songKey)
                    .setSourceKey(context.sourceKey)
                    .setLabel(context.label)
                    .setOffsetMs(LyricsTimingOffset.clamp(offsetMs))
                    .setSavedAtMs(now())
                    .build()
            // Merge only this entry; playback, glass, sound, transitions and all other timing records survive.
            store.updateData { it.toBuilder().putLyricsTiming(context.id, adjustment).build() }
            Unit
        }
}
