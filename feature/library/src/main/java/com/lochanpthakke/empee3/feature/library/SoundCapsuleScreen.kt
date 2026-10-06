package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.RankedSong
import com.lochanpthakke.empee3.core.model.SoundCapsule
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sound Capsule: a month of listening. The month's top song leads as a large cover,
 * then the totals, the top songs, albums and artists. Albums and artists open their
 * existing detail pages; a song plays the month's top songs from there.
 */
@Composable
fun SoundCapsuleScreen(
    viewModel: SoundCapsuleViewModel,
    onOpenDrawer: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month by viewModel.month.collectAsState()
    val capsule by viewModel.capsule.collectAsState()

    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.TopLevel(onOpenDrawer), title = "Sound Capsule") },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding = PaddingValues(top = Spacing.sm + scroll.contentTop, bottom = Spacing.xl + scroll.contentBottom),
        ) {
            item(key = "month") { MonthHeader(month, onShowMonth = viewModel::showMonth) }
            val shown = capsule
            when {
                shown == null -> Unit
                shown.isEmpty -> item(key = "empty") { EmptyMonth(shown.month) }
                else -> capsuleContent(shown, viewModel::playTopSong, onOpenAlbum, onOpenArtist)
            }
        }
    }
}

private fun LazyListScope.capsuleContent(
    capsule: SoundCapsule,
    onPlaySong: (Int) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
) {
    capsule.topSongs.firstOrNull()?.let { top ->
        item(key = "hero") { TopSongHero(top, onClick = { onPlaySong(0) }) }
    }
    item(key = "summary") { MonthSummary(capsule) }
    if (capsule.topSongs.isNotEmpty()) {
        item(key = "songs-title") { SectionTitle("Top songs") }
        itemsIndexed(capsule.topSongs, key = { _, ranked -> "song-${ranked.song.id}" }) { index, ranked ->
            TopSongRow(rank = index + 1, ranked = ranked, onClick = { onPlaySong(index) })
        }
    }
    if (capsule.topAlbums.isNotEmpty()) {
        item(key = "albums-title") { SectionTitle("Top albums") }
        item(key = "albums") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                itemsIndexed(capsule.topAlbums, key = { _, ranked -> ranked.album.id }) { index, ranked ->
                    TopAlbumTile(rank = index + 1, ranked = ranked, onClick = { onOpenAlbum(ranked.album) })
                }
            }
        }
    }
    if (capsule.topArtists.isNotEmpty()) {
        item(key = "artists-title") { SectionTitle("Top artists") }
        item(key = "artists") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                itemsIndexed(capsule.topArtists, key = { _, ranked -> ranked.artist.id }) { index, ranked ->
                    TopArtistTile(rank = index + 1, ranked = ranked, onClick = { onOpenArtist(ranked.artist) })
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: CapsuleMonth,
    onShowMonth: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { month.previous?.let(onShowMonth) }, enabled = month.previous != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "YOUR MONTH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(text = month.month.format(MONTH_FORMAT), style = TextStyles.pageTitle)
        }
        IconButton(onClick = { month.next?.let(onShowMonth) }, enabled = month.next != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** The month's most played song as a large cover with its count over a scrim. */
@Composable
private fun TopSongHero(
    top: RankedSong,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT)
                .clip(Radius.largeShape)
                .clickable(onClickLabel = "Play", onClick = onClick),
    ) {
        SongArtwork(song = top.song, modifier = Modifier.fillMaxSize())
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = SCRIM_ALPHA))),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.lg)) {
            Text(text = "Your top song", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = SECONDARY_ALPHA))
            Text(
                text = top.song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${top.song.artist} · ${playsLabel(top.plays)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MonthSummary(capsule: SoundCapsule) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val minutes = capsule.listenedMs / MS_PER_MINUTE
        val playsLabel = if (capsule.totalPlays == 1) "play" else "plays"
        SummaryTile(value = capsule.totalPlays.toString(), label = playsLabel, modifier = Modifier.weight(1f))
        SummaryTile(value = minutes.toString(), label = if (minutes == 1L) "minute" else "minutes", modifier = Modifier.weight(1f))
        SummaryTile(value = capsule.longestStreakDays.toString(), label = "day streak", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.glassPanel(MaterialTheme.shapes.medium, elevated = false, level = GlassLevel.Minimal),
        colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyles.sectionTitle,
        modifier = Modifier.padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xl, bottom = Spacing.sm),
    )
}

@Composable
private fun EmptyMonth(month: YearMonth) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EMPTY_ICON),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "Nothing played in ${month.format(MONTH_NAME_FORMAT)} yet",
            style = TextStyles.sectionTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "Songs you play this month show up here: your top songs, albums and artists.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val MONTH_NAME_FORMAT = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())

private const val MS_PER_MINUTE = 60_000L
private const val HERO_ASPECT = 1.4f
private const val SCRIM_ALPHA = 0.75f
private const val SECONDARY_ALPHA = 0.8f
private val EMPTY_ICON = 56.dp
