package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel

/** Tonal strength of the selected item's pill: a tint, not a filled block. */
private const val SELECTED_PILL_ALPHA = 0.16f

/**
 * Floating, icon-first navigation between the library sections. Placed by
 * [LibraryBottomControls] in the lowest layer of the shell's bottom chrome,
 * directly under the mini player, and shares its shape and material (borderless glass under a glass
 * theme, the theme's surface card otherwise).
 */
@Composable
fun LibraryNavigationBar(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = EmPee3Glass.isActive
    val shape = BottomChrome.shape()
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .glassPanel(shape, strong = true, elevated = false, outlined = false),
        shape = shape,
        color = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (glass) 0.dp else BottomChrome.elevation,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = Spacing.xs),
        ) {
            LibrarySection.entries.forEach { section ->
                LibraryNavigationItem(
                    section = section,
                    selected = section == selected,
                    onClick = { onSelect(section) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LibraryNavigationItem(
    section: LibrarySection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val pillColor by animateColorAsState(
        targetValue = accent.copy(alpha = if (selected) SELECTED_PILL_ALPHA else 0f),
        label = "libraryNavPill",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "libraryNavContent",
    )

    // The whole column is the touch target (at least 48 dp tall); the pill only marks selection.
    Column(
        modifier =
            modifier
                .clip(Radius.mediumShape)
                .selectable(selected = selected, onClick = onClick, role = Role.Tab)
                .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(Radius.pillShape)
                    .background(pillColor)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.standard),
            )
        }
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
