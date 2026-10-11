package com.lochanpthakke.empee3.core.network.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What is known about one track online. */
@Serializable
enum class CachedStatus { FOUND, NOT_FOUND, INSTRUMENTAL }

/**
 * A fetched result for one track, kept so lyrics work offline and are not fetched again.
 * [fingerprint] is the track metadata it was matched for: if the tags change, it no longer applies.
 * [userChosen]: the user picked this result themselves (it may then replace the file's own lyrics).
 */
@Serializable
data class CachedLyrics(
    val schema: Int = SCHEMA,
    val songId: Long,
    val fingerprint: String,
    val status: CachedStatus,
    val candidate: LyricsCandidate? = null,
    val fetchedAtMs: Long,
    val userChosen: Boolean = false,
) {
    companion object {
        const val SCHEMA = 1
    }
}

/** Storage of fetched lyrics. */
interface OnlineLyricsCache {
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
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : OnlineLyricsCache {
        private val json = Json { ignoreUnknownKeys = true }

        private fun file(songId: Long) = File(File(context.filesDir, DIRECTORY), "$songId.json")

        @Suppress("SwallowedException")
        override suspend fun get(songId: Long): CachedLyrics? =
            try {
                file(songId).takeIf { it.isFile }?.readText()?.let { json.decodeFromString<CachedLyrics>(it) }?.takeIf {
                    it.schema ==
                        CachedLyrics.SCHEMA
                }
            } catch (e: IOException) {
                null
            } catch (e: SerializationException) {
                null // A damaged entry is simply a cache miss.
            } catch (e: IllegalArgumentException) {
                null
            }

        @Suppress("SwallowedException")
        override suspend fun put(entry: CachedLyrics) {
            try {
                val target = file(entry.songId)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.writeText(json.encodeToString(CachedLyrics.serializer(), entry))
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
            } catch (e: IOException) {
                // Not cached: the lookup simply runs again next time.
            }
        }

        override suspend fun remove(songId: Long) {
            file(songId).delete()
        }

        private companion object {
            const val DIRECTORY = "online-lyrics"
        }
    }
