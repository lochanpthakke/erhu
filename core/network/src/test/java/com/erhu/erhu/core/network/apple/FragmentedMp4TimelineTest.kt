package com.erhu.erhu.core.network.apple

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

class FragmentedMp4TimelineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun box(
        type: String,
        vararg children: ByteArray,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(payload)
            children.forEach { write(it) }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(8 + body.size)
                writeBytes(type)
                write(body)
            }
        }.toByteArray()
    }

    private fun fullBox(
        type: String,
        version: Int,
        fields: DataOutputStream.() -> Unit,
    ): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(version shl 24)
                fields()
            }
        }.toByteArray()
        return box(type, payload = payload)
    }

    private fun fragment(
        sequence: Int,
        trackId: Int,
        tfdt: Long,
        tfdtVersion: Int = 0,
    ): ByteArray =
        box(
            "moof",
            fullBox("mfhd", 0) { writeInt(sequence) },
            box(
                "traf",
                fullBox("tfhd", 0) { writeInt(trackId) },
                fullBox("tfdt", tfdtVersion) { if (tfdtVersion == 1) writeLong(tfdt) else writeInt(tfdt.toInt()) },
            ),
        ) + box("mdat", payload = ByteArray(32) { it.toByte() })

    /** Apple's covers: one video track whose first fragment starts at 10 s (240 at timescale 24). */
    private fun appleLikeCover(tfdtVersion: Int = 0): File {
        val bytes =
            box("ftyp", payload = "iso6".toByteArray()) +
                box("moov", box("mvhd", payload = ByteArray(16))) +
                fragment(1, 1, 240, tfdtVersion) +
                fragment(2, 1, 308, tfdtVersion) +
                fragment(3, 1, 377, tfdtVersion)
        return folder.newFile().apply { writeBytes(bytes) }
    }

    @Test
    fun `fragments are moved so the first frame is at zero`() {
        val file = appleLikeCover()

        assertTrue(FragmentedMp4Timeline.startAtZero(file))

        assertEquals(listOf(0L, 68L, 137L), FragmentedMp4Timeline.decodeTimes(file))
    }

    @Test
    fun `64-bit decode times are moved too`() {
        val file = appleLikeCover(tfdtVersion = 1)

        assertTrue(FragmentedMp4Timeline.startAtZero(file))

        assertEquals(listOf(0L, 68L, 137L), FragmentedMp4Timeline.decodeTimes(file))
    }

    @Test
    fun `a file already starting at zero is left untouched`() {
        val file = appleLikeCover()
        FragmentedMp4Timeline.startAtZero(file)
        val before = file.readBytes()

        assertFalse(FragmentedMp4Timeline.startAtZero(file))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `only decode times change, never the frames or the file length`() {
        val file = appleLikeCover()
        val before = file.readBytes()

        FragmentedMp4Timeline.startAtZero(file)

        val after = file.readBytes()
        assertEquals(before.size, after.size)
        val changed = before.indices.filter { before[it] != after[it] }
        // 240 -> 0, 308 -> 68, 377 -> 137: only the low byte(s) of each 32-bit tfdt value differ.
        assertTrue(changed.isNotEmpty())
        changed.forEach { index -> assertTrue("byte $index is not in a tfdt", isInsideTfdt(after, index)) }
    }

    @Test
    fun `files with more than one track are left alone`() {
        val bytes =
            box("ftyp", payload = "iso6".toByteArray()) +
                box("moov") +
                fragment(1, 1, 240) +
                fragment(2, 2, 480)
        val file = folder.newFile().apply { writeBytes(bytes) }

        assertFalse(FragmentedMp4Timeline.startAtZero(file))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a truncated or non-mp4 file is left alone`() {
        val file = folder.newFile().apply { writeBytes("not an mp4 at all".toByteArray()) }
        val truncated = appleLikeCover().also { RandomAccessFile(it, "rw").use { raf -> raf.setLength(raf.length() - 10) } }
        val truncatedBytes = truncated.readBytes()

        assertFalse(FragmentedMp4Timeline.startAtZero(file))
        assertFalse(FragmentedMp4Timeline.startAtZero(truncated))
        assertArrayEquals(truncatedBytes, truncated.readBytes())
    }

    private fun isInsideTfdt(
        bytes: ByteArray,
        index: Int,
    ): Boolean {
        val type = "tfdt".toByteArray()
        for (start in 0..bytes.size - 4) {
            if (bytes.copyOfRange(start, start + 4).contentEquals(type)) {
                val boxStart = start - 4
                val size = java.nio.ByteBuffer.wrap(bytes, boxStart, 4).int
                if (index in boxStart until boxStart + size) return true
            }
        }
        return false
    }
}
