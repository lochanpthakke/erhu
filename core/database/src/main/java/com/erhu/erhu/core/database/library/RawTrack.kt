package com.erhu.erhu.core.database.library

/** One MediaStore row exactly as read: nothing here is parsed or resolved. */
data class RawTrack(
    val songId: Long,
    val title: String,
    val rawArtist: String,
    val rawAlbumArtist: String?,
    val album: String,
    val trackNumber: Int,
    val discNumber: Int,
    val folder: String,
    val isCompilation: Boolean,
) {
    /** The only view of a track that album grouping may see: it has no track-artist field. */
    fun toAlbumInput(): AlbumInput = AlbumInput(songId, album, rawAlbumArtist, folder, trackNumber, discNumber, isCompilation)
}

data class AlbumInput(
    val songId: Long,
    val album: String,
    val rawAlbumArtist: String?,
    val folder: String,
    val trackNumber: Int,
    val discNumber: Int,
    val isCompilation: Boolean,
)

enum class CreditRole { PRIMARY, FEATURED }
