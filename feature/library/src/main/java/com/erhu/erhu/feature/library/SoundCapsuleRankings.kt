package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.artwork.ArtistArtwork
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.RankedAlbum
import com.erhu.erhu.core.model.RankedArtist
import com.erhu.erhu.core.model.RankedSong

@Composable
private fun RankNumber(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = rank.toString(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
internal fun TopSongRow(
    rank: Int,
    ranked: RankedSong,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankNumber(rank, modifier = Modifier.width(RANK_WIDTH))
        Spacer(modifier = Modifier.width(Spacing.sm))
        SongArtwork(song = ranked.song, modifier = Modifier.size(SONG_ARTWORK).clip(Radius.smallShape))
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = ranked.song.title, style = TextStyles.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = ranked.song.artist,
                style = TextStyles.itemSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        PlaysText(ranked.plays)
    }
}

@Composable
internal fun TopAlbumTile(
    rank: Int,
    ranked: RankedAlbum,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(ALBUM_TILE).clickable(onClick = onClick)) {
        Box {
            AlbumArtwork(
                artworkUri = ranked.album.artworkUri,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(Radius.mediumShape),
            )
            RankBadge(rank, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm))
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = ranked.album.title, style = TextStyles.tileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = ranked.album.artist,
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PlaysText(ranked.plays)
    }
}

@Composable
internal fun TopArtistTile(
    rank: Int,
    ranked: RankedArtist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(ARTIST_TILE).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ArtistArtwork(
                // "NONE…" means looked up, no photo: show the monogram.
                artworkUri = ranked.artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork),
                artistName = ranked.artist.name,
                modifier = Modifier.size(ARTIST_TILE).clip(CircleShape),
            )
            RankBadge(rank, modifier = Modifier.align(Alignment.TopStart))
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = ranked.artist.name,
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        PlaysText(ranked.plays)
    }
}

@Composable
private fun RankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(RANK_BADGE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun PlaysText(plays: Int) {
    Text(
        text = playsLabel(plays),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

internal fun playsLabel(plays: Int): String = if (plays == 1) "1 play" else "$plays plays"

private val RANK_WIDTH = 28.dp
private val SONG_ARTWORK = 56.dp
private val ALBUM_TILE = 148.dp
private val ARTIST_TILE = 112.dp
private val RANK_BADGE = 28.dp
