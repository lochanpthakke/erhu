package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.Album
import com.erhu.erhu.feature.library.mix.RadioSeed

/** Presentation only: radios are built by the existing RadioRepository when tapped. */
internal sealed interface FeaturedSetlist {
    val key: String

    data class AlbumPick(
        val album: Album,
    ) : FeaturedSetlist {
        override val key: String get() = "album:${album.id}"
    }

    data class RadioPick(
        val album: Album,
        val identity: String,
        val artworkUri: String?,
        val supportingArtwork: List<String>,
    ) : FeaturedSetlist {
        val seed: RadioSeed get() = RadioSeed.Album(album.id)
        override val key: String get() = "radio:${album.id}"
    }
}

/** Small adapter over the existing daily/history-ranked picks; no new library read or ranking. */
internal object FeaturedSetlists {
    private const val RADIO_LIMIT = 3

    fun select(
        albums: List<Album>,
        artistArtwork: Map<Long, String>,
    ): List<FeaturedSetlist> {
        val picks = albums.filter { it.songCount > 0 }.distinctBy { it.id }
        val canStartRadio = picks.sumOf { it.songCount.toLong() } > 1
        val representedArtists = HashSet<Long>()
        var radios = 0
        return buildList {
            picks.forEach { album ->
                add(FeaturedSetlist.AlbumPick(album))
                // Compilation artwork must not imply that a single artist represents the record.
                if (!canStartRadio || radios >= RADIO_LIMIT || album.isCompilation) return@forEach
                val hasArtist = artistName(album) != null && album.artistId > 0
                if (hasArtist && !representedArtists.add(album.artistId)) return@forEach
                add(radioFor(album, picks, artistArtwork))
                radios++
            }
        }
    }

    internal fun radioFor(
        album: Album,
        picks: List<Album>,
        artistArtwork: Map<Long, String>,
    ): FeaturedSetlist.RadioPick {
        val artist = artistName(album)
        val hasArtist = artist != null && album.artistId > 0
        val portrait = if (hasArtist) artistArtwork[album.artistId]?.takeIf(::isLocalArtwork) else null
        val primary = portrait ?: album.artworkUri?.takeIf(::isLocalArtwork)
        val supporting =
            if (hasArtist) {
                picks
                    .filter { it.artistId == album.artistId && !it.isCompilation }
                    .mapNotNull { it.artworkUri?.takeIf(::isLocalArtwork) }
                    .distinct()
                    .filter { it != primary }
                    .take(2)
            } else {
                emptyList()
            }
        return FeaturedSetlist.RadioPick(album, artist ?: album.title, primary, supporting)
    }

    private fun artistName(album: Album): String? =
        album.artist.takeUnless { it.isBlank() || it.equals("Unknown Artist", ignoreCase = true) || it == "<unknown>" }

    /** Remote artist-profile URLs must never trigger a new download just for a Home card. */
    private fun isLocalArtwork(uri: String): Boolean =
        uri.startsWith("content://", ignoreCase = true) || uri.startsWith("file://", ignoreCase = true) || uri.startsWith("/")
}
