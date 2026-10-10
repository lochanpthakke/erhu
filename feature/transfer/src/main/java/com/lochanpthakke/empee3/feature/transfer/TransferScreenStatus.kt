package com.lochanpthakke.empee3.feature.transfer

import com.lochanpthakke.empee3.feature.transfer.server.TransferItem
import com.lochanpthakke.empee3.feature.transfer.server.TransferStatus

/** What the Wi-Fi Transfer screen tells the user. */
enum class TransferScreenStatus {
    STOPPED,
    STARTING,
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    WIFI_DISCONNECTED,
    PERMISSION_DENIED,
    FAILED,
}

/** Screen status from the session state and its files. The address is only ever shown while [TransferServerState.Running]. */
fun transferScreenStatus(
    state: TransferServerState,
    items: List<TransferItem>,
): TransferScreenStatus =
    when (state) {
        TransferServerState.Starting -> TransferScreenStatus.STARTING
        is TransferServerState.Running ->
            when {
                items.any { !it.status.isTerminal } -> TransferScreenStatus.IN_PROGRESS
                items.isNotEmpty() -> TransferScreenStatus.COMPLETED
                else -> TransferScreenStatus.WAITING
            }
        is TransferServerState.Stopped ->
            when (state.reason) {
                StopReason.NO_WIFI, StopReason.WIFI_LOST -> TransferScreenStatus.WIFI_DISCONNECTED
                StopReason.PERMISSION_DENIED, StopReason.STORAGE_PERMISSION_DENIED -> TransferScreenStatus.PERMISSION_DENIED
                StopReason.FAILED_TO_START -> TransferScreenStatus.FAILED
                else -> TransferScreenStatus.STOPPED
            }
    }

/** Saved / skipped as duplicates / failed (cancelled files count as not sent). */
data class TransferCounts(
    val saved: Int,
    val duplicates: Int,
    val failed: Int,
)

fun transferCounts(items: List<TransferItem>): TransferCounts =
    TransferCounts(
        saved = items.count { it.status == TransferStatus.COMPLETED },
        duplicates = items.count { it.status == TransferStatus.DUPLICATE },
        failed = items.count { it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED },
    )
