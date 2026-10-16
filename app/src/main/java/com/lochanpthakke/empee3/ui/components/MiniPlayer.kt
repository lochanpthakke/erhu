package com.lochanpthakke.empee3.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton
import com.lochanpthakke.empee3.core.designsystem.component.collapseHorizontally
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassIndication
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPressEffect
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import com.lochanpthakke.empee3.feature.library.AnimatedTrack
import com.lochanpthakke.empee3.feature.library.LocalPlayerTransition
import com.lochanpthakke.empee3.feature.library.SwapIcon
import com.lochanpthakke.empee3.feature.library.collectPlaybackPosition
import com.lochanpthakke.empee3.feature.library.collectStablePlaybackState
import com.lochanpthakke.empee3.feature.library.playerDrag

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
    val glass = EmPee3Glass.isActive
    val shape = BottomChrome.shape()
    val interactionSource = remember { MutableInteractionSource() }
    // The collapsed representation of the player: it reports where it is (the
    // expanding surface and the travelling artwork start here), fades as the
    // surface grows over it, and can be dragged up to expand.
    val transition = LocalPlayerTransition.current

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                // Floating card; shares margins and shape with the rest of the bottom chrome.
                .padding(horizontal = BottomChrome.horizontalMargin, vertical = BottomChrome.gap)
                // Glass: a borderless material layer over the content (tone + sheen, no stroke or shadow).
                .onGloballyPositioned { coordinates -> transition?.miniBounds = coordinates.boundsInRoot() }
                .graphicsLayer {
                    val p = transition?.progress?.value ?: 0f
                    alpha = (1f - p / MINI_FADE_SPAN).coerceIn(0f, 1f)
                }.then(if (transition != null) Modifier.playerDrag(transition, expandsUpward = true) else Modifier)
                .glassPressEffect(interactionSource, shape)
                .glassPanel(shape, strong = true, elevated = false, outlined = false, optics = 1f, environmentLens = true)
                .clickable(interactionSource = interactionSource, indication = glassIndication(), onClick = onMiniPlayerClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
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
                trackColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
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
                ) { track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongArtwork(
                            song = track,
                            contentDescription = "Album artwork",
                            modifier =
                                Modifier
                                    .onGloballyPositioned { coordinates -> transition?.miniArtworkBounds = coordinates.boundsInRoot() }
                                    // The player's artwork is ONE object: once it starts travelling, the
                                    // travelling copy (same bitmap, same place) is the only one drawn.
                                    .graphicsLayer { alpha = if ((transition?.progress?.value ?: 0f) > 0f) 0f else 1f }
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
                PressIconButton(
                    onClick = { playbackRepository.skipPrevious() },
                    modifier = Modifier.collapseHorizontally(compact),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
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
                            contentDescription = if (isPlaying) "Pause" else "Play",
                        )
                    }
                }

                PressIconButton(
                    onClick = { playbackRepository.skipNext() },
                    modifier = Modifier.collapseHorizontally(compact),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                    )
                }
            }
        }
    }
}

/** The mini player fades out over this part of the expansion (the surface covers it). */
private const val MINI_FADE_SPAN = 0.2f
