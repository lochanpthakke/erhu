package com.lochanpthakke.empee3.feature.transfer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.lochanpthakke.empee3.feature.transfer.server.TransferItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class WifiTransferViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val coordinator: WifiTransferCoordinator,
    ) : ViewModel() {
        val state: StateFlow<TransferServerState> = coordinator.state
        val items: StateFlow<List<TransferItem>> = coordinator.items

        fun missingPermissions(): List<String> = coordinator.missingPermissions()

        /** Starts the foreground service (which starts the server), or reports why it cannot. */
        fun start() {
            when {
                coordinator.missingPermissions().isNotEmpty() -> coordinator.reportNotStarted(StopReason.PERMISSION_DENIED)
                !coordinator.hasWifi() -> coordinator.reportNotStarted(StopReason.NO_WIFI)
                else -> WifiTransferService.start(context)
            }
        }

        fun permissionDenied() = coordinator.reportNotStarted(StopReason.PERMISSION_DENIED)

        fun stop() = coordinator.stop(StopReason.USER)
    }
