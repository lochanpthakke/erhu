package com.erhu.erhu.core.network.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What is known about one track online. */
@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.lyrics.CachedStatus")
enum class CachedStatus { FOUND, NOT_FOUND, INSTRUMENTAL }

/**
 * A fetched result for one track, kept so lyrics work offline and are not fetched again.
 * [fingerprint] is the track metadata it was matched for: if the tags change, it no longer applies.
 * [userChosen]: the user picked this result themselves (it may then replace the file's own lyrics).
 */
@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.lyrics.CachedLyrics")
data class CachedLyrics(
    val schema: Int = SCHEMA,
    val songId: Long,
    val fingerprint: String,
    val status: CachedStatus,
    val candidate: LyricsCandidate? = null,
    val fetchedAtMs: Long,
    val userChosen: Boolean = false,
    /** Absent in old files: those remain legacy saved content, not explicit downloads. */
    val explicitDownload: Boolean = false,
    /** New saves also bind the MediaStore URI. Legacy records have no URI and remain retained. */
    val sourceUri: String? = null,
) {
    companion object {
        const val SCHEMA = 1
    }
}

/** Durable storage of explicit downloads and retained legacy content. New responses live in memory. */
interface OnlineLyricsCache {
    suspend fun contains(songId: Long): Boolean = get(songId) != null

    suspend fun get(songId: Long): CachedLyrics?

    suspend fun put(entry: CachedLyrics)

    suspend fun remove(songId: Long)
}

/** The track's identity plus the metadata a match depends on: a re-tagged file is looked up again. */
fun fingerprint(query: LyricsQuery): String =
    listOf(query.title, query.artist, query.album.orEmpty(), (query.durationMs / FINGERPRINT_DURATION_STEP_MS).toString())
        .joinToString("|") { it.trim().lowercase(Locale.ROOT) }

private const val FINGERPRINT_DURATION_STEP_MS = 1_000L

/** One small JSON file per song in the app's own storage (no database schema change). */
@Singleton
class FileOnlineLyricsCache
    internal constructor(
        private val directory: File,
    ) : OnlineLyricsCache {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(File(context.filesDir, "online-lyrics"))

        private val json = Json { ignoreUnknownKeys = true }

        private fun file(songId: Long) = File(directory, "$songId.json")

        override suspend fun contains(songId: Long): Boolean = file(songId).exists()

        @Suppress("SwallowedException")
        override suspend fun get(songId: Long): CachedLyrics? =
            try {
                file(songId).takeIf { it.isFile }?.readText()?.let { json.decodeFromString<CachedLyrics>(it) }?.takeIf {
                    it.schema == CachedLyrics.SCHEMA && it.songId == songId
                }
            } catch (e: IOException) {
                null
            } catch (e: SerializationException) {
                null // A damaged entry is simply a cache miss.
            } catch (e: IllegalArgumentException) {
                null
            }

        override suspend fun put(entry: CachedLyrics) {
            val target = file(entry.songId)
            if (target.exists() && get(entry.songId) == null) throw IOException("The existing saved lyrics could not be read")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create lyrics directory")
            val temp = File(directory, "${entry.songId}.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temp).use { stream ->
                    stream.write(json.encodeToString(CachedLyrics.serializer(), entry).toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
        }

        override suspend fun remove(songId: Long) {
            val target = file(songId)
            if (target.exists() && !target.delete()) throw IOException("Could not remove saved lyrics")
        }
    }
