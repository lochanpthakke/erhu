package com.lochanpthakke.empee3.core.model

/**
 * What a metadata fix may change. [unsupportedReason] is set for actions no provider can
 * honestly serve: they are kept so older selections still parse, but never offered or run.
 */
enum class MetadataFixAction(
    val displayName: String,
    val unsupportedReason: String? = null,
) {
    ALL_FIELDS("All Fields"),
    TITLE_ONLY("Title Only"),
    ARTIST_ONLY("Artist Only"),
    ALBUM_ONLY("Album Only"),
    YEAR_ONLY("Year Only"),
    GENRE_ONLY("Genre Only"),
    BPM_ONLY("BPM Only"),
    LYRICS_ONLY("Lyrics Only", "Lyrics are looked up on the Lyrics page and are never written into files."),
    ARTWORK_ONLY("Artwork Only"),
    ;

    val isSupported: Boolean get() = unsupportedReason == null

    companion object {
        /** The actions offered in the UI, in display order. */
        val supported: List<MetadataFixAction> get() = entries.filter { it.isSupported }

        /** A stored name back to an action; unknown or unsupported names fall back to [ALL_FIELDS]. */
        fun fromStored(name: String?): MetadataFixAction = entries.firstOrNull { it.name == name && it.isSupported } ?: ALL_FIELDS
    }
}
