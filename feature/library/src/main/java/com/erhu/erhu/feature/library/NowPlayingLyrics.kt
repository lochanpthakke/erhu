package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.designsystem.component.PressIconButton
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song

/**
 * The lyrics panel of Now Playing: a large rounded surface in the artwork's own colours with
 * several lines in view. Timed lyrics follow playback (the current line near the top, the next
 * ones below it; drag to read ahead, then "back to current"), tapping a line seeks there, plain
 * lyrics read as they are. The expand button opens the full lyrics; without lyrics the panel shows
 * the lookup state, and tapping it opens the full view where lyrics can be searched.
 */
@Composable
@Suppress("LongParameterList")
internal fun LyricsPanel(
    songId: Long,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    actions: LyricsActions?,
    colors: LyricsColors,
    onSeek: (Long) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The panel is where lyrics show now: asking for them works as the lyrics page always did
    // (once per song, file lyrics first, then the cached / LRCLIB lookup).
    if (actions != null) LaunchedEffect(songId) { actions.onShown(songId) }
    val current = lyrics?.takeIf { it.songId == songId }
    val plain = remember(current?.text) { LyricsParser.parse(current?.text) is Lyrics.Plain }
    LyricsSurfaceTheme(colors) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .nowPlayingSurface(colors, Radius.largeShape)
                    .clip(Radius.largeShape),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (plain) R.string.lyrics_not_synced else R.string.queue_tab_lyrics),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.content,
                    modifier = Modifier.weight(1f),
                )
                PressIconButton(onClick = onExpand) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = stringResource(R.string.lyrics_cd_expand),
                        modifier = Modifier.size(IconSize.small),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                key(songId) {
                    if (current?.text != null) {
                        CompositionLocalProvider(LocalLyricsLook provides LyricsLook(compact = true, alignStart = true)) {
                            LyricsContent(current, position, isPlaying, onSeek)
                        }
                    } else {
                        LookupLine(current, onClick = onExpand)
                    }
                }
            }
        }
    }
}

/**
 * Lyrics drawn in the artwork's colours: the lyrics views read their colours from the theme
 * (current line = onSurface, other lines = onSurfaceVariant, timed word = primary), so they are
 * given the palette's contrast-checked colours here.
 */
@Composable
internal fun LyricsSurfaceTheme(
    colors: LyricsColors,
    content: @Composable () -> Unit,
) {
    val scheme =
        MaterialTheme.colorScheme.copy(
            surface = colors.surface,
            onSurface = colors.content,
            onSurfaceVariant = colors.secondary,
            primary = colors.accent,
        )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides colors.content, content = content)
    }
}

/** No lyrics text (yet): what the lookup is doing, never invented lyrics. */
@Composable
private fun LookupLine(
    lyrics: SongLyrics?,
    onClick: () -> Unit,
) {
    val text =
        stringResource(
            when (lyrics?.lookup) {
                null, LyricsLookup.Idle -> R.string.lyrics_status_unavailable
                LyricsLookup.Searching -> R.string.lyrics_status_searching
                LyricsLookup.NotFound -> R.string.lyrics_status_not_found
                LyricsLookup.Instrumental -> R.string.lyrics_status_instrumental
                LyricsLookup.Offline -> R.string.lyrics_status_offline
                is LyricsLookup.RateLimited -> R.string.lyrics_status_rate_limited
                is LyricsLookup.Failed -> R.string.lyrics_status_failed
                is LyricsLookup.NeedsChoice -> R.string.lyrics_status_needs_choice
            },
        )
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lyrics_cd_open), onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}

/**
 * The full lyrics over Now Playing, on the same artwork-coloured surface: the existing lyrics view
 * (timed lines follow playback, tap a line to seek, drag to read ahead and resume following,
 * choose or search online lyrics) under a header naming the song, with the transport underneath
 * so playback stays at hand. Back or the arrow collapses it again.
 */
@Composable
@Suppress("LongParameterList")
internal fun ExpandedLyrics(
    song: Song,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    actions: LyricsActions?,
    colors: LyricsColors,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onCollapse)
    LyricsSurfaceTheme(colors) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.lg, top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PressIconButton(onClick = onCollapse) {
                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.lyrics_cd_close))
                }
                Spacer(modifier = Modifier.width(Spacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CompositionLocalProvider(LocalLyricsLook provides LyricsLook(alignStart = true)) {
                SongLyricsView(
                    songId = song.id,
                    lyrics = lyrics,
                    position = position,
                    isPlaying = isPlaying,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f),
                    actions = actions,
                    searchQuery = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
                    song = song,
                    onPlayPause = onPlayPause,
                )
            }
            LyricsTransport(isPlaying, onPlayPause, onPrevious, onNext)
        }
    }
}

/** Previous, play / pause and next under the full lyrics. */
@Composable
private fun LyricsTransport(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val playPauseDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressIconButton(onClick = onPrevious, nudge = -1) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous_track),
                modifier = Modifier.size(IconSize.large),
            )
        }
        PressIconButton(
            onClick = onPlayPause,
            modifier = Modifier.semantics { contentDescription = playPauseDescription },
        ) {
            SwapIcon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { icon ->
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(IconSize.large))
            }
        }
        PressIconButton(onClick = onNext, nudge = 1) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next_track),
                modifier = Modifier.size(IconSize.large),
            )
        }
    }
}
