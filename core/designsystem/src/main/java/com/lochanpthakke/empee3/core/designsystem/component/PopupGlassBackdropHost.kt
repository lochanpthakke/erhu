package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.lochanpthakke.empee3.core.designsystem.theme.LocalPopupGlassBackdrop
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop

/** Separate-window hosts provide their own scene; child native popups are excluded from capture. */
@Composable
fun PopupGlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = rememberGlassBackdrop("popup-host", screenCoordinates = true)
    CompositionLocalProvider(LocalPopupGlassBackdrop provides backdrop) {
        Box(Modifier.glassBackdropSource(backdrop, enabled = backdrop.consumers > 0).then(modifier)) { content() }
    }
}
