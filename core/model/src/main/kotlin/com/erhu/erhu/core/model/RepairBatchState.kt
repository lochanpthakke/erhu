package com.erhu.erhu.core.model

/** One queued track of a metadata repair batch, as saved between sessions. */
data class PersistedRepairItem(
    val songId: Long,
    val status: String,
    val reason: String? = null,
)

/** A metadata repair batch saved so it can be resumed; [pauseReason] is why it stopped early, if it did. */
data class PersistedRepairBatch(
    val items: List<PersistedRepairItem>,
    val pauseReason: String? = null,
)
