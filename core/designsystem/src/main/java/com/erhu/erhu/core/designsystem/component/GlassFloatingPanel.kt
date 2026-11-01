package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erhu.erhu.core.designsystem.theme.PopupGlassSurface

/** Content-sized, bounded native window; only vertical scrolling competes with its controls. */
@Composable
fun GlassFloatingPanel(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 64).coerceAtLeast(160).dp
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PopupGlassSurface(Modifier.widthIn(max = 480.dp).fillMaxWidth(0.9f)) {
            Column(
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(24.dp),
                content = content,
            )
        }
    }
}
