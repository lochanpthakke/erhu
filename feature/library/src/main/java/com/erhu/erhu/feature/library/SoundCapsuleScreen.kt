package com.erhu.erhu.feature.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.artwork.ArtistArtwork
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.RankedArtist
import com.erhu.erhu.core.model.SoundCapsule
import java.time.YearMonth
import com.erhu.erhu.core.common.R as CommonR

/**
 * Sound Capsule: a month of listening.
 * Screen order:
 * 1. Toolbar (circular glass back button, collapsing title, share button)
 * 2. Month and year selector
 * 3. Listening summary: minutes listened, plays and tracks in three equal columns
 * 4. Top Artists: ranked list with the library's row metrics (48 dp artwork)
 * 5. Top Songs: horizontal shelf of square artwork cards
 * 6. Top Albums: horizontal shelf matching Top Songs
 * 7. Existing global mini-player
 *
 * Sizes and type come from the shared tokens (library rows, shelf cards), so the screen
 * reads like the rest of the app.
 */
@Composable
fun SoundCapsuleScreen(
    viewModel: SoundCapsuleViewModel,
    onOpenDrawer: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onOpenDrawer,
    onShareStats: (() -> Unit)? = null,
) {
    val month by viewModel.month.collectAsState()
    val capsule by viewModel.capsule.collectAsState()
    val collapsingTitle = rememberCollapsingTitleState()
    val listState = rememberLazyListState()

    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                title = stringResource(CommonR.string.nav_sound_capsule),
                collapsingTitle = collapsingTitle,
                actions = {
                    if (onShareStats != null) {
                        IconButton(onClick = onShareStats) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(CommonR.string.share_stats_title),
                                modifier = Modifier.size(IconSize.standard),
                            )
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) {
            ReportTitleScroll(listState)
        }

        val scroll = innerPadding.forScrollingContent(topOverlay = true)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            // contentBottom already covers the mini player and the system bars.
            contentPadding = PaddingValues(top = scroll.contentTop, bottom = scroll.contentBottom + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            item(key = "month_selector") {
                MonthHeader(month, onShowMonth = viewModel::showMonth)
            }

            capsuleSections(
                capsule = capsule,
                onPlayTopSong = viewModel::playTopSong,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
            )
        }
    }
}

/** The month's figures and rankings, below the month selector. */
private fun LazyListScope.capsuleSections(
    capsule: SoundCapsule?,
    onPlayTopSong: (Int) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
) {
    val shown = capsule
        when {
            shown == null -> Unit
            shown.isEmpty -> item(key = "empty") { EmptyMonth(shown.month) }
            else -> {
                item(key = "listening_summary") {
                    ListeningSummary(capsule = shown)
                }
                if (shown.topArtists.isNotEmpty()) {
                    item(key = "artists_list") {
                        TopArtistsSection(artists = shown.topArtists.take(TOP), onOpenArtist = onOpenArtist)
                    }
                }
                if (shown.topSongs.isNotEmpty()) {
                    item(key = "songs_carousel") {
                        Shelf(title = stringResource(CommonR.string.capsule_top_songs)) {
                            itemsIndexed(shown.topSongs.take(TOP), key = { _, item -> item.song.id }) { index, ranked ->
                                RankedShelfTile(
                                    rank = index + 1,
                                    title = ranked.song.title,
                                    subtitle = ranked.song.artist,
                                    plays = ranked.plays,
                                    onClick = { onPlayTopSong(index) },
                                ) { SongArtwork(song = ranked.song, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
                if (shown.topAlbums.isNotEmpty()) {
                    item(key = "albums_carousel") {
                        Shelf(title = stringResource(CommonR.string.capsule_top_albums)) {
                            itemsIndexed(shown.topAlbums.take(TOP), key = { _, item -> item.album.id }) { index, ranked ->
                                RankedShelfTile(
                                    rank = index + 1,
                                    title = ranked.album.title,
                                    subtitle = ranked.album.artist,
                                    plays = ranked.plays,
                                    onClick = { onOpenAlbum(ranked.album) },
                                ) { AlbumArtwork(artworkUri = ranked.album.artworkUri, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        }
}

/**
 * Month and year with previous / next. The end inset matches the toolbar's, so the arrows
 * line up under its share button.
 */
@Composable
private fun MonthHeader(
    month: CapsuleMonth,
    onShowMonth: (YearMonth) -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenGutter, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocaleFormatters.formatMonthYear(month.month, locale),
            style = TextStyles.pageTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { month.previous?.let(onShowMonth) },
            enabled = month.previous != null,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(CommonR.string.capsule_cd_previous_month),
                modifier = Modifier.size(IconSize.standard),
            )
        }
        IconButton(
            onClick = { month.next?.let(onShowMonth) },
            enabled = month.next != null,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(CommonR.string.capsule_cd_next_month),
                modifier = Modifier.size(IconSize.standard),
            )
        }
    }
}

/** Minutes listened, plays and tracks: three equal columns straight on the page background. */
@Composable
private fun ListeningSummary(capsule: SoundCapsule) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val minutes = (capsule.listenedMs / MS_PER_MINUTE).coerceAtLeast(0L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SummaryFigure(
            value = LocaleFormatters.formatInteger(minutes.toInt(), locale),
            label = stringResource(CommonR.string.capsule_minutes_listened),
            modifier = Modifier.weight(1f),
        )
        SummaryFigure(
            value = LocaleFormatters.formatInteger(capsule.totalPlays, locale),
            label = pluralStringResource(CommonR.plurals.capsule_plays_unit, capsule.totalPlays).replaceFirstChar { it.uppercase(locale) },
            modifier = Modifier.weight(1f),
        )
        SummaryFigure(
            value = LocaleFormatters.formatInteger(capsule.uniqueTracks, locale),
            label = pluralStringResource(CommonR.plurals.capsule_tracks_unit, capsule.uniqueTracks).replaceFirstChar { it.uppercase(locale) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = label,
            style = TextStyles.listSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The uppercase heading every section shares, on the screen gutter. */
@Composable
private fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(AppLocaleManager.activeLocale(LocalContext.current)),
        style = TextStyles.sectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = HEADING_LETTER_SPACING,
        modifier = modifier.padding(horizontal = Spacing.screenGutter),
    )
}

/** Top artists as library rows: rank, round photo, name over play count. */
@Composable
private fun TopArtistsSection(
    artists: List<RankedArtist>,
    onOpenArtist: (Artist) -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(stringResource(CommonR.string.capsule_top_artists))
        Spacer(modifier = Modifier.height(Spacing.xs))
        artists.forEachIndexed { index, ranked ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(ranked.artist) }
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = LocaleFormatters.formatInteger(index + 1, locale),
                    style = TextStyles.listTitle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(RANK_WIDTH),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                ArtistArtwork(
                    artworkUri = ranked.artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork),
                    artistName = ranked.artist.name,
                    modifier = Modifier.size(ArtworkSize.list),
                    clipToCircle = true,
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ranked.artist.name,
                        style = TextStyles.listTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playsText(ranked.plays),
                        style = TextStyles.listSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A headed horizontal shelf; the gutter is content padding so cards scroll to the edge. */
@Composable
private fun Shelf(
    title: String,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(title)
        Spacer(modifier = Modifier.height(Spacing.md))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/**
 * A shelf card the size of the app's other shelves: square artwork with its rank, then title,
 * subtitle and play count. As in the album grid, only the artwork is rounded, so no corner
 * cuts into the text.
 */
@Composable
private fun RankedShelfTile(
    rank: Int,
    title: String,
    subtitle: String,
    plays: Int,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(ArtworkSize.shelf)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(ArtworkSize.shelf)
                .clip(Radius.mediumShape),
        ) {
            artwork()
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm)
                    .size(RANK_BADGE)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = RANK_BADGE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = LocaleFormatters.formatInteger(rank, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = title,
            style = TextStyles.tileTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playsText(plays),
            style = TextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Empty month placeholder. */
@Composable
private fun EmptyMonth(month: YearMonth) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(EMPTY_ICON),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(CommonR.string.capsule_empty_month_title, LocaleFormatters.formatMonthName(month, locale)),
            style = TextStyles.sectionTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = stringResource(CommonR.string.capsule_empty_month_body),
            style = TextStyles.listSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val TOP = 5
private const val MS_PER_MINUTE = 60_000L
private const val RANK_BADGE_ALPHA = 0.7f
private val HEADING_LETTER_SPACING = 0.8.sp
private val RANK_WIDTH = 24.dp
private val RANK_BADGE = 24.dp
private val EMPTY_ICON = 56.dp
