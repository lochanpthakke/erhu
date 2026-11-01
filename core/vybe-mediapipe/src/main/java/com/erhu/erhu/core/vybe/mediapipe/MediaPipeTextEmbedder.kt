package com.erhu.erhu.core.vybe.mediapipe

import android.content.Context
import android.os.ParcelFileDescriptor
import com.erhu.erhu.core.vybe.EmbeddingRole
import com.erhu.erhu.core.vybe.TextEmbedder
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.EmbeddingType
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextFormatContext
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextRole
import java.io.Closeable
import java.io.File
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder as MediaPipeEmbedder

/** The model file could not be loaded, or the model failed to produce an embedding. */
class VybeEmbedderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * A real [TextEmbedder]: Google's EmbeddingGemma 300m running on the device through the
 * MediaPipe Text Embedder task (`com.google.mediapipe:tasks-text`). The model bundle
 * (`embedding_gemma.task`, tokenizer included) is read from a local file the user supplied;
 * nothing is downloaded and inference never leaves the phone.
 *
 * Verified against tasks-text 1.1.0:
 * - loading mirrors the library's own `createFromFile(Context, File)` (file descriptor, closed
 *   once the task is created), with L2 normalisation switched on;
 * - prompts come from the library: `embed(text, TextFormatContext)` turns a retrieval query into
 *   `task: search result | query: …` and a document into `title: none | text: …`, the
 *   EmbeddingGemma prompts from its model card;
 * - a `.task` bundle (zip) runs on MediaPipe's own graph; only LiteRT-LM files (starting with
 *   `LITERTLM`) would need the separate litertlm library.
 *
 * Blocking: create and call it off the main thread, and [close] it (it holds the native model).
 */
class MediaPipeTextEmbedder private constructor(
    private val embedder: MediaPipeEmbedder,
) : TextEmbedder,
    Closeable {
    override val dimension: Int by lazy { embed(DIMENSION_PROBE, EmbeddingRole.QUERY).size }

    /** @throws VybeEmbedderException when the model fails or returns no embedding. */
    override fun embed(
        text: String,
        role: EmbeddingRole,
    ): FloatArray {
        val vector =
            try {
                embedder
                    .embed(text, formatFor(role))
                    .embeddingResult()
                    .embeddings()
                    .firstOrNull()
                    ?.floatEmbedding()
            } catch (e: MediaPipeException) {
                throw VybeEmbedderException("Embedding failed: ${e.message}", e)
            }
        if (vector == null || vector.isEmpty()) throw VybeEmbedderException("The model returned no embedding")
        return vector
    }

    override fun close() = embedder.close()

    companion object {
        private const val DIMENSION_PROBE = "probe"

        private val QUERY_FORMAT: TextFormatContext =
            TextFormatContext
                .builder()
                .setTaskType(EmbeddingType.RETRIEVAL_QUERY)
                .setRole(TextRole.QUERY)
                .build()
        private val DOCUMENT_FORMAT: TextFormatContext =
            TextFormatContext
                .builder()
                .setTaskType(EmbeddingType.RETRIEVAL_DOCUMENT)
                .setRole(TextRole.DOCUMENT)
                .build()

        private fun formatFor(role: EmbeddingRole): TextFormatContext =
            when (role) {
                EmbeddingRole.QUERY -> QUERY_FORMAT
                EmbeddingRole.DOCUMENT -> DOCUMENT_FORMAT
            }

        /**
         * Loads the model bundle at [modelFile].
         * @throws VybeEmbedderException when the file is missing or unreadable, or is not a model MediaPipe can load.
         */
        fun fromFile(
            context: Context,
            modelFile: File,
        ): MediaPipeTextEmbedder {
            // A missing file fails in ParcelFileDescriptor.open (FileNotFoundException, an IOException).
            return try {
                ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    val options =
                        TextEmbedderOptions
                            .builder()
                            .setBaseOptions(BaseOptions.builder().setModelAssetFileDescriptor(descriptor.fd).build())
                            .setL2Normalize(true)
                            .build()
                    MediaPipeTextEmbedder(MediaPipeEmbedder.createFromOptions(context, options))
                }
            } catch (e: java.io.IOException) {
                throw VybeEmbedderException("Cannot read the model file: ${e.message}", e)
            } catch (e: MediaPipeException) {
                throw VybeEmbedderException("Cannot load the model: ${e.message}", e)
            }
        }
    }
}
