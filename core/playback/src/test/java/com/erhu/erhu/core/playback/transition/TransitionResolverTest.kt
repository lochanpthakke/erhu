package com.erhu.erhu.core.playback.transition

import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.CurveMath
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionResolver
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSource
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionResolverTest {
    private val album = CollectionRef.album(7)
    private val playlist = CollectionRef.playlist(7)
    private val global = TransitionSpec(durationMs = 1_000)
    private val albumSpec = TransitionSpec(durationMs = 4_000, preset = TransitionPreset.BASS_SWAP, bassSwap = true)
    private val pairSpec = TransitionSpec(durationMs = 8_000, curve = TransitionCurve.LINEAR)

    private fun origin(
        collection: CollectionRef,
        song: Long,
        slot: Int? = null,
    ) = QueueOrigin(collection, TrackOccurrence(song, slot))

    private val a1 = origin(album, 1)
    private val a2 = origin(album, 2)
    private val pairKey = TransitionPairKey(album, a1.occurrence, a2.occurrence)

    private fun config(
        master: Boolean = true,
        default: TransitionChoice = TransitionChoice.Custom(global),
        collection: TransitionChoice? = null,
        pair: TransitionChoice? = null,
    ) = TransitionConfig(
        GlobalTransitionSettings(master, default),
        listOfNotNull(collection?.let { album to it }).toMap(),
        listOfNotNull(pair?.let { pairKey to it }).toMap(),
    )

    private fun resolve(
        config: TransitionConfig,
        from: QueueOrigin? = a1,
        to: QueueOrigin? = a2,
    ) = TransitionResolver.resolve(config, from, to)

    @Test
    fun `collection switch pauses exact pair overrides and restores them without affecting other collections`() {
        val original = config(collection = TransitionChoice.Custom(albumSpec), pair = TransitionChoice.Custom(pairSpec))
        assertTrue(original.collectionEnabled(album))
        val disabled = original.withCollectionEnabled(album, false)
        assertFalse(disabled.collectionEnabled(album))
        assertNull(resolve(disabled).spec)
        assertNull(TransitionResolver.collectionDefault(disabled, album))
        assertEquals(global, resolve(disabled, origin(playlist, 1), origin(playlist, 2)).spec)
        assertEquals(original.collections, disabled.collections)
        assertEquals(original.pairs, disabled.pairs)
        val resumed = disabled.withCollectionEnabled(album, true)
        assertEquals(original, resumed)
        assertEquals(pairSpec, resolve(resumed).spec)
    }

    @Test
    fun `enabling unconfigured collection creates fade without changing global settings or explicit pair off`() {
        val original = config(default = TransitionChoice.Off, pair = TransitionChoice.Off)
        assertFalse(original.collectionEnabled(album))
        val enabled = original.withCollectionEnabled(album, true)
        assertTrue(enabled.collectionEnabled(album))
        assertEquals(original.global, enabled.global)
        assertEquals(original.pairs, enabled.pairs)
        assertNull(resolve(enabled).spec) // This exact pair still explicitly opts out.
        assertEquals(TransitionSpec(), resolve(enabled, a2, origin(album, 3)).spec)
        val masterOff = enabled.copy(global = enabled.global.copy(masterEnabled = false))
        assertNull(resolve(masterOff).spec)
        assertTrue(masterOff.collectionEnabled(album)) // Master safety switch stays independent.
    }

    @Test
    fun `master switch off disables everything`() {
        val r = resolve(config(master = false, collection = TransitionChoice.Custom(albumSpec), pair = TransitionChoice.Custom(pairSpec)))
        assertNull(r.spec)
        assertEquals(TransitionSource.MASTER_OFF, r.source)
    }

    @Test
    fun `pair beats collection beats global default`() {
        val all = config(collection = TransitionChoice.Custom(albumSpec), pair = TransitionChoice.Custom(pairSpec))
        assertEquals(pairSpec, resolve(all).spec)
        assertEquals(TransitionSource.PAIR, resolve(all).source)
        assertEquals(albumSpec, resolve(config(collection = TransitionChoice.Custom(albumSpec))).spec)
        assertEquals(global, resolve(config()).spec)
        assertEquals(TransitionSource.GLOBAL_DEFAULT, resolve(config()).source)
    }

    @Test
    fun `an explicit off never falls through to a more general on`() {
        val pairOff = resolve(config(collection = TransitionChoice.Custom(albumSpec), pair = TransitionChoice.Off))
        assertNull(pairOff.spec)
        assertEquals(TransitionSource.PAIR, pairOff.source)
        val albumOff = resolve(config(collection = TransitionChoice.Off))
        assertNull(albumOff.spec)
        assertEquals(TransitionSource.COLLECTION, albumOff.source)
        // An explicit pair transition still works inside a collection that is off.
        assertEquals(pairSpec, resolve(config(collection = TransitionChoice.Off, pair = TransitionChoice.Custom(pairSpec))).spec)
    }

    @Test
    fun `ordinary queues and mixed boundaries use the global default only`() {
        val c = config(collection = TransitionChoice.Custom(albumSpec), pair = TransitionChoice.Custom(pairSpec))
        assertEquals(global, resolve(c, null, null).spec)
        assertEquals(global, resolve(c, a1, null).spec) // into a track added with Play next
        assertEquals(global, resolve(c, a1, origin(playlist, 2)).spec) // album into a playlist
        assertNull(resolve(config(default = TransitionChoice.Off), null, null).spec)
    }

    @Test
    fun `inheriting at every level means the global default, reset is just inherit`() {
        val c = config(collection = TransitionChoice.Inherit, pair = TransitionChoice.Inherit)
        assertEquals(global, resolve(c).spec)
        assertEquals(c.collection(album), TransitionChoice.Inherit)
        assertEquals(global, TransitionResolver.collectionDefault(c, album))
        assertEquals(albumSpec, TransitionResolver.collectionDefault(config(collection = TransitionChoice.Custom(albumSpec)), album))
    }

    @Test
    fun `pairs are ordered and occurrence specific`() {
        val c = config(default = TransitionChoice.Off, pair = TransitionChoice.Custom(pairSpec))
        assertEquals(pairSpec, resolve(c, a1, a2).spec)
        assertNull(resolve(c, a2, a1).spec) // reversed order is another pair
        // A playlist with song 1 twice: positions keep the two copies apart.
        val p1 = origin(playlist, 1, slot = 0)
        val p2 = origin(playlist, 2, slot = 1)
        val p1again = origin(playlist, 1, slot = 5)
        val key = TransitionPairKey(playlist, p1.occurrence, p2.occurrence)
        val pc = TransitionConfig(GlobalTransitionSettings(true, TransitionChoice.Off), pairs = mapOf(key to TransitionChoice.Custom(pairSpec)))
        assertEquals(pairSpec, TransitionResolver.resolve(pc, p1, p2).spec)
        assertNull(TransitionResolver.resolve(pc, p1again, p2).spec)
    }

    @Test
    fun `keys round-trip and reject anything malformed`() {
        val key = TransitionPairKey(playlist, TrackOccurrence(12, 3), TrackOccurrence(12, 9))
        assertEquals(key, TransitionPairKey.fromKey(key.key))
        assertEquals(pairKey, TransitionPairKey.fromKey(pairKey.key))
        listOf(null, "", "album:7", "album:7|s1", "album:7|s1>", "album:7|x1>s2", "artist:1|s1>s2", "album:7|s1@x>s2").forEach {
            assertNull(it, TransitionPairKey.fromKey(it))
        }
        assertEquals(QueueOrigin(playlist, TrackOccurrence(4, 2)), QueueOrigin.fromKey("playlist:7#s4@2", 4))
        // Entries saved by the previous version carry only the collection.
        assertEquals(QueueOrigin(album, TrackOccurrence(9)), QueueOrigin.fromKey("album:7", 9))
        assertNull(QueueOrigin.fromKey("", 9))
    }

    @Test
    fun `linear and equal power curves`() {
        TransitionCurve.entries.filter { it != TransitionCurve.CUSTOM }.forEach { curve ->
            assertEquals(1f, CurveMath.gain(curve, TransitionRole.OUTGOING, 0f), 0f)
            assertEquals(0f, CurveMath.gain(curve, TransitionRole.OUTGOING, 1f), 0f)
            assertEquals(0f, CurveMath.gain(curve, TransitionRole.INCOMING, 0f), 0f)
            assertEquals(1f, CurveMath.gain(curve, TransitionRole.INCOMING, 1f), 0f)
        }
        for (i in 0..10) {
            val t = i / 10f
            val o = CurveMath.gain(TransitionCurve.EQUAL_POWER, TransitionRole.OUTGOING, t)
            val n = CurveMath.gain(TransitionCurve.EQUAL_POWER, TransitionRole.INCOMING, t)
            assertEquals(1f, o * o + n * n, 1e-5f)
            val linearSum =
                CurveMath.gain(TransitionCurve.LINEAR, TransitionRole.OUTGOING, t) +
                    CurveMath.gain(TransitionCurve.LINEAR, TransitionRole.INCOMING, t)
            assertEquals(1f, linearSum, 1e-6f)
        }
    }

    @Test
    fun `custom curves validate times, bounds and fixed end levels`() {
        val out = VolumeCurve.of(0f to 1f, 0.5f to 0.8f, 1f to 0f)
        assertTrue(out.isValid(TransitionRole.OUTGOING))
        assertEquals(0.9f, out.gainAt(0.25f), 1e-6f)
        assertFalse(VolumeCurve.of(0f to 1f, 0.6f to 0.5f, 0.4f to 0.5f, 1f to 0f).isValid(TransitionRole.OUTGOING)) // not ordered
        assertFalse(VolumeCurve.of(0f to 1f, 0.5f to 1.4f, 1f to 0f).isValid(TransitionRole.OUTGOING)) // above full level
        assertFalse(VolumeCurve.of(0f to 0.7f, 1f to 0f).isValid(TransitionRole.OUTGOING)) // would jump at the hand-over
        assertFalse(VolumeCurve.of(0f to 0f, 1f to 0.5f).isValid(TransitionRole.INCOMING)) // would jump after the end
        assertFalse(VolumeCurve.of(0.1f to 0f, 1f to 1f).isValid(TransitionRole.INCOMING)) // outside the window
        assertFalse(VolumeCurve.of(0f to Float.NaN, 1f to 1f).isValid(TransitionRole.INCOMING))
        // An invalid custom spec falls back to the built-in equal-power curve rather than playing garbage.
        val broken =
            TransitionSpec(
                curve = TransitionCurve.CUSTOM,
                outgoingCurve = VolumeCurve.of(0f to 0.2f, 1f to 0f),
                incomingCurve = VolumeCurve.of(0f to 0f, 1f to 1f),
            )
        assertEquals(TransitionCurve.EQUAL_POWER, broken.sanitized().curve)
        assertNull(broken.sanitized().outgoingCurve)
    }

    @Test
    fun `durations are clamped and presets are what they say`() {
        assertEquals(500, TransitionSpec(durationMs = 10).sanitized().durationMs)
        assertEquals(12_000, TransitionSpec(durationMs = 99_000).sanitized().durationMs)
        assertEquals(2_400, TransitionSpec(durationMs = 2_430).sanitized().durationMs)
        assertEquals("0.5 s", TransitionSpec.durationLabel(500))
        val rise = TransitionSpec.preset(TransitionPreset.RISE, TransitionSpec(durationMs = 6_000))
        assertEquals(6_000, rise.durationMs)
        assertTrue(rise.gain(TransitionRole.OUTGOING, 0.5f) > CurveMath.gain(TransitionCurve.LINEAR, TransitionRole.OUTGOING, 0.5f))
        val swap = TransitionSpec.preset(TransitionPreset.BASS_SWAP)
        assertTrue(swap.bassSwap)
        assertEquals(TransitionSpec.BASS_CUT_DB, swap.bassDb(TransitionRole.INCOMING, 0.2f), 0f)
        assertEquals(0f, swap.bassDb(TransitionRole.INCOMING, 0.8f), 0f)
        assertEquals(0f, swap.bassDb(TransitionRole.OUTGOING, 0.2f), 0f)
        assertEquals(TransitionSpec.BASS_CUT_DB, swap.bassDb(TransitionRole.OUTGOING, 0.8f), 0f)
        assertEquals(0f, TransitionSpec().bassDb(TransitionRole.INCOMING, 0.1f), 0f)
    }
}
