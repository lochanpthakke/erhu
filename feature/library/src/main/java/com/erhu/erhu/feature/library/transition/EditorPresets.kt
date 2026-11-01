package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.TransitionPreset

// The preset cards and the status line of the transition editor.
@Composable
internal fun PresetCards(
    draft: EditorDraft,
    onChoose: (PresetChoice) -> Unit,
) {
    val autoLabel = if (draft.target is EditorTarget.Collection) "Default" else "Auto"
    val cards =
        buildList {
            if (draft.target !is EditorTarget.Global) add(PresetChoice.Auto to autoLabel)
            add(PresetChoice.Off to "Off")
            TransitionPreset.entries.filter { it != TransitionPreset.CUSTOM }.forEach { add(PresetChoice.Preset(it) to it.label) }
            if (draft.mode == EditorMode.CUSTOM && draft.spec.preset == TransitionPreset.CUSTOM) {
                add(PresetChoice.Preset(TransitionPreset.CUSTOM) to "Custom")
            }
        }
    val selected: PresetChoice =
        when (draft.mode) {
            EditorMode.INHERIT -> PresetChoice.Auto
            EditorMode.OFF -> PresetChoice.Off
            EditorMode.CUSTOM -> PresetChoice.Preset(draft.spec.preset)
        }
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.lg)) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(cards, key = { it.second }) { (choice, label) ->
                PresetCard(label, choice == selected, showSparkle = choice == PresetChoice.Auto) {
                    // Custom is where edits land; tapping it again changes nothing.
                    if (!(choice is PresetChoice.Preset && choice.preset == TransitionPreset.CUSTOM)) onChoose(choice)
                }
            }
        }
    }
}

@Composable
internal fun PresetCard(
    label: String,
    selected: Boolean,
    showSparkle: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(width = 112.dp, height = 56.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (showSparkle) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
internal fun StatusLine(draft: EditorDraft) {
    val text =
        when (draft.mode) {
            EditorMode.INHERIT -> "Follows the level above: ${specSummary(draft.inherited)}. Change anything below to make it its own."
            EditorMode.OFF -> "Off here, even if a more general setting has a crossfade: the tracks play back to back."
            EditorMode.CUSTOM -> "Its own transition: ${specSummary(draft.spec)}."
        }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
}
