package com.lochanpthakke.empee3.core.database.library

/** The derived library for one scan; written to the database by the scanner. */
data class LibraryModel(
    val songs: Map<Long, SongAssignment>,
    val credits: List<CreditRow>,
    val albums: List<AlbumRow>,
    val albumArtists: List<AlbumArtistRow>,
    val artists: List<ArtistRow>,
    val unverifiedSegments: List<UnverifiedSegmentRow>,
)

data class SongAssignment(
    val albumId: Long,
    val primaryArtistId: Long,
)

data class CreditRow(
    val songId: Long,
    val artistId: Long,
    val role: CreditRole,
    val position: Int,
    val creditText: String,
    val isUnresolved: Boolean,
)

data class AlbumRow(
    val id: Long,
    val title: String,
    val displayArtist: String,
    val leadArtistId: Long,
    val songIds: List<Long>,
    val isCompilation: Boolean,
    val albumArtistInconsistent: Boolean,
    val groupingKey: String,
)

data class AlbumArtistRow(
    val albumId: Long,
    val artistId: Long,
    val position: Int,
)

data class ArtistRow(
    val id: Long,
    val key: String,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val isUnresolved: Boolean,
)

data class UnverifiedSegmentRow(
    val songId: Long,
    val artistId: Long,
    val segmentKey: String,
    val segmentIndex: Int,
)
