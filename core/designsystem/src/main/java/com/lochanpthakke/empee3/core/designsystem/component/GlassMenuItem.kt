package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class GlassMenuConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String = "Remove",
)

internal class GlassMenuInteraction(
    val enabled: Boolean,
    val select: (GlassMenuConfirmation?, () -> Unit) -> Unit,
)

internal val LocalGlassMenuInteraction = staticCompositionLocalOf { GlassMenuInteraction(false) { _, _ -> } }

/** All action rows share touch targets, typography, icons, disabled/error colors and close-before-action. */
@Composable
fun GlassMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    secondaryText: String? = null,
    confirmation: GlassMenuConfirmation? = null,
) {
    val interaction = LocalGlassMenuInteraction.current
    val foreground =
        if (destructive) menuDestructiveColor(MaterialTheme.colorScheme.error, LocalContentColor.current) else LocalContentColor.current
    DropdownMenuItem(
        text = {
            Column {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                secondaryText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = 0.9f)) }
            }
        },
        onClick = { interaction.select(confirmation, onClick) },
        modifier = modifier.heightIn(min = 52.dp),
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        enabled = enabled && interaction.enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        colors =
            MenuDefaults.itemColors(
                textColor = foreground,
                leadingIconColor = foreground,
                disabledTextColor = foreground.copy(alpha = 0.45f),
                disabledLeadingIconColor = foreground.copy(alpha = 0.45f),
            ),
    )
}

@Composable
fun GlassMenuSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.9f),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).semantics { heading() },
    )
}

@Composable
fun GlassMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        color = LocalContentColor.current.copy(alpha = 0.14f),
    )
}
