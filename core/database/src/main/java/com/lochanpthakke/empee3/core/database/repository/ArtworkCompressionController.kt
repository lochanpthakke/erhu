package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.model.ArtworkCompressionState
import com.lochanpthakke.empee3.core.network.apple.ArtworkCompressionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface ArtworkCompressionOperations {
    fun observe(albumId: Long): Flow<ArtworkCompressionState>

    suspend fun prepare(albumId: Long)

    suspend fun start(albumId: Long)

    fun cancel(albumId: Long)
}

/** Shares the repository lifecycle gate with downloads, deletion and startup staging cleanup. */
internal class ArtworkCompressionController(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val lifecycle: Mutex,
    private val hasDownload: (Long) -> Boolean,
    private val source: suspend (Long) -> File?,
    private val engine: ArtworkCompressionEngine,
    private val recoverStaging: () -> Unit = {},
) : ArtworkCompressionOperations {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val states = MutableStateFlow<Map<Long, ArtworkCompressionState>>(emptyMap())

    override fun observe(albumId: Long): Flow<ArtworkCompressionState> =
        states.map { it[albumId] ?: ArtworkCompressionState() }.distinctUntilChanged()

    override suspend fun prepare(albumId: Long) =
        withContext(dispatchers.io) {
            lifecycle.withLock {
                if (isRunning(albumId)) return@withLock
                if (!isAnyRunning()) recoverStaging()
                val file = source(albumId)
                val prior = states.value[albumId]
                // Keep the completed size report while this same output remains saved.
                if (prior?.compressedBytes != null && file?.length() == prior.compressedBytes) return@withLock
                update(albumId, file?.let { engine.offer(it) } ?: ArtworkCompressionState())
            }
        }

    override suspend fun start(albumId: Long) =
        withContext(dispatchers.io) {
            lifecycle.withLock {
                if (isRunning(albumId)) return@withLock
                val prior = states.value[albumId] ?: ArtworkCompressionState()
                if (isAnyRunning() || hasDownload(albumId)) {
                    update(albumId, prior.copy(message = "Another artwork operation is running. Try again when it finishes."))
                    return@withLock
                }
                val file = source(albumId) ?: return@withLock
                val job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            engine.compress(file) { update(albumId, it) }
                        } catch (cancelled: CancellationException) {
                            if (states.value[albumId]?.compressedBytes == null) {
                                update(albumId, prior.copy(message = "Cancelled. Original kept."))
                            }
                            throw cancelled
                        } catch (failure: Exception) {
                            if (states.value[albumId]?.compressedBytes == null) {
                                update(albumId, prior.copy(message = failure.message ?: "Compression failed. Original kept."))
                            }
                        }
                    }
                job.invokeOnCompletion { jobs.remove(albumId, job) }
                jobs[albumId] = job
                update(albumId, prior.copy(available = false, running = true, message = "Checking cover…"))
                job.start()
                Unit
            }
        }

    override fun cancel(albumId: Long) {
        jobs[albumId]?.cancel()
    }

    fun isRunning(albumId: Long): Boolean = jobs.containsKey(albumId)

    fun isAnyRunning(): Boolean = jobs.isNotEmpty()

    suspend fun cancelAndJoin(albumId: Long) {
        jobs[albumId]?.cancelAndJoin()
    }

    suspend fun cancelAllAndJoin() {
        jobs.values.toList().forEach { it.cancelAndJoin() }
    }

    private fun update(
        albumId: Long,
        state: ArtworkCompressionState,
    ) {
        states.update { it + (albumId to state) }
    }
}
