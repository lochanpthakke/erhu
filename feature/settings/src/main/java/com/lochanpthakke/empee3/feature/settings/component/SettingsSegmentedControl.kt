package com.lochanpthakke.empee3.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing

/** Height of a [SettingsSegmentedControl]: a 40 dp pill inside a 48 dp touch row. */
private val SEGMENT_HEIGHT = 40.dp

/** Track behind the segments. */
private const val SEGMENT_TRACK_ALPHA = 0.08f

/**
 * Compact single-choice control whose options share the width equally, so many short options
 * (equalizer bands 1–8) fit on a phone. The selected option is a filled pill in the accent.
 * [describe] gives each option's accessibility label.
 */
@Composable
fun SettingsSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    describe: (Int) -> String = { options[it] },
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = SEGMENT_TRACK_ALPHA))
                .padding(Spacing.xs)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = SEGMENT_HEIGHT)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = alpha) else Color.Transparent)
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                        .semantics { contentDescription = describe(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        },
                )
            }
        }
    }
}
