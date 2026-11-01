package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.glassPanel

/**
 * The one top bar used by every screen. It owns the status-bar inset (Material
 * TopAppBar default), which is why screens and the shell must not add it again.
 *
 * With the Glass Effect on and [glass] true, the bar is the shared glass
 * material, frosting the screen content that scrolls underneath it
 * ([AppScaffold] gives the top bar its screen's backdrop). [glass] = false keeps
 * a plain [containerColor] (e.g. transparent over Now Playing's artwork).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    navigation: TopBarNavigation,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    glass: Boolean = true,
    containerColor: Color =
        if (glass) {
            ErhuGlass.containerColor(MaterialTheme.colorScheme.surface)
        } else {
            ErhuGlass.chromeColor(MaterialTheme.colorScheme.surface)
        },
) {
    TopAppBar(
        title = title,
        modifier = if (glass) modifier.glassPanel(RectangleShape, elevated = false, outlined = false, bar = true) else modifier,
        navigationIcon = {
            when (navigation) {
                is TopBarNavigation.TopLevel ->
                    IconButton(onClick = navigation.onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Open navigation drawer")
                    }
                is TopBarNavigation.Detail ->
                    IconButton(onClick = navigation.onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
    )
}

/** Convenience overload for the common plain-text title. */
@Composable
fun AppTopBar(
    navigation: TopBarNavigation,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    glass: Boolean = true,
    containerColor: Color =
        if (glass) {
            ErhuGlass.containerColor(MaterialTheme.colorScheme.surface)
        } else {
            ErhuGlass.chromeColor(MaterialTheme.colorScheme.surface)
        },
) {
    AppTopBar(
        navigation = navigation,
        title = { Text(text = title, style = TextStyles.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        actions = actions,
        glass = glass,
        containerColor = containerColor,
    )
}
