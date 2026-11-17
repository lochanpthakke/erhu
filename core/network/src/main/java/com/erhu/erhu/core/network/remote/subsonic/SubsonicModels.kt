package com.erhu.erhu.core.network.remote.subsonic

import com.erhu.erhu.core.model.RemoteTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where and as whom to call a Subsonic-compatible server. Held in memory only; never logged. */
class SubsonicConnection(
    val baseUrl: HttpUrl,
    val username: String,
    val password: String,
) {
    val isInsecure: Boolean get() = !baseUrl.isHttps

    /** No user name means [password] is an OpenSubsonic API key. */
    val usesApiKey: Boolean get() = username.isEmpty()

    override fun toString(): String = "SubsonicConnection(${baseUrl.host})"
}

data class SubsonicArtist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val artworkKey: String?,
)

data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val artworkKey: String?,
)

data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val songCount: Int,
    val artworkKey: String?,
)

/** Search results and the starred (favourite) listing share this shape. */
data class SubsonicResults(
    val artists: List<SubsonicArtist> = emptyList(),
    val albums: List<SubsonicAlbum> = emptyList(),
    val tracks: List<RemoteTrack> = emptyList(),
)

/**
 * The server address a user typed, as a base URL: a missing scheme means HTTPS, and a pasted
 * `/rest` suffix or trailing slash is dropped. Null when it is not a usable http(s) address.
 */
fun parseServerUrl(input: String): HttpUrl? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
    if (segments.lastOrNull().equals("rest", ignoreCase = true)) segments.removeAt(segments.lastIndex)
    return url
        .newBuilder()
        .encodedPath("/")
        .apply { segments.forEach { addPathSegment(it) } }
        .query(null)
        .fragment(null)
        .build()
}

// ---- Wire format (the subset of the Subsonic JSON responses erhu reads) ----

@Serializable
internal class EnvelopeDto(
    @SerialName("subsonic-response") val response: ResponseDto? = null,
)

@Serializable
internal class ResponseDto(
    val status: String = "",
    val error: ErrorDto? = null,
    val artists: ArtistsDto? = null,
    val artist: ArtistDto? = null,
    val album: AlbumDto? = null,
    val albumList2: AlbumListDto? = null,
    val searchResult3: ResultsDto? = null,
    val starred2: ResultsDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
)

@Serializable
internal class ErrorDto(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
internal class ArtistsDto(
    val index: List<IndexDto> = emptyList(),
)

@Serializable
internal class IndexDto(
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
internal class ArtistDto(
    val id: String = "",
    val name: String = "",
    val albumCount: Int = 0,
    val coverArt: String? = null,
    val album: List<AlbumDto> = emptyList(),
) {
    fun toModel() = SubsonicArtist(id, name, albumCount, coverArt?.takeIf { it.isNotBlank() })
}

@Serializable
internal class AlbumDto(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val year: Int = 0,
    val songCount: Int = 0,
    val coverArt: String? = null,
    val song: List<SongDto> = emptyList(),
) {
    fun toModel() = SubsonicAlbum(id, name, artist, year, songCount, coverArt?.takeIf { it.isNotBlank() })
}

@Serializable
internal class AlbumListDto(
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
internal class SongDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** Seconds. */
    val duration: Long = 0,
    val track: Int = 0,
    val discNumber: Int = 1,
    val contentType: String? = null,
    val coverArt: String? = null,
    /** A timestamp when the user starred the song; absent otherwise. */
    val starred: String? = null,
    val isDir: Boolean = false,
    val genre: String? = null,
    val year: Int = 0,
) {
    fun toModel() =
        RemoteTrack(
            remoteId = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration * MS_PER_SECOND,
            trackNumber = track,
            discNumber = discNumber.coerceAtLeast(1),
            mimeType = contentType?.takeIf { it.isNotBlank() },
            artworkKey = coverArt?.takeIf { it.isNotBlank() },
            isFavorite = !starred.isNullOrBlank(),
            genre = genre?.trim()?.takeIf { it.isNotEmpty() },
            year = year.takeIf { it in 1900..2099 } ?: 0,
        )

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}

@Serializable
internal class ResultsDto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
) {
    fun toModel() =
        SubsonicResults(
            artists = artist.filter { it.id.isNotEmpty() }.map { it.toModel() },
            albums = album.filter { it.id.isNotEmpty() }.map { it.toModel() },
            tracks = song.toTracks(),
        )
}

@Serializable
internal class PlaylistsDto(
    val playlist: List<PlaylistDto> = emptyList(),
)

@Serializable
internal class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val coverArt: String? = null,
    val entry: List<SongDto> = emptyList(),
) {
    fun toModel() = SubsonicPlaylist(id, name, songCount, coverArt?.takeIf { it.isNotBlank() })
}

/** Folders and entries without an id cannot be played and are left out. */
internal fun List<SongDto>.toTracks(): List<RemoteTrack> = filter { it.id.isNotEmpty() && !it.isDir }.map { it.toModel() }
