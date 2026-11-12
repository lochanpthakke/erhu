package com.erhu.erhu.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.playback.diagnostics.Fact
import com.erhu.erhu.core.playback.diagnostics.Provenance

/** Opaque tonal groups keep technical text readable independently of the player behind them. */
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
                style = TextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (explanation != null) {
                IconButton(onClick = { explain = !explain }) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription =
                            stringResource(
                                if (explain) R.string.audio_details_cd_hide_explanation else R.string.audio_details_cd_show_explanation,
                            ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), content = content)
        }
    }
}

/** Attribution sits below the value rather than competing for a third column. */
@Composable
internal fun DetailRow(
    label: String,
    value: String?,
    source: Provenance? = null,
    note: String? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        val stacked = maxWidth < 280.dp || fontScale > 1.3f
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (stacked) {
                DetailLabel(label)
                DetailValue(value, source)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(Modifier.weight(0.35f)) { DetailLabel(label) }
                    Box(Modifier.weight(0.65f)) { DetailValue(value, source) }
                }
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLabel(label: String) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DetailValue(
    value: String?,
    source: Provenance?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            value ?: stringResource(R.string.audio_details_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
            color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value != null && source != null) SourceTag(source)
    }
}

@Composable
internal fun DetailRow(
    label: String,
    fact: Fact?,
    fallbackNote: String? = null,
) = DetailRow(label, fact?.value, fact?.source, fact?.note ?: fallbackNote.takeIf { fact == null })

/** A quiet source badge, kept separate from the technical value. */
@Composable
internal fun SourceTag(source: Provenance) {
    Surface(
        shape = Radius.smallShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            stringResource(source.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun Provenance.labelRes(): Int =
    when (this) {
        Provenance.FILE_HEADER -> R.string.audio_details_provenance_file_header
        Provenance.CONTAINER -> R.string.audio_details_provenance_container
        Provenance.LIBRARY -> R.string.audio_details_provenance_library
        Provenance.PLAYER -> R.string.audio_details_provenance_player
        Provenance.PLATFORM -> R.string.audio_details_provenance_platform
        Provenance.MEASURED -> R.string.audio_details_provenance_measured
        Provenance.ESTIMATE -> R.string.audio_details_provenance_estimate
    }

@Composable
internal fun DetailNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    )
}

/** The connector follows actual row height, including wrapped descriptions and actions. */
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
    val colors = MaterialTheme.colorScheme
    val dot = if (active) colors.primary else colors.onSurfaceVariant
    val connector = colors.onSurfaceVariant.copy(alpha = 0.3f)
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).drawBehind {
                if (!isLast) {
                    val connectorX = if (layoutDirection == LayoutDirection.Rtl) size.width - 6.dp.toPx() else 6.dp.toPx()
                    drawLine(
                        connector,
                        Offset(connectorX, 18.dp.toPx()),
                        Offset(connectorX, size.height + 6.dp.toPx()),
                        1.dp.toPx(),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(if (active) dot else Color.Transparent)
                .border(2.dp, dot, CircleShape),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Surface(
                shape = Radius.smallShape,
                color = if (active) colors.primaryContainer else colors.surface,
                contentColor = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
            ) {
                Text(
                    state,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
            Text(
                stringResource(if (appControlled) R.string.audio_details_prefix_app else R.string.audio_details_prefix_system, detail),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
