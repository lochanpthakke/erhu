@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class LrclibLyricsDto(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Float? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    /** Line-timed LRC only; word timing is never in here. */
    val syncedLyrics: String? = null,
    /** True when [lyricsfile] carries word-level timing. */
    val hasWordSync: Boolean = false,
    /** LRCLIB's Lyricsfile (YAML): lines with start/end times and, with [hasWordSync], timed words. */
    val lyricsfile: String? = null,
)
