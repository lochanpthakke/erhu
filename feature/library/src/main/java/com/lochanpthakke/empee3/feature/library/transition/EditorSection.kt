package com.lochanpthakke.empee3.feature.library.transition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.model.EqMove
import com.lochanpthakke.empee3.core.model.TransitionSpec
import com.lochanpthakke.empee3.core.model.VolumeShape
import com.lochanpthakke.empee3.core.playback.transition.PreviewState
import com.lochanpthakke.empee3.feature.library.formatTimestamp

// Building blocks of the transition editor (see TransitionEditorScreen).

/** The editor's option sections, in pager order. */
internal enum class EditorSection(
    val title: String,
    val color: Color,
) {
    VOLUME("Volume", AutomationColors.volume),
    EQ("EQ", AutomationColors.eq),
    FILTER("Filter", AutomationColors.filter),
}

@Composable
internal fun TrackRow(
    track: StudioTrack,
    role: String,
    cue: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        SongArtwork(track.song, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentDescription = null)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(track.song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                track.song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            cue?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(formatTimestamp(track.song.durationMs), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun PreviewButton(
    state: PreviewState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state is PreviewState.Playing
    // Just the symbol, no container: it sits in the gap between the two waveforms.
    IconButton(onClick = onToggle, modifier = modifier.size(PREVIEW_TOUCH)) {
        Icon(
            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Stop preview" else "Preview transition (your queue is not changed)",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(PREVIEW_ICON),
        )
    }
}

private val PREVIEW_TOUCH = 56.dp
private val PREVIEW_ICON = 40.dp

@Composable
internal fun DurationPill(
    durationMs: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Text(TransitionSpec.durationLabel(durationMs), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Change duration")
        }
    }
}

internal fun sectionValue(
    section: EditorSection,
    spec: TransitionSpec?,
): String {
    if (spec == null) return "None"
    return when (section) {
        EditorSection.VOLUME -> VolumeShape.of(spec)?.label ?: "Custom curve"
        EditorSection.EQ -> EqMove.of(spec)?.label ?: "Bass swap at ${percent(spec.bassSwapAt)} %"
        EditorSection.FILTER -> spec.filter.label
    }
}

@Composable
internal fun SectionRow(
    section: EditorSection,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Change ${section.title}", onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        ColorBar(section.color)
        Spacer(Modifier.width(Spacing.md))
        Text(section.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(Spacing.lg))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Effects, looping and beatmatching are listed so their absence is clear, never offered as working. */
@Composable
internal fun SectionRowUnavailable() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    ) {
        ColorBar(AutomationColors.unavailable)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                "Effects, looping, beatmatching",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Not available: the audio engine has no echo, reverb or loop stage, and your library has no BPM or beat data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ColorBar(color: Color) {
    Box(
        Modifier
            .size(width = 4.dp, height = 22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/** Bold section title used by the option sheet pages, with an optional action on the right. */
@Composable
internal fun SheetTitle(
    section: EditorSection,
    action: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        ColorBar(section.color)
        Spacer(Modifier.width(Spacing.md))
        Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
