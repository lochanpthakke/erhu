package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.datastore.repository.vybeEnabledOrDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File

class VybePreferenceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun disabledPreferenceSurvivesClosingAndReopeningDataStore() = runBlocking {
        val file = File(temp.root, "navigation.pb")
        val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val writer = DataStoreFactory.create(serializer = PlaybackStateProtoSerializer, scope = writerScope, produceFile = { file })
        assertTrue(writer.data.first().vybeEnabledOrDefault())
        writer.updateData { it.toBuilder().setVybeEnabled(false).addQueueSongIds(42).setPositionMs(5000).build() }
        writerScope.coroutineContext[Job]!!.cancelAndJoin()
        val readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val reader = DataStoreFactory.create(serializer = PlaybackStateProtoSerializer, scope = readerScope, produceFile = { file })
            assertFalse(reader.data.first().vybeEnabledOrDefault())
            assertEquals(5000L, reader.data.first().positionMs)
            assertEquals(listOf(42L), reader.data.first().queueSongIdsList)
        } finally {
            readerScope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test fun oldInstallationDefaultsToEnabled() {
        val old = PlaybackStateProto.parseFrom(PlaybackStateProto.newBuilder().setPositionMs(70354).build().toByteArray())
        assertFalse(old.hasVybeEnabled())
        assertTrue(old.vybeEnabledOrDefault())
    }

    @Test fun repeatedPreferenceWritesPreservePlaybackAndOtherPreferences() {
        var stored = PlaybackStateProto.newBuilder().addQueueSongIds(8).addQueueSongIds(9).setCurrentIndex(1)
            .setPositionMs(70354).setSidebarGlassyness(0.7f).setPopupGlassOpacity(0.4f).build()
        for (enabled in listOf(false, true, false, false, true)) {
            stored = PlaybackStateProto.parseFrom(stored.toBuilder().setVybeEnabled(enabled).build().toByteArray())
            assertTrue(stored.hasVybeEnabled())
            assertEquals(enabled, stored.vybeEnabled)
            assertEquals(listOf(8L, 9L), stored.queueSongIdsList)
            assertEquals(1, stored.currentIndex)
            assertEquals(70354L, stored.positionMs)
            assertEquals(0.7f, stored.sidebarGlassyness)
            assertEquals(0.4f, stored.popupGlassOpacity)
            stored = stored.toBuilder().setPositionMs(70355).build()
            assertEquals(enabled, stored.vybeEnabled)
            stored = stored.toBuilder().setPositionMs(70354).build()
        }
    }
}
