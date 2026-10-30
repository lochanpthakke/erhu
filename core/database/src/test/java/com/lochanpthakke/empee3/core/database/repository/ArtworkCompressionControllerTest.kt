package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.model.ArtworkCompressionState
import com.lochanpthakke.empee3.core.network.apple.ArtworkCompressionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ArtworkCompressionControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val lifecycle = Mutex()
    private var downloading = false
    private var recoveries = 0
    private val engine = FakeEngine()
    private val dispatchers =
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }

    private fun controller() =
        ArtworkCompressionController(
            scope,
            dispatchers,
            lifecycle,
            { downloading },
            { folder.newFile("cover-$it.mp4") },
            engine,
            { recoveries++ },
        )

    @After fun close() = scope.cancel()

    @Test fun `preparing an offer never starts an encoder`() =
        runBlocking {
            val controller = controller()
            controller.prepare(1)
            assertTrue(controller.observe(1).first().available)
            assertEquals(0, engine.encodes)
        }

    @Test fun `orphan recovery waits until every active compression job has stopped`() =
        runBlocking {
            val controller = controller()
            controller.start(1)
            controller.prepare(2)
            assertEquals(0, recoveries)
            controller.cancelAllAndJoin()
            controller.prepare(3)
            assertEquals(1, recoveries)
        }

    @Test fun `repeated actions start only one operation and a second album is not queued`() =
        runBlocking {
            val controller = controller()
            controller.start(1)
            controller.start(1)
            controller.start(2)
            assertEquals(1, engine.encodes)
            assertTrue(controller.isRunning(1))
            assertFalse(controller.isRunning(2))
            engine.gate.complete(Unit)
            assertEquals(100, controller.observe(1).first().progress)
            assertFalse(controller.isAnyRunning())
        }

    @Test fun `a concurrent download prevents compression`() =
        runBlocking {
            val controller = controller()
            downloading = true
            controller.start(1)
            assertEquals(0, engine.encodes)
            assertTrue(
                controller
                    .observe(1)
                    .first()
                    .message
                    .contains("Another artwork operation"),
            )
        }

    @Test fun `registration waits for the same lifecycle gate as deletion`() =
        runBlocking {
            val controller = controller()
            lifecycle.lock()
            val start = launch(Dispatchers.Unconfined) { controller.start(1) }
            assertEquals(0, engine.encodes)
            lifecycle.unlock()
            start.join()
            assertEquals(1, engine.encodes)
            controller.cancelAndJoin(1)
            assertFalse(controller.isRunning(1))
        }

    @Test fun `cancel and join drains active compression before deletion or cleanup`() =
        runBlocking {
            val controller = controller()
            controller.start(1)
            controller.cancelAllAndJoin()
            assertFalse(controller.isAnyRunning())
            assertTrue(
                controller
                    .observe(1)
                    .first()
                    .message
                    .contains("Cancelled"),
            )
        }

    @Test fun `encoding error leaves no active operation`() =
        runBlocking {
            val controller = controller()
            engine.fail = true
            controller.start(1)
            assertFalse(controller.isAnyRunning())
            assertEquals("Encoder failed. Original kept.", controller.observe(1).first().message)
        }

    @Test fun `commit is not reported as cancelled at the return boundary`() =
        runBlocking {
            val controller = controller()
            engine.cancelAfterCommit = true
            controller.start(1)
            engine.gate.complete(Unit)
            assertEquals(50L, controller.observe(1).first().compressedBytes)
            assertEquals(100, controller.observe(1).first().progress)
        }

    private class FakeEngine : ArtworkCompressionEngine {
        var encodes = 0
        var fail = false
        var cancelAfterCommit = false
        val gate = CompletableDeferred<Unit>()

        override suspend fun offer(source: File) = ArtworkCompressionState(available = true, originalBytes = 100)

        override suspend fun compress(
            source: File,
            state: (ArtworkCompressionState) -> Unit,
        ) {
            encodes++
            if (fail) throw IOException("Encoder failed. Original kept.")
            state(ArtworkCompressionState(running = true, originalBytes = 100))
            gate.await()
            state(ArtworkCompressionState(progress = 100, originalBytes = 100, compressedBytes = 50))
            if (cancelAfterCommit) throw CancellationException()
        }
    }
}
