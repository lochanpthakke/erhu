package com.lochanpthakke.empee3.core.database.scanner

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.model.Genres
import com.lochanpthakke.empee3.core.network.tagger.readGenres
import com.lochanpthakke.empee3.core.network.tagger.usableBpm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JAudioTaggerEnricher
    @Inject
    constructor(
        private val songDao: SongDao,
        private val logger: Logger,
    ) {
        init {
            java.util.logging.Logger
                .getLogger("org.jaudiotagger")
                .level = Level.OFF
        }

        /** One text field as the file's own tag stores it, or null when it can't be read. */
        @Suppress("TooGenericExceptionCaught")
        fun readTextTag(
            filePath: String,
            key: FieldKey,
        ): String? =
            try {
                File(filePath)
                    .takeIf { it.exists() }
                    ?.let { AudioFileIO.read(it).tag?.getFirst(key) }
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                logger.w(TAG, "Couldn't read $key from $filePath: ${e.message}")
                null
            }

        @Suppress("TooGenericExceptionCaught")
        fun extractYearFromId3Header(filePath: String): Int {
            val file = File(filePath)
            if (!file.exists()) return 0
            return try {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: return 0
                val yearStr =
                    tag
                        .getFirst(FieldKey.YEAR)
                        .ifBlank { tag.getFirst(FieldKey.ORIGINAL_YEAR) }
                        .takeIf { it.isNotBlank() } ?: return 0

                val regex = Regex("""\b(19\d{2}|20\d{2})\b""")
                val match = regex.find(yearStr)
                match?.value?.toIntOrNull() ?: 0
            } catch (_: Exception) {
                0
            }
        }

        @Suppress("TooGenericExceptionCaught")
        suspend fun enrichUnenrichedSongs() =
            withContext(Dispatchers.IO) {
                val unenrichedSongs = songDao.getUnenrichedSongs()
                for (song in unenrichedSongs) {
                    val file = File(song.filePath)
                    if (!file.exists()) {
                        songDao.updateEnrichedData(
                            id = song.id,
                            lyrics = null,
                            discNumber = 1,
                            composer = null,
                            genre = null,
                            bpm = null,
                        )
                        continue
                    }

                    var lyrics: String? = null
                    var discNumber = 1
                    var composer: String? = null
                    var genre: String? = null
                    var bpm: Int? = null

                    try {
                        val audioFile = AudioFileIO.read(file)
                        val tag = audioFile.tag
                        if (tag != null) {
                            lyrics = tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotBlank() }
                            composer = tag.getFirst(FieldKey.COMPOSER).takeIf { it.isNotBlank() }
                            // All of the file's genres, stored joined ("Electronic; Synth-pop"); see Genres.
                            genre = Genres.join(tag.readGenres())
                            bpm = usableBpm(tag.getFirst(FieldKey.BPM))
                            val discStr = tag.getFirst(FieldKey.DISC_NO).takeIf { it.isNotBlank() }
                            if (discStr != null) {
                                discNumber = discStr.split("/")[0].toIntOrNull() ?: 1
                            }
                        }
                    } catch (e: Exception) {
                        logger.w(TAG, "Failed to enrich ${song.filePath}: ${e.message}", e)
                    } finally {
                        songDao.updateEnrichedData(
                            id = song.id,
                            lyrics = lyrics,
                            discNumber = discNumber,
                            composer = composer,
                            genre = genre,
                            bpm = bpm,
                        )
                    }
                }
            }

        companion object {
            private const val TAG = "JAudioTaggerEnricher"
        }
    }
