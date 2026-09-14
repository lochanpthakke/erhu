package com.lochanpthakke.empee3.core.database.scanner

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.dao.SongDao
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
                        )
                        continue
                    }

                    var lyrics: String? = null
                    var discNumber = 1
                    var composer: String? = null
                    var genre: String? = null

                    try {
                        val audioFile = AudioFileIO.read(file)
                        val tag = audioFile.tag
                        if (tag != null) {
                            lyrics = tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotBlank() }
                            composer = tag.getFirst(FieldKey.COMPOSER).takeIf { it.isNotBlank() }
                            genre = tag.getFirst(FieldKey.GENRE).takeIf { it.isNotBlank() }
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
                        )
                    }
                }
            }

        companion object {
            private const val TAG = "JAudioTaggerEnricher"
        }
    }
