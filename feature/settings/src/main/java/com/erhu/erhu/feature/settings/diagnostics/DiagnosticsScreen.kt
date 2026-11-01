package com.erhu.erhu.feature.settings.diagnostics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.logging.CrashLogEntry
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsIcon
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
) {
    val context = LocalContext.current
    val crashLogs by viewModel.crashLogs.collectAsState()
    val latestRelease by viewModel.latestRelease.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

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
        SettingsSection(title = "Updates") {
            SettingsPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(Icons.Default.SystemUpdate)
                    Spacer(modifier = Modifier.width(Spacing.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Update Checker", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Current Version: 1.0.0 (GitHub Release Monthly Auto-Check)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                val release = latestRelease
                if (release != null) {
                    Text(
                        text = "New Release Available: ${release.tagName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = release.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(IconSize.small))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Download Latest APK")
                    }
                } else {
                    FilledTonalButton(
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(IconSize.small), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(IconSize.small))
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(if (isCheckingUpdate) "Checking GitHub..." else "Check for Updates Now")
                    }
                }
            }
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
