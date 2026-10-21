package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing

/*
 * Rows for the group surfaces in Groups.kt (utility screens such as Auto Fix Tags and the Artwork
 * Fetcher), laid out like Settings rows: icon badge, title and summary, trailing control.
 */

/** Minimum height of a group row: comfortably above the 48 dp touch target. */
private val GROUP_ROW_MIN_HEIGHT = 56.dp

/** Hairline between the rows of a group. */
private const val GROUP_DIVIDER_ALPHA = 0.14f

/** Hairline between the rows or parts of a group, inset from its edges. */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = Spacing.lg),
        thickness = GROUP_EDGE_WIDTH,
        color = MaterialTheme.colorScheme.outline.copy(alpha = GROUP_DIVIDER_ALPHA),
    )
}

/**
 * A tappable row of a group, laid out like a Settings row: optional [icon] badge, [title] with its
 * current value or description as [summary], and a [trailing] icon (a chevron or dropdown arrow).
 */
@Composable
fun GroupRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    trailing: ImageVector? = null,
    enabled: Boolean = true,
) {
    GroupRowLayout(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        if (trailing != null) Icon(imageVector = trailing, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An on/off option of a group. The whole row toggles; the switch only shows the state. */
@Composable
fun GroupToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    GroupRowLayout(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun GroupRowLayout(
    title: String,
    summary: String?,
    icon: ImageVector?,
    enabled: Boolean,
    modifier: Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = GROUP_ROW_MIN_HEIGHT)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon = icon)
            Spacer(modifier = Modifier.width(ICON_TEXT_GAP))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(top = SUMMARY_GAP),
                )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        trailing()
    }
}

/** Gap between a row's icon badge and its text, and between its title and summary. */
private val ICON_TEXT_GAP = 14.dp
private val SUMMARY_GAP = 2.dp

/** Content alpha of a disabled row (Material disabled emphasis). */
private const val DISABLED_CONTENT_ALPHA = 0.38f
