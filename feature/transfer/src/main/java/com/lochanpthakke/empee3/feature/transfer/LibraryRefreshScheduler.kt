package com.lochanpthakke.empee3.feature.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asks the library for one refresh after a batch of received files, not one per file: each saved
 * file restarts a short quiet period, and the refresh waits while uploads are still running. When
 * the session ends with files pending a refresh, [flush] requests it at once.
 */
class LibraryRefreshScheduler(
    private val scope: CoroutineScope,
    private val quietPeriodMs: Long = DEFAULT_QUIET_PERIOD_MS,
    private val isBusy: () -> Boolean,
    private val refresh: suspend () -> Unit,
) {
    private var pending: Job? = null

    @Synchronized
    fun onFileSaved() {
        pending?.cancel()
        pending =
            scope.launch {
                delay(quietPeriodMs)
                while (isBusy()) delay(quietPeriodMs)
                refresh()
            }
    }

    @Synchronized
    fun flush() {
        val job = pending ?: return
        pending = null
        if (job.isActive) {
            job.cancel()
            scope.launch { refresh() }
        }
    }

    companion object {
        const val DEFAULT_QUIET_PERIOD_MS = 4_000L
    }
}
