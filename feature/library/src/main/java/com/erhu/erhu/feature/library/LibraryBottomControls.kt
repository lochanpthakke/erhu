package com.erhu.erhu.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.BottomChrome
import com.erhu.erhu.core.designsystem.foundation.ComponentSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.glassIndication
import com.erhu.erhu.core.designsystem.theme.glassPressEffect
import com.erhu.erhu.core.designsystem.theme.liquidGlass

/**
 * The library's bottom control row: the section navigation and, beside it, a
 * separate Search action. Both use the bottom-chrome material and share one
 * height, so they read as one floating control system under the mini player.
 */
@Composable
fun LibraryBottomControls(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searching: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = BottomChrome.horizontalMargin, end = BottomChrome.horizontalMargin, bottom = Spacing.sm)
                // Search matches the navigation's height, whatever the font scale.
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LibraryNavigationBar(selected = selected, onSelect = onSelect, searching = searching, modifier = Modifier.weight(1f))
        LibrarySearchButton(onClick = onSearch, active = searching, modifier = Modifier.fillMaxHeight())
    }
}

@Composable
internal fun LibrarySearchButton(
    onClick: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    // Match the selected section ink while searching; glass stays neutral.
    val iconColor by animateColorAsState(
        targetValue =
            if (ErhuGlass.isActive) {
                if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            },
        animationSpec = Motion.fade(),
        label = "searchButtonIcon",
    )
    val glass = ErhuGlass.isActive
    val shape = BottomChrome.shape()
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier =
            modifier
                .width(ComponentSize.chromeAction)
                .glassPressEffect(interactionSource, shape)
                .liquidGlass(shape),
        shape = shape,
        color = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (glass) 0.dp else BottomChrome.elevation,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = glassIndication(),
                        role = Role.Button,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search library", tint = iconColor)
        }
    }
}
