package com.erhu.erhu.core.vybe

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt

/** One song's vector and the hash of the description it was embedded from. */
class IndexedSong(
    val songId: Long,
    val textHash: Long,
    val vector: FloatArray,
)

/**
 * VYBE's on-disk vector cache: one record per embedded song description, appended as indexing
 * goes (so an interrupted run keeps what it finished), the last record of a song winning.
 *
 * - Only derived data is stored: song id, a hash of the embedded text, the vector. No text.
 * - A file written for another model or vector size is ignored and replaced ([compatibleWith]).
 * - Ranking streams the file: vectors are never all held in memory, so a large library is fine.
 * - Vectors are stored as int8 with one float scale each (format 2): a quarter of float32's size.
 *   A format-1 (float32) file of the same model is converted in place on first use, so no song
 *   has to be embedded again.
 * - [compact] rewrites it with live records only (removed songs, superseded vectors dropped).
 *
 * Not thread-safe: one owner (the indexer) serialises access.
 */
class VybeIndexFile(
    private val file: File,
    private val modelId: String,
    private val dimension: Int,
) {
    init {
        require(dimension > 0) { "dimension must be positive" }
    }

    private val recordBytes = Long.SIZE_BYTES * 2 + vectorBytes(FORMAT_VERSION, dimension)

    /** Set once a format-1 file has been looked for (and converted). */
    private var upgradeChecked = false

    /** Live entries: song id -> hash of its embedded text. Empty when the file is missing or incompatible. */
    fun entries(): Map<Long, Long> {
        val live = LinkedHashMap<Long, Long>()
        forEachRecord(withVectors = false) { _, id, hash, _ -> live[id] = hash }
        return live
    }

    /** Record number of each [live] song's last record whose hash is current. */
    private fun latestRecords(live: Map<Long, Long>): Map<Long, Int> {
        val latest = HashMap<Long, Int>()
        forEachRecord(withVectors = false) { index, id, hash, _ -> if (live[id] == hash) latest[id] = index }
        return latest
    }

    /** Appends [songs]; starts a new file when there is none or it belongs to another model. */
    fun append(songs: List<IndexedSong>) {
        if (songs.isEmpty()) return
        val fresh = !compatible()
        file.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(FileOutputStream(file, !fresh))).use { out ->
            if (fresh) writeHeader(out)
            songs.forEach { writeRecord(out, it) }
        }
    }

    /** The current vectors of the [wanted] songs among [live] (id -> hash), in one streamed pass. */
    fun vectorsOf(
        live: Map<Long, Long>,
        wanted: Set<Long>,
    ): Map<Long, FloatArray> {
        if (wanted.isEmpty()) return emptyMap()
        val current = latestRecords(live.filterKeys { it in wanted })
        val found = HashMap<Long, FloatArray>()
        forEachRecord(withVectors = true) { index, id, _, vector -> if (current[id] == index) found[id] = vector }
        return found
    }

    /** The stored vector of [songId], or null. */
    fun vectorOf(songId: Long): FloatArray? {
        var found: FloatArray? = null
        forEachRecord(withVectors = true) { _, id, _, vector -> if (id == songId) found = vector }
        return found
    }

    /**
     * The [limit] best songs by [score] (null = leave the song out), best first, equal scores by
     * song id. Only songs in [live] (id -> hash) count, with their current vector.
     */
    fun rank(
        live: Map<Long, Long>,
        limit: Int,
        score: (songId: Long, vector: FloatArray) -> Float?,
    ): List<VybeMatch> {
        if (limit <= 0) return emptyList()
        val order = compareBy<VybeMatch> { it.score }.thenByDescending { it.songId }
        val best = PriorityQueue(limit + 1, order)
        // Pass 1 (ids only): each live song's last record. Pass 2: score just those, streamed.
        val current = latestRecords(live)
        forEachRecord(withVectors = true) { index, id, _, vector ->
            if (current[id] != index) return@forEachRecord
            val value = score(id, vector)?.takeIf { it.isFinite() } ?: return@forEachRecord
            best.add(VybeMatch(id, value))
            if (best.size > limit) best.poll()
        }
        return best.sortedWith(order.reversed())
    }

    /** Rewrites the file with the [keep] songs' current records only. */
    fun compact(keep: Map<Long, Long>) {
        if (!compatible()) return
        val current = latestRecords(keep)
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
            writeHeader(out)
            forEachRecord(withVectors = true) { index, id, hash, vector ->
                if (current[id] == index) writeRecord(out, IndexedSong(id, hash, vector))
            }
        }
        replaceWith(tmp)
    }

    /** Number of records on disk (live or superseded): for deciding when to [compact]. */
    fun recordCount(): Int = if (compatible()) ((file.length() - headerBytes()) / recordBytes).toInt() else 0

    fun compatibleWith(): Boolean = compatible()

    private fun compatible(): Boolean {
        upgradeLegacy()
        return headerVersion() == FORMAT_VERSION
    }

    /** The file's format version when it belongs to this model and dimension, else null. */
    private fun headerVersion(): Int? =
        if (!file.isFile) {
            null
        } else {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { readHeader(it) }
            } catch (_: IOException) {
                null
            }
        }

    /** Rewrites a format-1 (float32) file of this model in the current format, keeping every record. */
    private fun upgradeLegacy() {
        if (upgradeChecked) return
        upgradeChecked = true
        if (headerVersion() != LEGACY_FLOAT_VERSION) return
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
            writeHeader(out)
            readRecords(LEGACY_FLOAT_VERSION, withVectors = true) { _, id, hash, vector ->
                writeRecord(out, IndexedSong(id, hash, vector))
            }
        }
        replaceWith(tmp)
    }

    private fun replaceWith(tmp: File) {
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Couldn't replace ${file.name}")
        }
    }

    private fun headerBytes(): Long = MAGIC.length.toLong() + Int.SIZE_BYTES * 2 + Short.SIZE_BYTES + modelId.toByteArray().size

    private fun writeHeader(out: DataOutputStream) {
        out.writeBytes(MAGIC)
        out.writeInt(FORMAT_VERSION)
        out.writeInt(dimension)
        out.writeUTF(modelId)
    }

    /** The header's format version when it is a known format of this dimension and model, else null. */
    private fun readHeader(input: DataInputStream): Int? {
        val magic = ByteArray(MAGIC.length)
        input.readFully(magic)
        if (String(magic) != MAGIC) return null
        val version = input.readInt()
        return version.takeIf { it in KNOWN_VERSIONS && input.readInt() == dimension && input.readUTF() == modelId }
    }

    private fun writeRecord(
        out: DataOutputStream,
        song: IndexedSong,
    ) {
        require(song.vector.size == dimension) { "vector size ${song.vector.size} != $dimension" }
        out.writeLong(song.songId)
        out.writeLong(song.textHash)
        val (scale, bytes) = quantize(song.vector)
        out.writeFloat(scale)
        out.write(bytes)
    }

    private inline fun forEachRecord(
        withVectors: Boolean,
        action: (index: Int, songId: Long, textHash: Long, vector: FloatArray) -> Unit,
    ) {
        upgradeLegacy()
        readRecords(FORMAT_VERSION, withVectors, action)
    }

    /** Streams the records of a [version] file; nothing when the file is missing or in another format. */
    @Suppress("NestedBlockDepth", "ReturnCount")
    private inline fun readRecords(
        version: Int,
        withVectors: Boolean,
        action: (index: Int, songId: Long, textHash: Long, vector: FloatArray) -> Unit,
    ) {
        if (!file.isFile) return
        DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
            try {
                if (readHeader(input) != version) return
            } catch (_: IOException) {
                return
            }
            val empty = FloatArray(0)
            // One bulk read per record, decoded from the bytes: not one readFloat() call per value.
            val raw = ByteArray(vectorBytes(version, dimension))
            var index = 0
            while (true) {
                val id =
                    try {
                        input.readLong()
                    } catch (_: EOFException) {
                        return
                    }
                try {
                    val hash = input.readLong()
                    // readFully, not skip: InputStream.skipNBytes needs Android 14.
                    input.readFully(raw)
                    action(index++, id, hash, if (withVectors) decode(version, raw, dimension) else empty)
                } catch (_: EOFException) {
                    // A record cut short by an interrupted write: everything before it stands.
                    return
                }
            }
        }
    }

    companion object {
        private const val MAGIC = "VYBEIDX1"

        /** Format 1: float32 vectors (only read, to convert). Format 2: int8 vectors, one float scale each. */
        private const val LEGACY_FLOAT_VERSION = 1
        private const val FORMAT_VERSION = 2
        private val KNOWN_VERSIONS = setOf(LEGACY_FLOAT_VERSION, FORMAT_VERSION)
        private const val BUFFER = 64 * 1024
        private const val INT8_MAX = 127f

        private fun vectorBytes(
            version: Int,
            dimension: Int,
        ): Int = if (version == LEGACY_FLOAT_VERSION) Float.SIZE_BYTES * dimension else Float.SIZE_BYTES + dimension

        /** Symmetric int8 with the vector's own scale (largest magnitude -> 127); a zero vector stays zero. */
        internal fun quantize(vector: FloatArray): Pair<Float, ByteArray> {
            var max = 0f
            for (v in vector) if (v.isFinite()) max = maxOf(max, abs(v))
            val scale = if (max > 0f) max / INT8_MAX else 0f
            val bytes = ByteArray(vector.size)
            if (scale > 0f) {
                for (i in vector.indices) {
                    val v = vector[i]
                    bytes[i] = if (v.isFinite()) (v / scale).roundToInt().coerceIn(-INT8_MAX.toInt(), INT8_MAX.toInt()).toByte() else 0
                }
            }
            return scale to bytes
        }

        private fun decode(
            version: Int,
            raw: ByteArray,
            dimension: Int,
        ): FloatArray {
            val buffer = ByteBuffer.wrap(raw) // big-endian, as DataOutputStream wrote it
            if (version == LEGACY_FLOAT_VERSION) return FloatArray(dimension) { buffer.getFloat(it * Float.SIZE_BYTES) }
            val scale = buffer.getFloat(0)
            return FloatArray(dimension) { raw[Float.SIZE_BYTES + it] * scale }
        }

        /**
         * The vector size [file] was written with, when it is an index of [modelId]: lets the
         * stored vectors be read without loading the model. Null when missing or incompatible.
         */
        @Suppress("ReturnCount")
        fun storedDimension(
            file: File,
            modelId: String,
        ): Int? {
            if (!file.isFile) return null
            return try {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                    val magic = ByteArray(MAGIC.length)
                    input.readFully(magic)
                    if (String(magic) != MAGIC || input.readInt() !in KNOWN_VERSIONS) return null
                    val dimension = input.readInt()
                    dimension.takeIf { it > 0 && input.readUTF() == modelId }
                }
            } catch (_: IOException) {
                null
            }
        }
    }
}

/** A stable 64-bit hash of a description (FNV-1a over UTF-16 units): changes whenever the text does. */
fun textHash(text: String): Long {
    var hash = FNV_OFFSET
    for (ch in text) {
        hash = hash xor ch.code.toLong()
        hash *= FNV_PRIME
    }
    return hash
}

private const val FNV_OFFSET = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
