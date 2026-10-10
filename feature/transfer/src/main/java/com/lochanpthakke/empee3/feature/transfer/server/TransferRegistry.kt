package com.lochanpthakke.empee3.feature.transfer.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class TransferStatus(
    val isTerminal: Boolean,
) {
    RECEIVING(false),
    FINALIZING(false),
    COMPLETED(true),
    DUPLICATE(true),
    FAILED(true),
    CANCELLED(true),
}

/** One file of the session as the phone sees it. [name] is the name it is (or would be) saved under. */
data class TransferItem(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val receivedBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.RECEIVING,
    /** Why it failed / was skipped, in words the user can act on. */
    val message: String? = null,
)

/**
 * Per-file transfer states of a session. Every change goes through [move], which only allows
 * forward transitions out of non-terminal states: a finished, failed or cancelled file never
 * changes again, so a late callback or a second finalization cannot rewrite its result.
 */
class TransferRegistry {
    private val nextId = AtomicLong(1)
    private val _items = MutableStateFlow<List<TransferItem>>(emptyList())
    val items: StateFlow<List<TransferItem>> = _items.asStateFlow()

    fun begin(
        name: String,
        sizeBytes: Long,
    ): Long {
        val id = nextId.getAndIncrement()
        _items.update { it + TransferItem(id, name, sizeBytes) }
        return id
    }

    fun progress(
        id: Long,
        receivedBytes: Long,
    ) = move(id, setOf(TransferStatus.RECEIVING)) { it.copy(receivedBytes = receivedBytes) }

    fun finalizing(id: Long) =
        move(id, setOf(TransferStatus.RECEIVING)) {
            it.copy(status = TransferStatus.FINALIZING, receivedBytes = it.sizeBytes)
        }

    fun completed(
        id: Long,
        savedName: String,
    ) = move(id, setOf(TransferStatus.FINALIZING)) { it.copy(status = TransferStatus.COMPLETED, name = savedName, message = null) }

    fun duplicate(id: Long) =
        move(id, ACTIVE) { it.copy(status = TransferStatus.DUPLICATE, message = "Already on the phone: skipped, nothing was overwritten") }

    fun failed(
        id: Long,
        message: String,
    ) = move(id, ACTIVE) { it.copy(status = TransferStatus.FAILED, message = message) }

    fun cancelled(id: Long) = move(id, ACTIVE) { it.copy(status = TransferStatus.CANCELLED, message = "Cancelled before it finished") }

    /** Ends every unfinished file (the session is stopping). */
    fun cancelAllActive() {
        _items.value.filter { !it.status.isTerminal }.forEach { cancelled(it.id) }
    }

    fun clear() {
        _items.value = emptyList()
    }

    /** Applies [change] only if the item is currently in one of [from]. Returns whether it applied. */
    private fun move(
        id: Long,
        from: Set<TransferStatus>,
        change: (TransferItem) -> TransferItem,
    ): Boolean {
        var applied = false
        _items.update { list ->
            list.map { item ->
                if (item.id == id && item.status in from) {
                    applied = true
                    change(item)
                } else {
                    item
                }
            }
        }
        return applied
    }

    private companion object {
        val ACTIVE = setOf(TransferStatus.RECEIVING, TransferStatus.FINALIZING)
    }
}
