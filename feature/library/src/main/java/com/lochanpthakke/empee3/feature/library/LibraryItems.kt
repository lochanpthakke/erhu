package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtistArtwork
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.foundation.sharedArtwork
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song

/*
 * Library content items (Tracks, Favorites, Albums, Artists). Rows share one
 * structure — 48 dp leading visual, a strong title, one quiet secondary line —
 * and sit directly on the screen background: no cards, borders or dividers.
 */

/** Separator between pieces of secondary metadata. */
private const val META_SEPARATOR = " · "

/**
 * A track in the library: artwork, title (accent colour when it is the playing
 * song), "artist · album", then [trailing] actions.
 */
@Composable
fun TrackRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    LibraryRow(
        title = song.title,
        subtitle = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(META_SEPARATOR),
        isHighlighted = isCurrent,
        onClick = onClick,
        modifier = modifier,
        leading = {
            if (showTrackNumber && song.trackNumber > 0) {
                Box(
                    modifier = Modifier.size(ArtworkSize.list),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = song.trackNumber.toString(),
                        style = TextStyles.itemTitle,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SongArtwork(
                    song = song,
                    modifier =
                        Modifier
                            .size(ArtworkSize.list)
                            .clip(Radius.smallShape),
                )
            }
        },
        trailing = trailing,
    )
}

/** An artist: monogram avatar, name, "n songs · n albums". */
@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryRow(
        title = artist.name,
        subtitle = countLabel(artist.songCount, "song") + META_SEPARATOR + countLabel(artist.albumCount, "album"),
        isHighlighted = false,
        onClick = onClick,
        // No action buttons: the text keeps the same gutter as the leading side.
        endInset = Spacing.screenGutter,
        modifier = modifier,
        leading = { ArtistMonogram(name = artist.name) },
    )
}

/** An album tile for the artwork grid: square cover, title, artist. Width comes from the grid. */
@Composable
fun AlbumTile(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not clipped to a rounded shape: the corner would cut into the first glyph of
    // the bottom text line. Only the artwork is rounded.
    Column(modifier = modifier.clickable(onClick = onClick)) {
        AlbumArtwork(
            artworkUri = album.artworkUri,
            contentDescription = null,
            modifier =
                Modifier
                    .sharedArtwork(albumArtworkKey(album.id))
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = album.title,
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A playlist's cover: the user's own artwork, or the default playlist cover. */
@Composable
fun PlaylistCover(
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    AlbumArtwork(
        artworkUri = playlist.artworkUri,
        contentDescription = null,
        placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
        modifier = modifier,
    )
}

/** Artwork-first playlist card, the same treatment as [AlbumTile]. */
@Composable
fun PlaylistTile(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        PlaylistCover(
            playlist = playlist,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = playlist.name,
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = countLabel(playlist.songCount, "song"),
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** First cell of the playlist grid: same footprint as a playlist card. */
@Composable
fun NewPlaylistTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.large),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = "New playlist", style = TextStyles.tileTitle, maxLines = 1)
    }
}

/**
 * An artist tile for the artwork grid: a circular artist picture (or a large
 * monogram when there is none), the name, and song / album counts.
 */
@Composable
fun ArtistTile(
    artist: Artist,
    artworkUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistArtwork(
            artworkUri = artworkUri,
            artistName = artist.name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = artist.name,
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = countLabel(artist.songCount, "song") + META_SEPARATOR + countLabel(artist.albumCount, "album"),
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // Trailing icon buttons carry their own padding, so rows with actions use a small end inset.
    endInset: Dp = Spacing.xs,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = Spacing.screenGutter, end = endInset, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyles.listTitle,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyles.listSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** Artists have no artwork in the library data, so they get a quiet initial in a circle. */
@Composable
private fun ArtistMonogram(name: String) {
    val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(
        modifier =
            Modifier
                .size(ArtworkSize.list)
                .clip(CircleShape)
                .background(EmPee3Glass.chromeColor(MaterialTheme.colorScheme.surfaceVariant)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = TextStyles.listTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "1 song", "3 songs". */
internal fun countLabel(
    count: Int,
    noun: String,
): String = if (count == 1) "1 $noun" else "$count ${noun}s"
