package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.model.ArtworkCompressionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

interface ArtworkCompressionEngine {
    suspend fun offer(source: File): ArtworkCompressionState

    suspend fun compress(
        source: File,
        state: (ArtworkCompressionState) -> Unit,
    )
}

class ArtworkCompressor
    @Inject
    constructor(
        private val dispatchers: DispatcherProvider,
    ) : ArtworkCompressionEngine {
        override suspend fun offer(source: File): ArtworkCompressionState =
            withContext(dispatchers.io) {
                try {
                    val input = ArtworkHardwareEncoder.inspect(source)
                    ArtworkCompressionState(available = true, originalBytes = input.bytes)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ArtworkCompressionState()
                }
            }

        override suspend fun compress(
            source: File,
            state: (ArtworkCompressionState) -> Unit,
        ): Unit =
            withContext(dispatchers.io) {
                if (!encoder.tryLock()) throw IOException("Another cover is being compressed. Try again when it finishes.")
                try {
                    val context = currentCoroutineContext()
                    val input = ArtworkHardwareEncoder.inspect(source)
                    val current = ArtworkCompressionState(running = true, originalBytes = input.bytes, message = "Compressing…")
                    state(current)
                    ArtworkCompressionTransaction().run(
                        source = source,
                        encode = { staged ->
                            ArtworkHardwareEncoder.encode(source, staged, input, { context.ensureActive() }) { progress ->
                                state(current.copy(progress = progress))
                            }
                        },
                        validate = { staged ->
                            state(current.copy(progress = 85, message = "Checking video…"))
                            ArtworkHardwareEncoder.validateEncoded(staged, input)
                            val decoded = ArtworkVideoValidator.validate(staged) { context.ensureActive() }
                            if (decoded.frames != input.frames || decoded.width != 1080 || decoded.height != 1080) {
                                throw IOException("Encoded video did not fully decode as expected. Original kept.")
                            }
                            state(current.copy(progress = 99, message = "Finishing…"))
                        },
                        committed = { result ->
                            // Synchronous commit notification survives cancellation at the caller's return boundary.
                            state(
                                ArtworkCompressionState(
                                    progress = 100,
                                    message = "Compressed on this phone",
                                    originalBytes = result.originalBytes,
                                    compressedBytes = result.compressedBytes,
                                ),
                            )
                        },
                    )
                    Unit
                } finally {
                    encoder.unlock()
                }
            }

        private companion object {
            val encoder = Mutex()
        }
    }
