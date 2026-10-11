package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.playback.diagnostics.Fact
import com.lochanpthakke.empee3.core.playback.diagnostics.Provenance

private const val PANEL_ALPHA = 0.55f
private const val DIM_ALPHA = 0.5f

/** A titled block with an optional "what does this mean" explanation, on one rounded surface. */
@Composable
internal fun DetailSection(
    title: String,
    explanation: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var explain by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.lg)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (explanation != null) {
                IconButton(onClick = { explain = !explain }) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = if (explain) "Hide explanation" else "What does this mean?",
                    )
                }
            }
        }
        if (explain && explanation != null) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(EmPee3Glass.chromeColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PANEL_ALPHA)))
                    .padding(vertical = Spacing.sm),
            content = content,
        )
    }
}

/** A value with its origin; null shows as "Unavailable", never as 0 or a guess. */
@Composable
internal fun DetailRow(
    label: String,
    value: String?,
    source: Provenance? = null,
    note: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            if (value == null) {
                Text(
                    "Unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f, fill = false))
                if (source != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    SourceTag(source)
                }
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DetailRow(
    label: String,
    fact: Fact?,
    fallbackNote: String? = null,
) = DetailRow(label, fact?.value, fact?.source, fact?.note ?: fallbackNote.takeIf { fact == null })

/** Small outlined tag naming where a value comes from. */
@Composable
internal fun SourceTag(source: Provenance) {
    Text(
        source.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = DIM_ALPHA), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Plain explanatory text inside a section. */
@Composable
internal fun DetailNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    )
}

/** One step of the signal chain: a status dot, a connector to the next step, title, state and detail. */
@Composable
internal fun ChainStep(
    title: String,
    state: String,
    active: Boolean,
    detail: String,
    isLast: Boolean,
    appControlled: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    val dot = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (active) dot else Color.Transparent)
                    .border(2.dp, dot, CircleShape),
            )
            if (!isLast) Box(Modifier.width(2.dp).height(64.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = DIM_ALPHA)))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f).padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    state,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (appControlled) "App · " else "System · ") + detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
