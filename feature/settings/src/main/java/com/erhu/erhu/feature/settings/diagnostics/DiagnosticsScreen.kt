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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.erhu.erhu.core.common.logging.CrashLogEntry
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsNavigationRow
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsPanel
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.core.common.R as CommonR

@Composable
@Suppress("LongMethod")
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenIntroduction: (() -> Unit)? = null,
    onOpenAnnouncements: (() -> Unit)? = null,
) {
    val crashLogs by viewModel.crashLogs.collectAsState()
    val uriHandler = LocalUriHandler.current

    SettingsPage(
        title = title,
        navigation = TopBarNavigation.Detail(onBack),
        modifier = modifier,
        actions = {
            if (crashLogs.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(CommonR.string.diagnostics_cd_clear_logs),
                    )
                }
            }
        },
    ) {
        if (onOpenIntroduction != null) {
            SettingsSection(title = stringResource(CommonR.string.diagnostics_about_section)) {
                SettingsGroup {
                    row {
                        SettingsNavigationRow(
                            title = stringResource(CommonR.string.diagnostics_intro_title),
                            summary = stringResource(CommonR.string.diagnostics_intro_summary),
                            onClick = onOpenIntroduction,
                        )
                    }
                }
            }
        }
        if (onOpenAnnouncements != null) {
            SettingsSection(title = stringResource(CommonR.string.diagnostics_announcements_section)) {
                SettingsGroup {
                    row {
                        SettingsNavigationRow(
                            title = stringResource(CommonR.string.diagnostics_announcements_section),
                            summary = stringResource(CommonR.string.diagnostics_announcements_summary),
                            onClick = onOpenAnnouncements,
                        )
                    }
                }
            }
        }
        SettingsSection(title = stringResource(CommonR.string.diagnostics_updates_section)) {
            UpdatePanel(viewModel)
        }

        SettingsSection(title = stringResource(CommonR.string.diagnostics_legal_section)) {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = stringResource(CommonR.string.diagnostics_privacy_title),
                        summary = stringResource(CommonR.string.diagnostics_privacy_summary),
                        onClick = {
                            uriHandler.openUri("https://github.com/lochanpthakke/erhu/blob/master/docs/privacy-policy.md")
                        },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(CommonR.string.diagnostics_crash_logs_section)) {
            if (crashLogs.isEmpty()) {
                SettingsPanel {
                    Text(
                        text = stringResource(CommonR.string.diagnostics_crash_logs_empty),
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
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(CommonR.string.diagnostics_cd_copy_trace),
                )
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
