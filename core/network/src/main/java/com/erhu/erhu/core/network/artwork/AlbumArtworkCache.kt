package com.erhu.erhu.core.network.artwork

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Durable query metadata and bounded validated image bytes; separate from custom/embedded artwork. */
class AlbumArtworkCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Entry(
        val schema: Int = 1,
        val savedAt: Long,
        val candidates: List<AlbumArtworkCandidate>,
    )

    fun key(
        query: AlbumArtworkQuery,
        source: String,
    ): String = hash(json.encodeToString(query.copy(sourcePath = null)) + "|" + source)

    fun read(
        key: String,
        allowExpired: Boolean = false,
    ): List<AlbumArtworkCandidate>? =
        runCatching {
            val file = File(directory, "q-$key.json")
            if (!file.isFile || file.length() > 256 * 1024) return null
            val entry = json.decodeFromString<Entry>(file.readText())
            if (entry.schema != 1 || (!allowExpired && now() - entry.savedAt > 7L * 86400000)) return null
            entry.candidates
                .filter {
                    it.verifiedImage && imageFile(it.imageUrl).isFile
                }.onEach { imageFile(it.imageUrl).setLastModified(now()) }
                .takeIf { it.isNotEmpty() }
        }.getOrNull()

    fun write(
        key: String,
        candidates: List<AlbumArtworkCandidate>,
    ) {
        if (candidates.isEmpty()) return
        atomic(File(directory, "q-$key.json"), json.encodeToString(Entry(savedAt = now(), candidates = candidates)).toByteArray())
        prune()
    }

    fun saveImage(
        url: String,
        bytes: ByteArray,
    ) = atomic(imageFile(url), bytes)

    fun bytes(url: String): ByteArray? =
        runCatching {
            imageFile(url).takeIf { it.isFile && it.length() <= 3 * 1024 * 1024 }?.readBytes()
        }.getOrNull()

    fun preview(url: String): String = imageFile(url).takeIf(File::isFile)?.toURI()?.toString() ?: url

    private fun imageFile(url: String) = File(directory, "img-${hash(url)}.bin")

    private fun atomic(
        target: File,
        bytes: ByteArray,
    ) {
        directory.mkdirs()
        val temp = File(directory, target.name + ".tmp")
        try {
            temp.outputStream().use {
                it.write(bytes)
                it.flush()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (
                _: java.nio.file.AtomicMoveNotSupportedException,
            ) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    private fun prune() {
        val files =
            directory
                .listFiles()
                ?.filter { it.name.startsWith("img-") }
                ?.sortedBy(File::lastModified)
                .orEmpty()
        var bytes = files.sumOf(File::length)
        for (file in files) {
            if (bytes <= 64L * 1024 * 1024) break
            bytes -= file.length()
            file.delete()
        }
        directory
            .listFiles()
            ?.filter { it.name.startsWith("q-") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(256)
            ?.forEach(File::delete)
    }

    companion object {
        fun hash(value: String) = hash(value.toByteArray())

        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
