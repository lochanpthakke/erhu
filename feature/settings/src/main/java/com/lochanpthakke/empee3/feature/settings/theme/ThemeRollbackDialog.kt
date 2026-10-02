package com.lochanpthakke.empee3.feature.settings.theme

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog

@Composable
fun ThemeRollbackDialog(onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme Reverted for Stability") },
        text = {
            Text(
                "empee3 encountered an unexpected shutdown while applying your active theme or skin. " +
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
