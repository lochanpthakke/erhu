package com.erhu.erhu.feature.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.polishedSurface

/** Opaque polished backing: artwork and text never share a filtered backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueGlossSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val surface = Color(0xFF111111)
    val scheme =
        MaterialTheme.colorScheme.copy(
            surface = surface,
            surfaceContainer = Color(0xFF1B1B1B),
            surfaceContainerHigh = Color(0xFF232323),
            onSurface = Color(0xFFF2F3F5),
            onSurfaceVariant = Color(0xFFBAC0C8),
            outline = Color(0xFF737A84),
            outlineVariant = Color(0xFF3C424B),
        )
    MaterialTheme(colorScheme = scheme) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.shadow(16.dp, shape).polishedSurface(shape),
            content = content,
        )
    }
}
