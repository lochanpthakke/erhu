package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.datastore.proto.RepairBatchProto
import com.erhu.erhu.core.datastore.proto.RepairItemProto
import com.erhu.erhu.core.model.PersistedRepairBatch
import com.erhu.erhu.core.model.PersistedRepairItem
import kotlinx.coroutines.flow.first

/** The metadata editor's repair queue, kept in the app's settings store so a batch survives the process. */
interface RepairBatchPersistence {
    /** The saved batch, or null when there is none. */
    suspend fun load(): PersistedRepairBatch?

    /** Saves [batch]; an empty batch clears it. */
    suspend fun save(batch: PersistedRepairBatch): AppResult<Unit>
}

class RepairBatchPersistenceImpl(
    private val dataStore: DataStore<PlaybackStateProto>,
) : RepairBatchPersistence {
    override suspend fun load(): PersistedRepairBatch? = dataStore.data.first().toRepairBatch()

    override suspend fun save(batch: PersistedRepairBatch): AppResult<Unit> =
        appResultOf {
            // Merge, don't replace: playback, theme and sound state live in the same proto.
            dataStore.updateData { batch.applyTo(it) }
            Unit
        }
}

fun PlaybackStateProto.toRepairBatch(): PersistedRepairBatch? {
    if (!hasRepairBatch() || repairBatch.itemsCount == 0) return null
    return PersistedRepairBatch(
        items =
            repairBatch.itemsList.map {
                PersistedRepairItem(songId = it.songId, status = it.status, reason = it.reason.takeIf { reason -> reason.isNotEmpty() })
            },
        pauseReason = repairBatch.pauseReason.takeIf { it.isNotEmpty() },
    )
}

fun PersistedRepairBatch.applyTo(state: PlaybackStateProto): PlaybackStateProto {
    if (items.isEmpty()) return state.toBuilder().clearRepairBatch().build()
    val proto =
        RepairBatchProto
            .newBuilder()
            .setPauseReason(pauseReason.orEmpty())
            .addAllItems(
                items.map {
                    RepairItemProto
                        .newBuilder()
                        .setSongId(it.songId)
                        .setStatus(it.status)
                        .setReason(it.reason.orEmpty())
                        .build()
                },
            ).build()
    return state.toBuilder().setRepairBatch(proto).build()
}
