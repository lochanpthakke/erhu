package com.erhu.erhu.core.datastore

import com.erhu.erhu.core.datastore.proto.CollectionTransitionProto
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.datastore.proto.TransitionSpecProto
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.PersistedPlaybackState
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeCurve
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TransitionSettingsMapperTest {
    private val albumA = CollectionRef.album(101)
    private val albumB = CollectionRef.album(102)
    private val playlist = CollectionRef.playlist(101)
    private val pair = TransitionPairKey(playlist, TrackOccurrence(5, 0), TrackOccurrence(5, 3))
    private val custom =
        TransitionSpec(
            durationMs = 6_000,
            preset = TransitionPreset.CUSTOM,
            curve = TransitionCurve.CUSTOM,
            outgoingCurve = VolumeCurve.of(0f to 1f, 0.3f to 0.9f, 0.7f to 0.2f, 1f to 0f),
            incomingCurve = VolumeCurve.of(0f to 0.1f, 0.5f to 0.6f, 1f to 1f),
            bassSwap = true,
            bassSwapAt = 0.4f,
            filter = TransitionFilter.HIGH_PASS_IN_LOW_PASS_OUT,
            outgoingCutMs = 12_500,
            incomingStartMs = 3_000,
        )

    private fun roundTrip(state: PlaybackStateProto): PlaybackStateProto {
        // Through the real serializer, as on disk between two app launches.
        val bytes = ByteArrayOutputStream().also { runBlocking { PlaybackStateProtoSerializer.writeTo(state, it) } }.toByteArray()
        return runBlocking { PlaybackStateProtoSerializer.readFrom(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun `every level survives a restart, custom curves included`() {
        var state = PlaybackStateProto.newBuilder().setAppThemeId("ocean").build()
        state = GlobalTransitionSettings(masterEnabled = false, default = TransitionChoice.Custom(TransitionSpec(durationMs = 1_000))).applyTo(state)
        state = TransitionChoice.Custom(TransitionSpec.preset(TransitionPreset.BASS_SWAP)).applyToCollection(albumA, state)
        state = TransitionChoice.Off.applyToCollection(playlist, state)
        state = TransitionChoice.Custom(custom).applyToPair(pair, state)
        val config = roundTrip(state).toTransitionConfig()
        assertEquals(GlobalTransitionSettings(false, TransitionChoice.Custom(TransitionSpec(durationMs = 1_000))), config.global)
        assertEquals(TransitionChoice.Custom(TransitionSpec.preset(TransitionPreset.BASS_SWAP)), config.collection(albumA))
        assertEquals(TransitionChoice.Off, config.collection(playlist)) // explicit off, distinct from inherit
        assertEquals(TransitionChoice.Inherit, config.collection(albumB))
        assertEquals(TransitionChoice.Custom(custom), config.pair(pair))
        assertEquals("ocean", state.appThemeId) // other settings in the shared file are kept
    }

    @Test
    fun `levels are independent and inherit removes the entry`() {
        var state = TransitionChoice.Custom(custom).applyToCollection(albumA, PlaybackStateProto.getDefaultInstance())
        state = TransitionChoice.Custom(TransitionSpec()).applyToCollection(albumB, state)
        state = TransitionChoice.Inherit.applyToCollection(albumB, state)
        val config = state.toTransitionConfig()
        assertEquals(TransitionChoice.Custom(custom), config.collection(albumA))
        assertEquals(TransitionChoice.Inherit, config.collection(albumB))
        assertTrue(albumB.key !in state.collectionTransitionsMap)
        state = TransitionChoice.Off.applyToPair(pair, state)
        state = TransitionChoice.Inherit.applyToPair(pair, state)
        assertTrue(state.pairTransitionsMap.isEmpty())
    }

    @Test
    fun `defaults without any saved setting reproduce the old behaviour`() {
        val config = PlaybackStateProto.getDefaultInstance().toTransitionConfig()
        assertEquals(TransitionConfig(), config)
        assertTrue(config.global.masterEnabled)
        assertEquals(TransitionChoice.Off, config.global.default)
    }

    @Test
    fun `settings saved by the previous version are migrated`() {
        val old =
            PlaybackStateProto
                .newBuilder()
                .putCollectionTransitions(
                    albumA.key,
                    CollectionTransitionProto
                        .newBuilder()
                        .setEnabled(true)
                        .setDurationMs(4_000)
                        .setCurve("LINEAR")
                        .setSchemaVersion(1)
                        .build(),
                )
                .putCollectionTransitions(albumB.key, CollectionTransitionProto.newBuilder().setEnabled(false).setDurationMs(8_000).setSchemaVersion(1).build())
                .build()
        val config = old.toTransitionConfig()
        assertEquals(TransitionChoice.Custom(TransitionSpec(durationMs = 4_000, curve = TransitionCurve.LINEAR)), config.collection(albumA))
        assertEquals(TransitionChoice.Inherit, config.collection(albumB))
    }

    @Test
    fun `unknown keys, names and broken curves fall back safely`() {
        val broken =
            TransitionSpecProto
                .newBuilder()
                .setDurationMs(2_000)
                .setPreset("FILTER_SWEEP")
                .setCurve("CUSTOM")
                .addAllOutT(listOf(0f, 0.6f, 0.4f, 1f))
                .addAllOutGain(listOf(1f, 0.5f, 0.5f, 0f))
                .addAllInT(listOf(0f, 1f))
                .addAllInGain(listOf(0f, 1f))
                .build()
        val state =
            PlaybackStateProto
                .newBuilder()
                .putCollectionTransitions("artist:3", CollectionTransitionProto.newBuilder().setMode("OFF").build())
                .putPairTransitions("garbage", CollectionTransitionProto.newBuilder().setMode("OFF").build())
                .putPairTransitions(pair.key, CollectionTransitionProto.newBuilder().setMode("CUSTOM").setSpec(broken).build())
                .build()
        val config = state.toTransitionConfig()
        assertTrue(config.collections.isEmpty())
        val spec = (config.pair(pair) as TransitionChoice.Custom).spec
        assertEquals(TransitionPreset.FADE, spec.preset)
        assertEquals(TransitionCurve.EQUAL_POWER, spec.curve) // invalid curve rejected
        assertEquals(null, spec.outgoingCurve)
    }

    @Test
    fun `queue entries keep their origin and occurrence across a restart`() {
        val saved =
            PersistedPlaybackState(
                queueSongIds = listOf(5, 5, 9),
                currentIndex = 1,
                positionMs = 10,
                shuffleModeEnabled = true,
                repeatMode = RepeatMode.ALL,
                queueCollectionKeys = listOf("playlist:101#s5@0", "playlist:101#s5@3", ""),
            )
        assertEquals(saved, saved.toProto().toDomain())
    }
}
