package com.lochanpthakke.empee3.feature.transfer

import com.lochanpthakke.empee3.feature.transfer.server.TransferItem
import com.lochanpthakke.empee3.feature.transfer.server.TransferStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransferFlowTest {
    @Test
    fun `the library is refreshed once per batch, after uploads finish`() =
        runTest {
            var busy = false
            var refreshes = 0
            val scheduler = LibraryRefreshScheduler(this, quietPeriodMs = 1_000, isBusy = { busy }) { refreshes++ }

            // Three files saved in quick succession: one refresh, after the quiet period.
            scheduler.onFileSaved()
            advanceTimeBy(500)
            scheduler.onFileSaved()
            advanceTimeBy(500)
            scheduler.onFileSaved()
            advanceTimeBy(999)
            assertEquals(0, refreshes)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, refreshes)

            // While another upload is still running, the refresh waits for it.
            busy = true
            scheduler.onFileSaved()
            advanceTimeBy(5_000)
            assertEquals(1, refreshes)
            busy = false
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(2, refreshes)

            // Stopping the session with a refresh pending asks for it at once; nothing pending: nothing.
            scheduler.onFileSaved()
            scheduler.flush()
            runCurrent()
            assertEquals(3, refreshes)
            scheduler.flush()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(3, refreshes)
        }

    @Test
    fun `no saved file, no refresh`() =
        runTest {
            var refreshes = 0
            LibraryRefreshScheduler(this, isBusy = { false }) { refreshes++ }.flush()
            advanceTimeBy(60_000)
            assertEquals(0, refreshes)
        }

    private fun item(
        status: TransferStatus,
        id: Long = 1,
    ) = TransferItem(id, "a.mp3", 10, status = status)

    @Test
    fun `screen status follows the session and its files`() {
        val running = TransferServerState.Running("http://192.168.1.5:8770", "http://192.168.1.5:8770/#t=x", "123456")
        assertEquals(TransferScreenStatus.STOPPED, transferScreenStatus(TransferServerState.Stopped(), emptyList()))
        assertEquals(TransferScreenStatus.STARTING, transferScreenStatus(TransferServerState.Starting, emptyList()))
        assertEquals(TransferScreenStatus.WAITING, transferScreenStatus(running, emptyList()))
        val receiving = listOf(item(TransferStatus.COMPLETED), item(TransferStatus.RECEIVING, 2))
        assertEquals(TransferScreenStatus.IN_PROGRESS, transferScreenStatus(running, receiving))
        val finished = listOf(item(TransferStatus.COMPLETED), item(TransferStatus.FAILED, 2))
        assertEquals(TransferScreenStatus.COMPLETED, transferScreenStatus(running, finished))
        assertEquals(TransferScreenStatus.WIFI_DISCONNECTED, transferScreenStatus(TransferServerState.Stopped(StopReason.WIFI_LOST), emptyList()))
        assertEquals(TransferScreenStatus.WIFI_DISCONNECTED, transferScreenStatus(TransferServerState.Stopped(StopReason.NO_WIFI), emptyList()))
        assertEquals(TransferScreenStatus.PERMISSION_DENIED, transferScreenStatus(TransferServerState.Stopped(StopReason.PERMISSION_DENIED), emptyList()))
        assertEquals(TransferScreenStatus.FAILED, transferScreenStatus(TransferServerState.Stopped(StopReason.FAILED_TO_START), emptyList()))
        val idle = TransferServerState.Stopped(StopReason.IDLE_TIMEOUT)
        assertEquals(TransferScreenStatus.STOPPED, transferScreenStatus(idle, listOf(item(TransferStatus.COMPLETED))))

        val counts =
            transferCounts(
                listOf(item(TransferStatus.COMPLETED), item(TransferStatus.DUPLICATE, 2), item(TransferStatus.FAILED, 3), item(TransferStatus.CANCELLED, 4)),
            )
        assertEquals(TransferCounts(saved = 1, duplicates = 1, failed = 2), counts)
    }
}
