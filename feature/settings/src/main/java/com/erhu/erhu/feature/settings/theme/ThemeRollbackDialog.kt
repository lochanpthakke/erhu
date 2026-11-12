package com.erhu.erhu.feature.settings.theme

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.common.R as CommonR

@Composable
fun ThemeRollbackDialog(onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.theme_rollback_title)) },
        text = {
            Text(stringResource(CommonR.string.theme_rollback_body))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.action_ok))
            }
        },
    )
}
