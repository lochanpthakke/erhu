package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.LyricsTimingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.BufferedSource
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.File
import kotlinx.coroutines.Job

class LyricsTimingPersistenceTest {
    // Android's File.renameTo replaces existing files; Windows does not. Exercise real DataStore
    // persistence with its portable Okio adapter while retaining the app's exact proto serializer.
    private fun diskStore(file: File, job: Job): DataStore<PlaybackStateProto> = DataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, object : OkioSerializer<PlaybackStateProto> {
            override val defaultValue = PlaybackStateProtoSerializer.defaultValue
            override suspend fun readFrom(source: BufferedSource): PlaybackStateProto = PlaybackStateProtoSerializer.readFrom(source.inputStream())
            override suspend fun writeTo(t: PlaybackStateProto, sink: BufferedSink) = PlaybackStateProtoSerializer.writeTo(t, sink.outputStream())
        }, producePath = { file.absolutePath.toPath() }),
        scope = CoroutineScope(job + Dispatchers.IO),
    )
    @get:Rule val folder = TemporaryFolder()
    private val first = LyricsTimingContext("song-a", "source-a", "LRCLIB")
    private val second = first.copy(sourceKey = "source-b")

    @Test fun actualDataStoreRestartRetainsSeparateVersionsAndOtherSettings() = runBlocking {
        val file = folder.root.resolve("timing.pb")
        val job = SupervisorJob()
        val store = diskStore(file, job)
        store.updateData { it.toBuilder().addQueueSongIds(42).setPositionMs(5000).setAppThemeId("dark").setVybeEnabled(false).setSidebarGlassyness(0.75f).build() }
        val timing = LyricsTimingPersistenceImpl(store) { 100 }
        assertTrue(timing.observe().first().isEmpty())
        val result = timing.save(first, 1250)
        if (result is AppResult.Error) throw result.error.cause ?: AssertionError(result.error.message)
        assertTrue(result is AppResult.Success)
        assertTrue(timing.save(second, -500) is AppResult.Success)
        job.cancelAndJoin()
        val restartedJob = SupervisorJob()
        try {
            val restarted = diskStore(file, restartedJob)
            val saved = LyricsTimingPersistenceImpl(restarted)
            assertEquals(1250L, saved.observe().first()[first.id]!!.offsetMs)
            assertEquals(-500L, saved.observe().first()[second.id]!!.offsetMs)
            saved.save(second, 0)
            val state = restarted.data.first()
            assertEquals(listOf(42L), state.queueSongIdsList)
            assertEquals(5000L, state.positionMs)
            assertEquals("dark", state.appThemeId)
            assertFalse(state.vybeEnabled)
            assertEquals(0.75f, state.sidebarGlassyness)
            assertEquals(0L, saved.observe().first()[second.id]!!.offsetMs)
            assertEquals(1250L, saved.observe().first()[first.id]!!.offsetMs)
        } finally { restartedJob.cancelAndJoin() }
    }

    @Test fun failedSaveDoesNotPublishSuccessOrAlterExistingAdjustments() = runBlocking {
        val state = MutableStateFlow(PlaybackStateProto.getDefaultInstance())
        var fail = false
        val store = object : DataStore<PlaybackStateProto> {
            override val data: Flow<PlaybackStateProto> = state
            override suspend fun updateData(transform: suspend (PlaybackStateProto) -> PlaybackStateProto): PlaybackStateProto {
                val next = transform(state.value)
                if (fail) throw IOException("Disk full")
                state.value = PlaybackStateProto.parseFrom(next.toByteArray())
                return state.value
            }
        }
        val timing = LyricsTimingPersistenceImpl(store)
        timing.save(first, 90000)
        assertEquals(30000L, timing.observe().first()[first.id]!!.offsetMs)
        fail = true
        assertTrue(timing.save(first, -1000) is AppResult.Error)
        assertEquals(30000L, timing.observe().first()[first.id]!!.offsetMs)
        assertEquals(1, timing.observe().first().size)
    }
}
