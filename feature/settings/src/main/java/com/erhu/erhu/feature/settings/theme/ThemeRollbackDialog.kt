package com.erhu.erhu.feature.settings.theme

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog

@Composable
fun ThemeRollbackDialog(onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme Reverted for Stability") },
        text = {
            Text(
                "erhu encountered an unexpected shutdown while applying your active theme or skin. " +
                    "To ensure app stability, your theme has been safely reset to a working default.",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}
