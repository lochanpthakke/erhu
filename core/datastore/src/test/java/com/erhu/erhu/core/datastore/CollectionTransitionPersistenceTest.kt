package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionSpec
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

class CollectionTransitionPersistenceTest {
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

    @Test fun switchPersistsAcrossRestartAndResumesSavedPairWithoutChangingPlaybackOrLyrics() = runBlocking {
        val file=folder.root.resolve("collections.pb")
        val job=SupervisorJob()
        val collection=CollectionRef.playlist(7)
        val pair=TransitionPairKey(collection,TrackOccurrence(1,0),TrackOccurrence(2,4))
        val choice=TransitionChoice.Custom(TransitionSpec(durationMs=4500))
        val timing=LyricsTimingContext("song-a","source-a","File lyrics")
        val store=diskStore(file,job)
        store.updateData { it.toBuilder().addQueueSongIds(1).setPositionMs(12345).build() }
        val settings=TransitionSettingsPersistenceImpl(store)
        assertTrue(settings.saveCollectionEnabled(collection,true) is AppResult.Success)
        assertTrue(settings.savePair(pair,choice) is AppResult.Success)
        assertTrue(LyricsTimingPersistenceImpl(store).save(timing,750) is AppResult.Success)
        assertTrue(settings.saveCollectionEnabled(collection,false) is AppResult.Success)
        val original=settings.observe().first()
        job.cancelAndJoin()
        val restartedJob=SupervisorJob()
        try {
            val restarted=diskStore(file,restartedJob)
            val saved=TransitionSettingsPersistenceImpl(restarted)
            assertFalse(saved.observe().first().collectionEnabled(collection))
            assertEquals(choice,saved.observe().first().pair(pair))
            assertTrue(saved.saveCollectionEnabled(collection,true) is AppResult.Success)
            val resumed=saved.observe().first()
            assertTrue(resumed.collectionEnabled(collection))
            assertEquals(original.collections,resumed.collections)
            assertEquals(original.pairs,resumed.pairs)
            assertEquals(750L,LyricsTimingPersistenceImpl(restarted).observe().first()[timing.id]!!.offsetMs)
            assertEquals(12345L,restarted.data.first().positionMs)
            assertEquals(listOf(1L),restarted.data.first().queueSongIdsList)
        } finally { restartedJob.cancelAndJoin() }
    }
}
