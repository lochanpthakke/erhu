@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.tagger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class TagWriteRequest(
    val filePath: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val lyrics: String? = null,
    val artworkBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagWriteRequest) return false

        return filePath == other.filePath &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            year == other.year &&
            genre == other.genre &&
            trackNumber == other.trackNumber &&
            lyrics == other.lyrics &&
            artworkBytes.contentEquals(other.artworkBytes)
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (year ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        return result
    }
}

@Singleton
@Suppress("detekt:All")
class JAudioTaggerWriter
    @Inject
    constructor(
        private val logger: Logger,
    ) {
        suspend fun writeTags(request: TagWriteRequest): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                appResultOf {
                    val file = File(request.filePath)
                    if (!file.exists()) {
                        throw IllegalArgumentException("Audio file does not exist: ${request.filePath}")
                    }

                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag ?: audioFile.createDefaultTag()

                    request.title?.let { tag.setField(FieldKey.TITLE, it) }
                    request.artist?.let { tag.setField(FieldKey.ARTIST, it) }
                    request.album?.let { tag.setField(FieldKey.ALBUM, it) }
                    request.year?.let { if (it > 0) tag.setField(FieldKey.YEAR, it.toString()) }
                    request.genre?.let { tag.setField(FieldKey.GENRE, it) }
                    request.trackNumber?.let { if (it > 0) tag.setField(FieldKey.TRACK, it.toString()) }
                    request.lyrics?.let { if (it.isNotBlank()) tag.setField(FieldKey.LYRICS, it) }

                    request.artworkBytes?.let { bytes ->
                        val compressedBytes = downscaleArtwork(bytes)
                        val artworkFile = File.createTempFile("art_temp", ".jpg")
                        try {
                            artworkFile.writeBytes(compressedBytes)
                            val artwork = ArtworkFactory.createArtworkFromFile(artworkFile)
                            tag.deleteArtworkField()
                            tag.setField(artwork)
                        } finally {
                            artworkFile.delete()
                        }
                    }

                    audioFile.tag = tag
                    AudioFileIO.write(audioFile)
                    logger.i(TAG, "Successfully wrote tags to ${request.filePath}")
                }
            }

        private fun downscaleArtwork(rawBytes: ByteArray): ByteArray {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > MAX_IMAGE_SIZE || options.outHeight / sampleSize > MAX_IMAGE_SIZE) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap =
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                    ?: return rawBytes

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            bitmap.recycle()
            return baos.toByteArray()
        }

        companion object {
            private const val TAG = "JAudioTaggerWriter"
            private const val MAX_IMAGE_SIZE = 1440
        }
    }
