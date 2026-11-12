package com.erhu.erhu.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.artwork.SongArtwork
import com.erhu.erhu.core.designsystem.component.BottomChrome
import com.erhu.erhu.core.designsystem.component.PressIconButton
import com.erhu.erhu.core.designsystem.component.collapseHorizontally
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.ComponentSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.glassIndication
import com.erhu.erhu.core.designsystem.theme.glassPressEffect
import com.erhu.erhu.core.designsystem.theme.liquidGlass
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.feature.library.AnimatedTrack
import com.erhu.erhu.feature.library.LocalPlayerTransition
import com.erhu.erhu.feature.library.SwapIcon
import com.erhu.erhu.feature.library.collectPlaybackPosition
import com.erhu.erhu.feature.library.collectStablePlaybackState
import com.erhu.erhu.feature.library.playerDrag

@Composable
@Suppress("LongMethod")
fun MiniPlayer(
    playbackRepository: PlaybackRepository,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: () -> Float = { 0f },
) {
    // Stable state drives composition; the position is only read inside the
    // progress lambda (draw phase), so position ticks redraw the bar without
    // recomposing the mini player.
    val playbackState by playbackRepository.collectStablePlaybackState()
    val position = playbackRepository.collectPlaybackPosition()
    val song = playbackState.currentSong ?: return
    val durationMs = playbackState.durationMs

    val isPlaying =
        playbackState.status == PlaybackStatus.PLAYING ||
            playbackState.status == PlaybackStatus.BUFFERING
    val glass = ErhuGlass.isActive
    val shape = BottomChrome.shape()
    val interactionSource = remember { MutableInteractionSource() }
    // The collapsed representation of the player: it reports where it is (the
    // expanding surface and the travelling artwork start here), fades as the
    // surface grows over it, and can be dragged up to expand.
    val transition = LocalPlayerTransition.current
    val fullyExpanded by remember(transition) { derivedStateOf { transition?.isSettledOpen == true } }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                // Floating card; shares margins and shape with the rest of the bottom chrome.
                .padding(horizontal = BottomChrome.horizontalMargin, vertical = BottomChrome.gap)
                // Share navigation glass and strength, sampling the screen content underneath.
                .onGloballyPositioned { coordinates -> transition?.miniBounds = coordinates.boundsInRoot() }
                .graphicsLayer {
                    val p = transition?.fraction ?: 0f
                    alpha = (1f - p / MINI_FADE_SPAN).coerceIn(0f, 1f)
                }.then(if (transition != null) Modifier.playerDrag(transition, expandsUpward = true) else Modifier)
                .glassPressEffect(interactionSource, shape)
                .liquidGlass(shape)
                .clickable(interactionSource = interactionSource, indication = glassIndication(), onClick = onMiniPlayerClick),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (glass) 0.dp else BottomChrome.elevation),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = {
                    if (durationMs > 0) (position.value.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.miniProgress),
                color = MaterialTheme.colorScheme.primary,
                trackColor = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Artwork and title move together when the track changes.
                AnimatedTrack(
                    song = song,
                    index = playbackState.currentIndex,
                    shuffle = playbackState.shuffleModeEnabled,
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                    // The expanded surface fully covers this representation. Keep its
                    // content current without running a second, invisible page transition.
                    animate = !fullyExpanded,
                ) { track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongArtwork(
                            song = track,
                            contentDescription =
                                androidx.compose.ui.res.stringResource(
                                    com.erhu.erhu.core.common.R.string.cd_album_artwork,
                                ),
                            modifier =
                                Modifier
                                    .onGloballyPositioned { coordinates -> transition?.miniArtworkBounds = coordinates.boundsInRoot() }
                                    // The player's artwork is ONE object: once it starts travelling, the
                                    // travelling copy (same bitmap, same place) is the only one drawn.
                                    .graphicsLayer { alpha = if ((transition?.fraction ?: 0f) > 0f) 0f else 1f }
                                    .size(ArtworkSize.miniPlayer)
                                    .clip(Radius.smallShape),
                        )

                        Spacer(modifier = Modifier.width(Spacing.md))

                        Column {
                            Text(
                                text = track.title,
                                modifier = Modifier.basicMarquee(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                modifier = Modifier.basicMarquee(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // In the compact library chrome the skip buttons fold away; play/pause stays.
                // Media control buttons keep their standard LTR sequence (Previous -> Play -> Next)
                // in all languages, matching system media player conventions.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PressIconButton(
                        onClick = { playbackRepository.skipPrevious() },
                        modifier = Modifier.collapseHorizontally(compact),
                        nudge = -1,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription =
                                androidx.compose.ui.res
                                    .stringResource(com.erhu.erhu.core.common.R.string.cd_previous_track),
                        )
                    }

                    PressIconButton(
                        onClick = {
                            if (isPlaying) {
                                playbackRepository.pause()
                            } else {
                                playbackRepository.play()
                            }
                        },
                    ) {
                        SwapIcon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription =
                                    androidx.compose.ui.res.stringResource(
                                        if (isPlaying) {
                                            com.erhu.erhu.core.common.R.string.cd_pause
                                        } else {
                                            com.erhu.erhu.core.common.R.string.cd_play
                                        },
                                    ),
                            )
                        }
                    }

                    PressIconButton(
                        onClick = { playbackRepository.skipNext() },
                        modifier = Modifier.collapseHorizontally(compact),
                        nudge = 1,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription =
                                androidx.compose.ui.res
                                    .stringResource(com.erhu.erhu.core.common.R.string.cd_next_track),
                        )
                    }
                }
            }
        }
    }
}

/** The mini player fades out over this part of the expansion (the surface covers it). */
private const val MINI_FADE_SPAN = 0.2f
