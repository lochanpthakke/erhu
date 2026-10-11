@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.tagger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppError
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagException
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes tags into a local audio file under scoped storage.
 *
 * JAudioTagger needs a real, seekable java.io.File, but on Android 10+ the app
 * may only write other apps' media through its content URI (after the user
 * granted access). So the file is:
 *  1. copied from its content URI into the app cache (kept as a backup),
 *  2. edited there with JAudioTagger, and the result re-read to verify it,
 *  3. streamed back over the original through the content URI,
 *  4. restored from the backup if step 3 fails part-way,
 *  5. rescanned so MediaStore (and then Room) pick up the new tags.
 * The original is never touched until a verified edited copy exists.
 */
@Singleton
class JAudioTaggerWriter
    @Inject
    constructor(
        private val store: AudioContentStore,
        private val workDir: TagEditWorkDir,
        private val rescanner: MediaRescanner,
        private val logger: Logger,
    ) {
        init {
            java.util.logging.Logger
                .getLogger("org.jaudiotagger")
                .level = Level.OFF
        }

        suspend fun writeTags(request: TagWriteRequest): AppResult<Unit> =
            withContext(Dispatchers.IO) {
                appResultOf(mapError = ::toAppError) {
                    require(request.hasChanges) { "Nothing to write" }
                    val extension =
                        audioFileExtension(request.filePath)
                            ?: throw UnsupportedFormatException("Unknown audio format for ${request.filePath}")

                    val dir = workDir.get().apply { mkdirs() }
                    val id = UUID.randomUUID().toString()
                    val backup = File(dir, "$id-original.$extension")
                    val edited = File(dir, "$id-edited.$extension")
                    try {
                        store.openInput(request.contentUri).use { input -> backup.outputStream().use { input.copyTo(it) } }
                        backup.copyTo(edited, overwrite = true)

                        applyTags(edited, request)
                        verifyTags(edited, request)
                        writeBack(request.contentUri, edited, backup)

                        rescanner.rescan(request.filePath, null)
                        logger.i(TAG, "Wrote tags to ${request.contentUri}")
                    } finally {
                        backup.delete()
                        edited.delete()
                    }
                }
            }

        private fun applyTags(
            file: File,
            request: TagWriteRequest,
        ) {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            request.title?.let { tag.setField(FieldKey.TITLE, it) }
            request.artist?.let { tag.setField(FieldKey.ARTIST, it) }
            request.album?.let { tag.setField(FieldKey.ALBUM, it) }
            request.year?.let { tag.setField(FieldKey.YEAR, it.toString()) }
            request.genre?.let { tag.setField(FieldKey.GENRE, it) }
            request.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
            request.bpm?.let { tag.setField(FieldKey.BPM, it.toString()) }
            request.lyrics?.let { tag.setField(FieldKey.LYRICS, it) }

            request.artworkBytes?.let { bytes ->
                val artworkFile = File(file.parentFile, "${file.nameWithoutExtension}-art.jpg")
                try {
                    artworkFile.writeBytes(downscaleArtwork(bytes))
                    tag.deleteArtworkField()
                    tag.setField(ArtworkFactory.createArtworkFromFile(artworkFile))
                } finally {
                    artworkFile.delete()
                }
            }

            audioFile.tag = tag
            AudioFileIO.write(audioFile)
        }

        /** Re-reads the edited copy so a silently broken write never reaches the original. */
        private fun verifyTags(
            file: File,
            request: TagWriteRequest,
        ) {
            val tag = AudioFileIO.read(file).tag ?: throw CannotWriteException("No tag after write")
            val expected =
                listOfNotNull(
                    request.title?.let { FieldKey.TITLE to it },
                    request.artist?.let { FieldKey.ARTIST to it },
                    request.album?.let { FieldKey.ALBUM to it },
                    request.genre?.let { FieldKey.GENRE to it },
                )
            for ((key, value) in expected) {
                val actual = tag.getFirst(key)
                if (actual != value) throw CannotWriteException("Verification failed for $key: '$actual'")
            }
            if (request.year != null && !tag.getFirst(FieldKey.YEAR).startsWith(request.year.toString())) {
                throw CannotWriteException("Verification failed for YEAR")
            }
        }

        private fun writeBack(
            contentUri: String,
            edited: File,
            backup: File,
        ) {
            try {
                store.openOverwrite(contentUri).use { output -> edited.inputStream().use { it.copyTo(output) } }
            } catch (e: SecurityException) {
                // Rejected before any byte was written — the original is intact.
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Write-back failed for $contentUri, restoring original", e)
                try {
                    store.openOverwrite(contentUri).use { output -> backup.inputStream().use { it.copyTo(output) } }
                } catch (restoreError: Exception) {
                    logger.e(TAG, "Could not restore $contentUri after failed write", restoreError)
                    e.addSuppressed(restoreError)
                }
                throw e
            }
        }

        private fun toAppError(error: Throwable): AppError =
            when (error) {
                is SecurityException ->
                    AppError.WriteAccessRequired(
                        "Android didn't allow empee3 to modify this file. Allow access when asked and try again.",
                        error,
                    )
                is FileNotFoundException ->
                    AppError.IoError("The file no longer exists or can't be opened.", error)
                is UnsupportedFormatException, is CannotReadException, is InvalidAudioFrameException ->
                    AppError.TagWriteError("This file's format can't be tagged.", error)
                is ReadOnlyFileException ->
                    AppError.TagWriteError("This file is read-only.", error)
                is CannotWriteException, is TagException ->
                    AppError.TagWriteError("Tags couldn't be written: ${error.message}", error)
                is IllegalArgumentException ->
                    AppError.ValidationError(error.message ?: "Invalid tag values")
                else -> AppError.TagWriteError("Tags couldn't be written: ${error.message ?: error.javaClass.simpleName}", error)
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

        private class UnsupportedFormatException(
            message: String,
        ) : Exception(message)

        companion object {
            private const val TAG = "JAudioTaggerWriter"
            private const val MAX_IMAGE_SIZE = 1440
        }
    }
