package com.lochanpthakke.empee3.core.vybe

/** What a text is embedded as: a user's search query, or a song description being searched. */
enum class EmbeddingRole { QUERY, DOCUMENT }

/**
 * Turns text into a vector, entirely on the device. The model runtime lives behind this
 * interface; nothing in VYBE depends on which model or library produces the vectors.
 *
 * Implementations run the model synchronously: call from a background thread. Any
 * model-specific input formatting (task prefixes for [EmbeddingRole], tokenisation,
 * truncation) belongs inside the implementation.
 */
interface TextEmbedder {
    /** Length of every vector this embedder returns. */
    val dimension: Int

    fun embed(
        text: String,
        role: EmbeddingRole,
    ): FloatArray
}
