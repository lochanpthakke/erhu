package com.erhu.erhu.feature.settings.diagnostics

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.network.update.UpdateCheck
import com.erhu.erhu.core.network.update.UpdateDownloadState
import com.erhu.erhu.feature.settings.component.SettingsIcon
import com.erhu.erhu.feature.settings.component.SettingsPanel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun UpdatePanel(viewModel: DiagnosticsViewModel) {
    val context = LocalContext.current
    val check by viewModel.update.collectAsState()
    val error by viewModel.updateError.collectAsState()
    val checking by viewModel.isCheckingUpdate.collectAsState()
    val transfer by viewModel.downloadState.collectAsState()
    val installMessage by viewModel.installationMessage.collectAsState()
    val launch by viewModel.launchInstaller.collectAsState()
    val automatic by viewModel.automaticCheckSummary.collectAsState()
    var autoPrompt by rememberSaveable { mutableStateOf(false) }
    var permissionNeeded by rememberSaveable { mutableStateOf(false) }
    val installer =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.installMessage(installationResultMessage(result.resultCode))
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.packageManager.canRequestPackageInstalls()) {
                permissionNeeded = false
                viewModel.requestInstall()
            } else {
                permissionNeeded = true
                viewModel.installMessage(
                    "Installation permission was not granted. " +
                        "Allow updates from erhu in Android settings, then retry.",
                )
            }
        }
    LaunchedEffect(transfer, autoPrompt) {
        if (transfer is UpdateDownloadState.Ready && autoPrompt) {
            autoPrompt = false
            viewModel.requestInstall()
        }
    }
    LaunchedEffect(launch) {
        val ready = launch ?: return@LaunchedEffect
        viewModel.installerLaunched()
        when (prepareUpdateInstall(ready.file.isFile, context.packageManager.canRequestPackageInstalls())) {
            InstallPreparation.MISSING_FILE -> viewModel.installMessage("The downloaded APK was removed from cache. Download it again.")
            InstallPreparation.PERMISSION_REQUIRED -> {
                permissionNeeded = true
                viewModel.installMessage(
                    "Android needs permission to install updates from erhu. Installation always requires your confirmation.",
                )
            }
            InstallPreparation.READY ->
                try {
                    permissionNeeded = false
                    installer.launch(updateInstallerIntent(context, ready))
                } catch (_: Exception) {
                    viewModel.installMessage("Android could not open the package installer. Check installation permission and retry.")
                }
        }
    }

    val busy = transfer is UpdateDownloadState.Downloading || transfer == UpdateDownloadState.Verifying
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(Icons.Default.SystemUpdate)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text("App Update Checker", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Current Version: ${viewModel.installedVersion.versionName} (${viewModel.installedVersion.versionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "GitHub Releases (including beta) · Monthly automatic check",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        automatic?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Spacing.sm))
        }
        if (checking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(IconSize.small))
                Spacer(Modifier.width(Spacing.sm))
                Text("Checking GitHub…")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (check == UpdateCheck.UpToDate) Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium)
        ((check as? UpdateCheck.Available) ?: (transfer as? UpdateDownloadState.Ready)?.update)?.let { available ->
            Text(
                "New version: ${available.release.tagName}" + (available.versionCode?.let { " ($it)" } ?: ""),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            val date =
                available.release.publishedAt?.let { value ->
                    runCatching {
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(value))
                    }.getOrDefault(value)
                }
            Text("Released: ${date ?: "Date unavailable"}", style = MaterialTheme.typography.bodySmall)
            Text(
                available.release.body?.takeIf(String::isNotBlank) ?: "No release notes provided.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!busy && transfer !is UpdateDownloadState.Ready) {
                Button(onClick = {
                    autoPrompt = true
                    permissionNeeded = false
                    viewModel.downloadUpdate()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (transfer is UpdateDownloadState.Failed ||
                            transfer == UpdateDownloadState.Cancelled
                        ) {
                            "Retry Download"
                        } else {
                            "Download Update"
                        },
                    )
                }
            }
        }
        when (val state = transfer) {
            is UpdateDownloadState.Downloading -> {
                val total = state.total
                if (total !=
                    null
                ) {
                    LinearProgressIndicator(
                        progress = { (state.bytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    if (total !=
                        null
                    ) {
                        "${state.bytes * 100 / total}% · ${state.bytes / 1024} / ${total / 1024} KB"
                    } else {
                        "${state.bytes / 1024} KB downloaded"
                    },
                )
                TextButton(onClick = {
                    autoPrompt = false
                    viewModel.cancelDownload()
                }) { Text("Cancel Download") }
            }
            UpdateDownloadState.Verifying -> Text("Verifying APK identity, version and signing certificate…")
            is UpdateDownloadState.Ready -> {
                Text("APK verified and ready to install.")
                Button(onClick = viewModel::requestInstall, modifier = Modifier.fillMaxWidth()) { Text("Install Update") }
            }
            is UpdateDownloadState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            UpdateDownloadState.Cancelled -> Text("Download cancelled.")
            UpdateDownloadState.Idle -> Unit
        }
        installMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (permissionNeeded) {
            FilledTonalButton(onClick = {
                try {
                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } catch (
                    _: Exception,
                ) {
                    viewModel.installMessage("Android installation settings are unavailable on this device.")
                }
            }) { Text("Allow Installation") }
        }
        Spacer(Modifier.height(Spacing.sm))
        FilledTonalButton(onClick = viewModel::checkForUpdates, enabled = !checking && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (checking) {
                    "Checking GitHub…"
                } else if (error != null) {
                    "Retry Update Check"
                } else {
                    "Check for Updates Now"
                },
            )
        }
    }
}
