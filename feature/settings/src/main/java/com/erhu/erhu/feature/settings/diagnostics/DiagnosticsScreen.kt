package com.erhu.erhu.feature.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.erhu.erhu.core.common.logging.CrashLogEntry
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsPanel
import com.erhu.erhu.feature.settings.component.SettingsSection

@Composable
@Suppress("LongMethod")
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenIntroduction: (() -> Unit)? = null,
) {
    val crashLogs by viewModel.crashLogs.collectAsState()

    SettingsPage(
        title = title,
        navigation = TopBarNavigation.Detail(onBack),
        modifier = modifier,
        actions = {
            if (crashLogs.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear crash logs")
                }
            }
        },
    ) {
        if (onOpenIntroduction != null) {
            SettingsSection(title = "About") {
                SettingsGroup {
                    row {
                        com.erhu.erhu.feature.settings.component.SettingsNavigationRow(
                            title = "Introduction",
                            summary = "Explore erhu and music access setup.",
                            onClick = onOpenIntroduction,
                        )
                    }
                }
            }
        }
        SettingsSection(title = "Updates") {
            UpdatePanel(viewModel)
        }

        SettingsSection(title = "Local crash log history") {
            if (crashLogs.isEmpty()) {
                SettingsPanel {
                    Text(
                        text = "No crash logs recorded. App has been running stably!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SettingsGroup {
                    crashLogs.forEach { entry ->
                        row {
                            CrashLogRow(entry = entry, onCopy = { viewModel.copyLogToClipboard(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogRow(
    entry: CrashLogEntry,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Stack Trace")
            }
        }
        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 10,
            modifier = Modifier.padding(end = Spacing.md),
        )
    }
}
