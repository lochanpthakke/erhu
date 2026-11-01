package com.erhu.erhu.core.network.apple

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Apple's motion covers are HLS streams whose timestamps start at 10 s (the first fragment's
 * `tfdt` is 240 at timescale 24). HLS players normalise that; joined into one MP4 file it is not,
 * so a player starts its clock at 0 and shows the first frame only 10 s later. [startAtZero]
 * moves every fragment's decode time back by the first one, in place: only the `tfdt` values
 * change, never the frames or the file length.
 */
object FragmentedMp4Timeline {
    private const val HEADER = 8
    private const val VERSION_FLAGS = 4

    private class DecodeTime(
        val position: Long,
        val is64Bit: Boolean,
        val value: Long,
        val trackId: Int,
    )

    /**
     * Rebases [file] so its first fragment starts at 0. Returns false, writing nothing, when it
     * already does, is not a well-formed fragmented MP4, or has more than one track (moving tracks
     * separately could put them out of sync).
     */
    fun startAtZero(file: File): Boolean =
        RandomAccessFile(file, "rw").use { raf ->
            val times = readDecodeTimes(raf) ?: return false
            if (times.isEmpty() || times.map { it.trackId }.distinct().size != 1) return false
            val first = times.minOf { it.value }
            if (first == 0L) return false
            times.forEach { time ->
                raf.seek(time.position)
                val moved = time.value - first
                if (time.is64Bit) raf.writeLong(moved) else raf.writeInt(moved.toInt())
            }
            true
        }

    /** Every fragment's decode time, in file order (for tests and checks). */
    fun decodeTimes(file: File): List<Long> = RandomAccessFile(file, "r").use { raf -> readDecodeTimes(raf)?.map { it.value }.orEmpty() }

    /** All `moof/traf/tfdt` values, or null if any box runs past its parent or the file. */
    private fun readDecodeTimes(raf: RandomAccessFile): List<DecodeTime>? =
        try {
            val times = mutableListOf<DecodeTime>()
            val complete =
                forEachBox(raf, 0, raf.length()) { type, content, end ->
                    if (type != "moof") return@forEachBox true
                    forEachBox(raf, content, end) { child, trafContent, trafEnd ->
                        child != "traf" || readTraf(raf, trafContent, trafEnd, times)
                    }
                }
            if (complete) times else null
        } catch (_: IOException) {
            null
        }

    private fun readTraf(
        raf: RandomAccessFile,
        content: Long,
        end: Long,
        into: MutableList<DecodeTime>,
    ): Boolean {
        var trackId: Int? = null
        var time: Pair<Long, Boolean>? = null
        val complete =
            forEachBox(raf, content, end) { type, boxContent, boxEnd ->
                when (type) {
                    "tfhd" -> {
                        if (boxEnd - boxContent < VERSION_FLAGS + 4) return@forEachBox false
                        raf.seek(boxContent + VERSION_FLAGS)
                        trackId = raf.readInt()
                    }
                    "tfdt" -> {
                        raf.seek(boxContent)
                        val is64Bit = raf.readUnsignedByte() == 1
                        if (boxEnd - boxContent < VERSION_FLAGS + if (is64Bit) 8 else 4) return@forEachBox false
                        time = boxContent + VERSION_FLAGS to is64Bit
                    }
                }
                true
            }
        val id = trackId
        val found = time
        if (!complete || id == null) return false
        if (found != null) {
            raf.seek(found.first)
            val value = if (found.second) raf.readLong() else raf.readInt().toLong() and 0xFFFFFFFFL
            into += DecodeTime(found.first, found.second, value, id)
        }
        return true
    }

    private class Box(
        val type: String,
        val content: Long,
        val end: Long,
    )

    /** The box at [position], or null if it is malformed or runs past [end]. */
    private fun readBox(
        raf: RandomAccessFile,
        position: Long,
        end: Long,
    ): Box? {
        if (end - position < HEADER) return null
        raf.seek(position)
        var size = raf.readInt().toLong() and 0xFFFFFFFFL
        val typeBytes = ByteArray(4).also { raf.readFully(it) }
        var header = HEADER.toLong()
        if (size == 1L) {
            size = raf.readLong()
            header += 8
        }
        if (size == 0L) size = end - position
        val type = String(typeBytes, Charsets.ISO_8859_1)
        return if (size < header || position + size > end) null else Box(type, position + header, position + size)
    }

    /**
     * Calls [visit] with each box in [start, end) (its type, where its content starts, where it ends)
     * until it returns false; false if a box is malformed.
     */
    private inline fun forEachBox(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        visit: (type: String, content: Long, end: Long) -> Boolean,
    ): Boolean {
        var position = start
        var complete = true
        while (complete && position < end) {
            val box = readBox(raf, position, end)
            complete = box != null && visit(box.type, box.content, box.end)
            position = box?.end ?: end
        }
        return complete
    }
}
