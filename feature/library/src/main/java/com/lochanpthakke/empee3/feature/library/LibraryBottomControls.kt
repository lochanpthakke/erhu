package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel

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
        LibraryNavigationBar(selected = selected, onSelect = onSelect, modifier = Modifier.weight(1f))
        LibrarySearchButton(onClick = onSearch, modifier = Modifier.fillMaxHeight())
    }
}

@Composable
private fun LibrarySearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = EmPee3Glass.isActive
    val shape = BottomChrome.shape()
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .width(ComponentSize.chromeAction)
                .glassPanel(shape, strong = true, elevated = false, outlined = false),
        shape = shape,
        color = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (glass) 0.dp else BottomChrome.elevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search library")
        }
    }
}
