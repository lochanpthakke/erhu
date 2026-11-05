package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStoreFactory
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class OnboardingPreferenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun completionSurvivesClosingAndReopeningTheStore() = runBlocking {
        val file = File(temporaryFolder.root, "introduction.pb")
        val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val writer = DataStoreFactory.create(serializer = PlaybackStateProtoSerializer, scope = writerScope, produceFile = { file })
        assertFalse(writer.data.first().onboardingCompleted)
        writer.updateData { it.toBuilder().setOnboardingCompleted(true).setVybeEnabled(false).addQueueSongIds(42).build() }
        writerScope.coroutineContext[Job]!!.cancelAndJoin()
        val readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val reader = DataStoreFactory.create(serializer = PlaybackStateProtoSerializer, scope = readerScope, produceFile = { file })
            assertTrue(reader.data.first().onboardingCompleted)
            assertFalse(reader.data.first().vybeEnabled)
            assertEquals(listOf(42L), reader.data.first().queueSongIdsList)
        } finally {
            readerScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
    @Test fun anOldInstallationHasNotCompletedIntroduction() {
        assertFalse(PlaybackStateProto.parseFrom(byteArrayOf()).onboardingCompleted)
    }
    @Test fun completionAndPlaybackWritesPreserveEachOtherAndTheVybePreference() {
        val before = PlaybackStateProto.newBuilder().setVybeEnabled(false).addQueueSongIds(42)
            .setPositionMs(70354).setScanLocationConfigured(true).build()
        val completed = PlaybackStateProto.parseFrom(before.toBuilder().setOnboardingCompleted(true).build().toByteArray())
        assertTrue(completed.onboardingCompleted)
        assertFalse(completed.vybeEnabled)
        assertTrue(completed.scanLocationConfigured)
        assertEquals(70354L, completed.positionMs)
        val nextPlayback = completed.toDomain().copy(positionMs = 80000).applyTo(completed)
        assertTrue(nextPlayback.onboardingCompleted)
        assertFalse(nextPlayback.vybeEnabled)
        assertEquals(listOf(42L), nextPlayback.queueSongIdsList)
    }
    @Test fun completionDoesNotRequireMusicPermissionOrAConfiguredScanLocation() {
        val skipped = PlaybackStateProto.newBuilder().setOnboardingCompleted(true).build()
        assertTrue(PlaybackStateProto.parseFrom(skipped.toByteArray()).onboardingCompleted)
        assertFalse(skipped.scanLocationConfigured)
    }
}
