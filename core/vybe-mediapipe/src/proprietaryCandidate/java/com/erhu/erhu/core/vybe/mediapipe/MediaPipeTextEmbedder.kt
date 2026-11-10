package com.erhu.erhu.core.vybe.mediapipe

import android.content.Context
import com.erhu.erhu.core.vybe.EmbeddingRole
import com.erhu.erhu.core.vybe.TextEmbedder
import java.io.Closeable
import java.io.File

class VybeEmbedderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** No native runtime or synthetic embeddings in the restricted distribution. */
@Suppress("UNUSED_PARAMETER")
class MediaPipeTextEmbedder private constructor() : TextEmbedder, Closeable {
    override val dimension: Int get() = throw VybeEmbedderException(UNAVAILABLE)
    override fun embed(text: String, role: EmbeddingRole): FloatArray = throw VybeEmbedderException(UNAVAILABLE)
    override fun close() = Unit

    companion object {
        private const val UNAVAILABLE = "VYBE is unavailable in this distribution pending native-library clearance."
        fun fromFile(context: Context, modelFile: File): MediaPipeTextEmbedder = throw VybeEmbedderException(UNAVAILABLE)
    }
}
