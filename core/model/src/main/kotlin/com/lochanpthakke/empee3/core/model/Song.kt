package com.lochanpthakke.empee3.core.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkData: ByteArray? = null,
    val isFavorite: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false

        return id == other.id &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            durationMs == other.durationMs &&
            uri == other.uri &&
            isFavorite == other.isFavorite &&
            (
                (artworkData == null && other.artworkData == null) ||
                    (artworkData != null && other.artworkData != null && artworkData.contentEquals(other.artworkData))
            )
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + (artworkData?.contentHashCode() ?: 0)
        return result
    }
}
