package com.lochanpthakke.empee3.core.model

enum class MetadataFixAction(val displayName: String) {
    ALL_FIELDS("All Fields"),
    TITLE_ONLY("Title Only"),
    ARTIST_ONLY("Artist Only"),
    ALBUM_ONLY("Album Only"),
    GENRE_ONLY("Genre Only"),
    BPM_ONLY("BPM Only"),
    LYRICS_ONLY("Lyrics Only"),
    ARTWORK_ONLY("Artwork Only"),
}
