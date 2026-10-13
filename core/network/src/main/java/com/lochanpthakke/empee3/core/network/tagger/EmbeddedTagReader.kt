package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.common.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.aiff.AiffTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.io.File
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

data class EmbeddedTagData(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
    val trackTotal: String? = null,
    val discNumber: String? = null,
    val discTotal: String? = null,
    val composer: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val bpm: String? = null,
    val artworkBytes: ByteArray? = null,
    val hasEmbeddedTag: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedTagData) return false
        return title == other.title &&
            artist == other.artist &&
            albumArtist == other.albumArtist &&
            album == other.album &&
            year == other.year &&
            genre == other.genre &&
            trackNumber == other.trackNumber &&
            trackTotal == other.trackTotal &&
            discNumber == other.discNumber &&
            discTotal == other.discTotal &&
            composer == other.composer &&
            comment == other.comment &&
            lyrics == other.lyrics &&
            bpm == other.bpm &&
            hasEmbeddedTag == other.hasEmbeddedTag &&
            (artworkBytes?.contentEquals(other.artworkBytes ?: byteArrayOf()) ?: (other.artworkBytes == null))
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (year?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber?.hashCode() ?: 0)
        result = 31 * result + (trackTotal?.hashCode() ?: 0)
        result = 31 * result + (discNumber?.hashCode() ?: 0)
        result = 31 * result + (discTotal?.hashCode() ?: 0)
        result = 31 * result + (composer?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (bpm?.hashCode() ?: 0)
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + hasEmbeddedTag.hashCode()
        return result
    }
}

@Singleton
@Suppress("TooGenericExceptionCaught", "MagicNumber")
class EmbeddedTagReader
    @Inject
    constructor(
        private val logger: Logger,
    ) {
        init {
            java.util.logging.Logger
                .getLogger("org.jaudiotagger")
                .level = Level.OFF
        }

        suspend fun readEmbeddedTags(filePath: String): EmbeddedTagData =
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (!file.exists() || !file.isFile) return@withContext EmbeddedTagData(hasEmbeddedTag = false)

                try {
                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag ?: return@withContext EmbeddedTagData(hasEmbeddedTag = false)

                    fun getTagField(key: FieldKey): String? =
                        try {
                            tag.getFirst(key).takeIf { it.isNotBlank() }
                        } catch (_: Exception) {
                            null
                        }

                    val artworkBytes =
                        try {
                            tag.firstArtwork?.binaryData
                        } catch (_: Exception) {
                            null
                        }

                    EmbeddedTagData(
                        title = getTagField(FieldKey.TITLE),
                        artist = getTagField(FieldKey.ARTIST),
                        albumArtist = getTagField(FieldKey.ALBUM_ARTIST),
                        album = getTagField(FieldKey.ALBUM),
                        year = getTagField(FieldKey.YEAR),
                        genre = getTagField(FieldKey.GENRE),
                        trackNumber = getTagField(FieldKey.TRACK),
                        trackTotal = getTagField(FieldKey.TRACK_TOTAL),
                        discNumber = getTagField(FieldKey.DISC_NO),
                        discTotal = getTagField(FieldKey.DISC_TOTAL),
                        composer = getTagField(FieldKey.COMPOSER),
                        comment = getTagField(FieldKey.COMMENT),
                        lyrics = getTagField(FieldKey.LYRICS),
                        bpm = getTagField(FieldKey.BPM),
                        artworkBytes = artworkBytes,
                        hasEmbeddedTag = true,
                    )
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to read embedded tags for $filePath: ${e.message}", e)
                    EmbeddedTagData(hasEmbeddedTag = false)
                }
            }

        /**
         * Whether the file's tag can hold a year and month without a day. ID3v2.3 (and v2.2), which
         * jaudiotagger also creates for untagged MP3s, stores a date as TYER + TDAT (day and month):
         * "2010-11" becomes "2010-11-01". ID3v2.4, Vorbis comments, MP4 and WAV keep any precision.
         * False when the file can't be read (the safe answer).
         */
        suspend fun canStoreYearMonth(filePath: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val audioFile = AudioFileIO.read(File(filePath))
                    val tag = audioFile.tag
                    when {
                        audioFile is MP3File -> audioFile.iD3v2Tag is ID3v24Tag
                        tag is AiffTag -> tag.iD3Tag is ID3v24Tag
                        else -> true
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Couldn't check the tag format of $filePath: ${e.message}", e)
                    false
                }
            }

        companion object {
            private const val TAG = "EmbeddedTagReader"
        }
    }
