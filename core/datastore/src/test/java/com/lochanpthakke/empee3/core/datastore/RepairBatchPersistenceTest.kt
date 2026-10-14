package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.model.PersistedRepairBatch
import com.lochanpthakke.empee3.core.model.PersistedRepairItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The repair queue survives a restart, alongside (never instead of) the other saved state. */
class RepairBatchPersistenceTest {
    private fun PlaybackStateProto.afterRestart(): PlaybackStateProto = PlaybackStateProto.parseFrom(toByteArray())

    private val batch =
        PersistedRepairBatch(
            items =
                listOf(
                    PersistedRepairItem(1, "DONE"),
                    PersistedRepairItem(2, "NO_MATCH", "No close match"),
                    PersistedRepairItem(3, "NETWORK_FAILED", "MusicBrainz: Couldn't resolve the provider's address"),
                    PersistedRepairItem(4, "PENDING"),
                ),
            pauseReason = "No internet connection",
        )

    @Test
    fun theBatchRoundTrips_andKeepsOtherSettings() {
        val start =
            PlaybackStateProto
                .newBuilder()
                .setAppThemeId("default_dark")
                .setMetadataProvider("MusicBrainz")
                .build()
        val restored = batch.applyTo(start).afterRestart()
        assertEquals(batch, restored.toRepairBatch())
        assertEquals("default_dark", restored.appThemeId)
        assertEquals("MusicBrainz", restored.metadataProvider)
    }

    @Test
    fun anEmptyBatchClearsIt() {
        val saved = batch.applyTo(PlaybackStateProto.getDefaultInstance())
        val cleared = PersistedRepairBatch(emptyList()).applyTo(saved).afterRestart()
        assertFalse(cleared.hasRepairBatch())
        assertNull(cleared.toRepairBatch())
        assertNull(PlaybackStateProto.getDefaultInstance().toRepairBatch())
    }
}
