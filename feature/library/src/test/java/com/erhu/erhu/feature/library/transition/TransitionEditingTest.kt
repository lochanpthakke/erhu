package com.erhu.erhu.feature.library.transition

import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.EqMove
import com.erhu.erhu.core.model.GlobalTransitionSettings
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionChoice
import com.erhu.erhu.core.model.TransitionConfig
import com.erhu.erhu.core.model.TransitionCurve
import com.erhu.erhu.core.model.TransitionFilter
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.core.model.TransitionPreset
import com.erhu.erhu.core.model.TransitionRole
import com.erhu.erhu.core.model.TransitionSpec
import com.erhu.erhu.core.model.VolumeShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionEditingTest {
    private val playlist = CollectionRef.playlist(3)

    private fun track(
        song: Long,
        position: Int,
    ) = StudioTrack(
        Song(id = song, title = "t$song", artist = "a", album = "b", durationMs = 200_000, uri = "content://x/$song"),
        TrackOccurrence(song, position),
    )

    private val t1 = track(1, 0)
    private val t2 = track(2, 1)
    private val t3 = track(3, 2)
    private val t1Again = track(1, 3)
    private val fade = TransitionSpec(durationMs = 4_000)
    private val rise = TransitionSpec.preset(TransitionPreset.RISE)

    private fun config(vararg pairs: Pair<TransitionPairKey, TransitionChoice>) =
        TransitionConfig(GlobalTransitionSettings(true, TransitionChoice.Custom(fade)), pairs = pairs.toMap())

    private fun key(
        a: StudioTrack,
        b: StudioTrack,
    ) = TransitionPairKey(playlist, a.occurrence, b.occurrence)

    @Test
    fun `pairs follow the order and settings stay with their exact pair`() {
        val cfg = config(key(t1, t2) to TransitionChoice.Custom(rise), key(t2, t3) to TransitionChoice.Off)
        val pairs = studioPairs(playlist, listOf(t1, t2, t3), cfg)
        assertEquals(listOf(key(t1, t2), key(t2, t3)), pairs.map { it.key })
        assertEquals(rise, pairs[0].resolved)
        assertNull(pairs[1].resolved)
        assertEquals("Rise", pairLabel(pairs[0].choice))
        assertEquals("Off", pairLabel(pairs[1].choice))

        // Reordered: 3, 1, 2 — the 1→2 setting is still on 1→2; the new 3→1 pair inherits.
        val reordered = studioPairs(playlist, listOf(t3, t1, t2), cfg)
        assertEquals("Auto", pairLabel(reordered[0].choice))
        assertEquals(fade, reordered[0].resolved)
        assertEquals(TransitionChoice.Custom(rise), reordered[1].choice)

        // Removing track 2: 1→3 is a new pair (inherits); nothing is carried over by position.
        val removed = studioPairs(playlist, listOf(t1, t3), cfg)
        assertEquals(TransitionChoice.Inherit, removed.single().choice)

        // Inserting a track between 1 and 2: both new pairs inherit, the old 1→2 setting waits until they are adjacent again.
        val inserted = studioPairs(playlist, listOf(t1, t3, t2), cfg)
        assertTrue(inserted.all { it.choice == TransitionChoice.Inherit })
        assertTrue(studioPairs(playlist, emptyList(), cfg).isEmpty())
        assertTrue(studioPairs(playlist, listOf(t1), cfg).isEmpty())
    }

    @Test
    fun `duplicate occurrences of one song keep separate transitions`() {
        val cfg = config(key(t1, t2) to TransitionChoice.Custom(rise))
        val pairs = studioPairs(playlist, listOf(t1, t2, t1Again, t2), cfg)
        assertEquals(TransitionChoice.Custom(rise), pairs[0].choice)
        assertEquals(TransitionChoice.Inherit, pairs[2].choice) // the second copy of song 1 is another occurrence
        assertEquals(3, pairs.map { it.key }.distinct().size) // 1→2, 2→1(second copy), 1(second copy)→2: three different pairs
    }

    @Test
    fun `dragging respects time order, bounds and fixed end levels`() {
        var spec = TransitionEditing.setCurve(fade, TransitionCurve.CUSTOM)
        val points = TransitionEditing.curveOf(spec, TransitionRole.OUTGOING).points
        assertEquals(TransitionCurve.CUSTOM, spec.curve)
        // Interior point pushed past its neighbour and above full level: clamped.
        spec = TransitionEditing.movePoint(spec, TransitionRole.OUTGOING, 1, t = 0.9f, gain = 3f)
        val moved = TransitionEditing.curveOf(spec, TransitionRole.OUTGOING).points
        assertTrue(moved[1].t < moved[2].t)
        assertEquals(1f, moved[1].gain, 0f)
        // End points keep their time; the outgoing start/end and the incoming end keep their level.
        spec = TransitionEditing.movePoint(spec, TransitionRole.OUTGOING, 0, t = 0.4f, gain = 0.2f)
        assertEquals(0f, TransitionEditing.curveOf(spec, TransitionRole.OUTGOING).points[0].t, 0f)
        assertEquals(1f, TransitionEditing.curveOf(spec, TransitionRole.OUTGOING).points[0].gain, 0f)
        val incomingBefore = TransitionEditing.curveOf(spec, TransitionRole.INCOMING)
        spec = TransitionEditing.movePoint(spec, TransitionRole.INCOMING, 0, t = 0f, gain = 0.3f) // the incoming start may move
        assertEquals(0.3f, TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points[0].gain, 0f)
        assertEquals(incomingBefore.points.size, TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points.size)
        // Editing one curve never changed the other.
        assertEquals(moved.drop(1), TransitionEditing.curveOf(spec, TransitionRole.OUTGOING).points.drop(1))
        assertEquals(TransitionPreset.CUSTOM, spec.preset)
        assertTrue(spec == spec.sanitized())
    }

    @Test
    fun `points can be added up to the limit and interior points removed`() {
        var spec = TransitionEditing.setCurve(fade, TransitionCurve.CUSTOM)
        val before = TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points.size
        spec = TransitionEditing.addPoint(spec, TransitionRole.INCOMING, 0.33f)
        assertEquals(before + 1, TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points.size)
        repeat(10) { spec = TransitionEditing.addPoint(spec, TransitionRole.INCOMING, 0.05f + it * 0.09f) }
        assertEquals(8, TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points.size)
        assertEquals(spec, TransitionEditing.removePoint(spec, TransitionRole.INCOMING, 0)) // end points stay
        spec = TransitionEditing.removePoint(spec, TransitionRole.INCOMING, 1)
        assertEquals(7, TransitionEditing.curveOf(spec, TransitionRole.INCOMING).points.size)
    }

    @Test
    fun `presets reset curves and keep the duration`() {
        val edited = TransitionEditing.movePoint(TransitionEditing.setCurve(fade, TransitionCurve.CUSTOM), TransitionRole.OUTGOING, 1, 0.2f, 0.4f)
        val reset = TransitionEditing.applyPreset(edited, TransitionPreset.FADE)
        assertEquals(4_000, reset.durationMs)
        assertNull(reset.outgoingCurve)
        assertEquals(TransitionPreset.FADE, reset.preset)
        assertTrue(TransitionEditing.applyPreset(reset, TransitionPreset.BASS_SWAP).bassSwap)
        assertEquals(TransitionPreset.FADE, TransitionEditing.setBassSwap(TransitionSpec.preset(TransitionPreset.BASS_SWAP), false).preset)
        assertEquals(12_000, TransitionEditing.setDuration(fade, 40_000).durationMs)
        assertEquals(0.9f, TransitionEditing.setBassSwapAt(fade, 2f).bassSwapAt, 0f)
    }

    @Test
    fun `named options relabel the preset honestly`() {
        val blend = TransitionSpec.preset(TransitionPreset.BLEND, fade)
        assertEquals(TransitionPreset.BLEND, TransitionEditing.relabel(blend).preset)
        // Changing any part of a preset makes it Custom; changing it back restores the preset name.
        val changed = TransitionEditing.setFilter(blend, TransitionFilter.NONE)
        assertEquals(TransitionPreset.CUSTOM, changed.preset)
        assertEquals(TransitionPreset.BLEND, TransitionEditing.setFilter(changed, TransitionFilter.HIGH_PASS_IN_OUT).preset)
        val swap = TransitionEditing.setEqMove(fade, EqMove.CENTER_BASS_SWAP)
        assertEquals(TransitionPreset.BASS_SWAP, swap.preset)
        assertEquals(TransitionPreset.FADE, TransitionEditing.setEqMove(swap, EqMove.NONE).preset)
        val slam = TransitionEditing.setVolumeShape(fade, VolumeShape.CENTER_CUT)
        assertEquals(TransitionPreset.SLAM, slam.preset)
        assertEquals(VolumeShape.CENTER_CUT, VolumeShape.of(slam))
        // A custom swap point is kept and shown as custom.
        val custom = TransitionEditing.setBassSwapAt(fade, 0.3f)
        assertTrue(custom.bassSwap)
        assertEquals(0.3f, custom.bassSwapAt, 0f)
        assertNull(EqMove.of(custom))
    }

    @Test
    fun `sliding the waveforms moves the cue points within the tracks`() {
        // Outgoing lane slid right by 5 s: it hands over 5 s before its end.
        var spec = TransitionEditing.shiftCue(fade, TransitionRole.OUTGOING, 5_000f, 200_000, 180_000)
        assertEquals(5_000, spec.outgoingCutMs)
        // Incoming lane slid left by 8 s: it starts 8 s in; sliding back past 0 stops at the start.
        spec = TransitionEditing.shiftCue(spec, TransitionRole.INCOMING, -8_000f, 200_000, 180_000)
        assertEquals(8_000, spec.incomingStartMs)
        assertEquals(0, TransitionEditing.shiftCue(spec, TransitionRole.INCOMING, 60_000f, 200_000, 180_000).incomingStartMs)
        assertEquals(0, TransitionEditing.shiftCue(spec, TransitionRole.OUTGOING, -60_000f, 200_000, 180_000).outgoingCutMs)
        // At least a second of each track always plays.
        assertEquals(179_000, TransitionEditing.shiftCue(spec, TransitionRole.INCOMING, -999_999f, 200_000, 180_000).incomingStartMs)
        // Presets keep the cue points: they belong to the pair.
        val blend = TransitionEditing.applyPreset(spec, TransitionPreset.BLEND)
        assertEquals(5_000, blend.outgoingCutMs)
        assertEquals(8_000, blend.incomingStartMs)
        assertEquals(TransitionPreset.BLEND, blend.preset)
    }
}
