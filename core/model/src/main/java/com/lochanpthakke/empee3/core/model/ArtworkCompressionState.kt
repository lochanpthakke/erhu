package com.lochanpthakke.empee3.core.model

/** Per-album explicit operation state; no persistent job or automatic compression. */
data class ArtworkCompressionState(
    val available: Boolean = false,
    val running: Boolean = false,
    val progress: Int = 0,
    val message: String = "",
    val originalBytes: Long = 0,
    val compressedBytes: Long? = null,
)
