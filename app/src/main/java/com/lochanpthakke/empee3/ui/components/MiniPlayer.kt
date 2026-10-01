package com.lochanpthakke.empee3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import com.lochanpthakke.empee3.feature.library.collectPlaybackPosition
import com.lochanpthakke.empee3.feature.library.collectStablePlaybackState

@Composable
@Suppress("LongMethod")
fun MiniPlayer(
    playbackRepository: PlaybackRepository,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stable state drives composition; the position is only read inside the
    // progress lambda (draw phase), so position ticks redraw the bar without
    // recomposing the mini player.
    val playbackState by playbackRepository.collectStablePlaybackState()
    val position = playbackRepository.collectPlaybackPosition()
    val song = playbackState.currentSong ?: return
    val durationMs = playbackState.durationMs

    val isPlaying = playbackState.status == PlaybackStatus.PLAYING
    val glass = EmPee3Glass.isActive
    val shape = if (glass) RoundedCornerShape(EmPee3Theme.shapes.miniPlayerCornerRadiusDp.dp) else Radius.mediumShape

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                // Floating card: a tighter outer margin than the screen gutter is intentional.
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                // Glass: a borderless material layer over the content (tone + sheen, no stroke or shadow).
                .glassPanel(shape, strong = true, elevated = false, outlined = false)
                .clickable(onClick = onMiniPlayerClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (glass) 0.dp else 6.dp),
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
                SongArtwork(
                    song = song,
                    contentDescription = "Album artwork",
                    modifier =
                        Modifier
                            .size(ArtworkSize.miniPlayer)
                            .clip(Radius.smallShape),
                )

                Spacer(modifier = Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = { playbackRepository.skipPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
                    )
                }

                IconButton(
                    onClick = {
                        if (isPlaying) {
                            playbackRepository.pause()
                        } else {
                            playbackRepository.play()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }

                IconButton(onClick = { playbackRepository.skipNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                    )
                }
            }
        }
    }
}
