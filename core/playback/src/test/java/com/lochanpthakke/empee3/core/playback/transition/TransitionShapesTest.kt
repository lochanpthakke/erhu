package com.lochanpthakke.empee3.core.playback.transition

import com.lochanpthakke.empee3.core.model.EqMove
import com.lochanpthakke.empee3.core.model.PassType
import com.lochanpthakke.empee3.core.model.TransitionCurve
import com.lochanpthakke.empee3.core.model.TransitionFilter
import com.lochanpthakke.empee3.core.model.TransitionPreset
import com.lochanpthakke.empee3.core.model.TransitionRole
import com.lochanpthakke.empee3.core.model.TransitionSpec
import com.lochanpthakke.empee3.core.model.VolumeShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionShapesTest {
    private val base = TransitionSpec(durationMs = 4_000)

    @Test
    fun `every volume shape is a valid curve pair and is recognised again`() {
        VolumeShape.entries.forEach { shape ->
            val spec = shape.applyTo(base)
            assertEquals(shape.name, spec, spec.sanitized()) // valid: sanitizing changes nothing
            assertEquals(shape, VolumeShape.of(spec))
            assertEquals(4_000, spec.durationMs)
        }
        val overlap = VolumeShape.OVERLAP.applyTo(base)
        assertEquals(1f, overlap.gain(TransitionRole.OUTGOING, 0.5f), 0f) // both at full level mid-way
        assertEquals(1f, overlap.gain(TransitionRole.INCOMING, 0.5f), 0f)
        val centerCut = VolumeShape.CENTER_CUT.applyTo(base)
        assertEquals(1f, centerCut.gain(TransitionRole.OUTGOING, 0.4f), 0f)
        assertEquals(0f, centerCut.gain(TransitionRole.INCOMING, 0.4f), 0f)
        assertEquals(0f, centerCut.gain(TransitionRole.OUTGOING, 0.6f), 0f)
        assertEquals(1f, VolumeShape.CUT_IN_FADE_OUT.applyTo(base).gain(TransitionRole.INCOMING, 0f), 0f)
    }

    @Test
    fun `eq moves set the swap point and are recognised`() {
        assertNull(EqMove.NONE.swapAt)
        EqMove.entries.forEach { assertEquals(it, EqMove.of(it.applyTo(base))) }
        assertEquals(null, EqMove.of(base.copy(bassSwap = true, bassSwapAt = 0.33f))) // a custom point
        assertEquals(EqMove.NONE, EqMove.of(base))
    }

    @Test
    fun `filter sweeps cover the right track and halves, with transparent ends`() {
        val inOut = TransitionFilter.HIGH_PASS_IN_OUT
        assertTrue(inOut.affects(TransitionRole.INCOMING) && inOut.affects(TransitionRole.OUTGOING))
        assertEquals(TransitionFilter.HIGH_PASS_THIN_HZ, inOut.settingAt(TransitionRole.INCOMING, 0f)!!.hz, 0.5f)
        assertEquals(TransitionFilter.HIGH_PASS_OPEN_HZ, inOut.settingAt(TransitionRole.INCOMING, 0.5f)!!.hz, 0.01f)
        assertEquals(TransitionFilter.HIGH_PASS_OPEN_HZ, inOut.settingAt(TransitionRole.INCOMING, 1f)!!.hz, 0.01f)
        assertEquals(TransitionFilter.HIGH_PASS_OPEN_HZ, inOut.settingAt(TransitionRole.OUTGOING, 0.3f)!!.hz, 0.01f)
        assertEquals(TransitionFilter.HIGH_PASS_THIN_HZ, inOut.settingAt(TransitionRole.OUTGOING, 1f)!!.hz, 0.5f)
        val lowPass = TransitionFilter.HIGH_PASS_IN_LOW_PASS_OUT.settingAt(TransitionRole.OUTGOING, 1f)!!
        assertEquals(PassType.LOW_PASS, lowPass.type)
        assertEquals(TransitionFilter.LOW_PASS_DARK_HZ, lowPass.hz, 0.5f)
        assertNull(TransitionFilter.HIGH_PASS_IN.settingAt(TransitionRole.OUTGOING, 0.9f))
        assertNull(TransitionFilter.NONE.settingAt(TransitionRole.INCOMING, 0.1f))
    }

    @Test
    fun `presets are built only from what the engine plays`() {
        val blend = TransitionSpec.preset(TransitionPreset.BLEND, base)
        assertEquals(VolumeShape.OVERLAP, VolumeShape.of(blend))
        assertEquals(EqMove.CENTER_BASS_SWAP, EqMove.of(blend))
        assertEquals(TransitionFilter.HIGH_PASS_IN_OUT, blend.filter)
        assertEquals(TransitionFilter.LOW_PASS_OUT, TransitionSpec.preset(TransitionPreset.MELT, base).filter)
        assertEquals(VolumeShape.CENTER_CUT, VolumeShape.of(TransitionSpec.preset(TransitionPreset.SLAM, base)))
        assertEquals(TransitionFilter.HIGH_PASS_IN, TransitionSpec.preset(TransitionPreset.RISE, base).filter)
        TransitionPreset.entries.forEach { preset ->
            val spec = TransitionSpec.preset(preset, base)
            assertEquals(preset.name, spec, spec.sanitized())
            assertEquals(4_000, spec.durationMs)
        }
        // A custom preset keeps everything the user had and only makes the curves editable.
        val custom = TransitionSpec.preset(TransitionPreset.CUSTOM, TransitionSpec.preset(TransitionPreset.MELT, base))
        assertEquals(TransitionCurve.CUSTOM, custom.curve)
        assertEquals(TransitionFilter.LOW_PASS_OUT, custom.filter)
    }
}
