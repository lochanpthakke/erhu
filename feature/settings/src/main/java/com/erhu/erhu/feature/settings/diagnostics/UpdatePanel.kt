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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.network.update.AutomaticUpdateRecord
import com.erhu.erhu.core.network.update.UpdateCheck
import com.erhu.erhu.core.network.update.UpdateDownloadState
import com.erhu.erhu.feature.settings.component.SettingsIcon
import com.erhu.erhu.feature.settings.component.SettingsPanel
import java.time.Instant
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun UpdatePanel(viewModel: DiagnosticsViewModel) {
    val context = LocalContext.current
    val locale = rememberLocale(context)
    val check by viewModel.update.collectAsState()
    val error by viewModel.updateError.collectAsState()
    val checking by viewModel.isCheckingUpdate.collectAsState()
    val transfer by viewModel.downloadState.collectAsState()
    val installMessage by viewModel.installationMessage.collectAsState()
    val launch by viewModel.launchInstaller.collectAsState()
    val automatic by viewModel.automaticCheckSummary.collectAsState()
    val automaticRecord by viewModel.automaticCheckRecord.collectAsState()
    var autoPrompt by rememberSaveable { mutableStateOf(false) }
    var permissionNeeded by rememberSaveable { mutableStateOf(false) }
    val installer =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.installMessage(installationResultMessageRes(result.resultCode))
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.packageManager.canRequestPackageInstalls()) {
                permissionNeeded = false
                viewModel.requestInstall()
            } else {
                permissionNeeded = true
                viewModel.installMessage(CommonR.string.update_install_permission_denied)
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
            InstallPreparation.MISSING_FILE -> viewModel.installMessage(CommonR.string.update_install_missing_file)
            InstallPreparation.PERMISSION_REQUIRED -> {
                permissionNeeded = true
                viewModel.installMessage(CommonR.string.update_install_permission_needed)
            }
            InstallPreparation.READY ->
                try {
                    permissionNeeded = false
                    installer.launch(updateInstallerIntent(context, ready))
                } catch (_: Exception) {
                    viewModel.installMessage(CommonR.string.update_install_installer_unavailable)
                }
        }
    }

    val busy = transfer is UpdateDownloadState.Downloading || transfer == UpdateDownloadState.Verifying
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(Icons.Default.SystemUpdate)
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(CommonR.string.update_panel_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        CommonR.string.update_current_version,
                        viewModel.installedVersion.versionName,
                        LocaleFormatters.formatInteger(viewModel.installedVersion.versionCode, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(CommonR.string.update_release_channel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        val automaticText =
            automaticRecord?.let { record ->
                val datePart = LocaleFormatters.formatMediumDate(record.checkedAtMillis, locale)
                val timePart = LocaleFormatters.formatShortTime(record.checkedAtMillis, locale)
                val detail =
                    when (record) {
                        is AutomaticUpdateRecord.UpToDate ->
                            stringResource(CommonR.string.update_up_to_date)
                        is AutomaticUpdateRecord.Available ->
                            stringResource(CommonR.string.update_auto_available_message, record.tagName)
                        is AutomaticUpdateRecord.Error -> record.message
                    }
                stringResource(CommonR.string.update_auto_check_summary, "$datePart $timePart", detail)
            } ?: automatic
        automaticText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Spacing.sm))
        }
        if (checking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(IconSize.small))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(CommonR.string.update_checking_github))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (check == UpdateCheck.UpToDate) {
            Text(stringResource(CommonR.string.update_up_to_date), style = MaterialTheme.typography.bodyMedium)
        }
        ((check as? UpdateCheck.Available) ?: (transfer as? UpdateDownloadState.Ready)?.update)?.let { available ->
            val versionHeader =
                available.versionCode?.let { code ->
                    stringResource(
                        CommonR.string.update_new_version_with_code,
                        available.release.tagName,
                        LocaleFormatters.formatInteger(code, locale),
                    )
                } ?: stringResource(CommonR.string.update_new_version, available.release.tagName)
            Text(
                versionHeader,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            val date =
                available.release.publishedAt?.let { value ->
                    runCatching { LocaleFormatters.formatMediumDate(Instant.parse(value), locale) }.getOrDefault(value)
                }
            Text(
                stringResource(
                    CommonR.string.update_released_date,
                    date ?: stringResource(CommonR.string.update_date_unavailable),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                available.release.body?.takeIf(String::isNotBlank) ?: stringResource(CommonR.string.update_no_release_notes),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!busy && transfer !is UpdateDownloadState.Ready) {
                Button(onClick = {
                    autoPrompt = true
                    permissionNeeded = false
                    viewModel.downloadUpdate()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (transfer is UpdateDownloadState.Failed ||
                                transfer == UpdateDownloadState.Cancelled
                            ) {
                                CommonR.string.update_retry_download
                            } else {
                                CommonR.string.update_download_action
                            },
                        ),
                    )
                }
            }
        }
        when (val state = transfer) {
            is UpdateDownloadState.Downloading -> {
                val total = state.total
                if (total != null && total > 0L) {
                    LinearProgressIndicator(
                        progress = { (state.bytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    if (total != null && total > 0L) {
                        stringResource(
                            CommonR.string.update_download_progress_total,
                            LocaleFormatters.formatPercent(state.bytes.toDouble() / total, locale),
                            LocaleFormatters.formatFileSize(context, state.bytes, locale),
                            LocaleFormatters.formatFileSize(context, total, locale),
                        )
                    } else {
                        stringResource(
                            CommonR.string.update_download_progress_unknown,
                            LocaleFormatters.formatFileSize(context, state.bytes, locale),
                        )
                    },
                )
                TextButton(onClick = {
                    autoPrompt = false
                    viewModel.cancelDownload()
                }) { Text(stringResource(CommonR.string.update_cancel_download)) }
            }
            UpdateDownloadState.Verifying -> Text(stringResource(CommonR.string.update_verifying_apk))
            is UpdateDownloadState.Ready -> {
                Text(stringResource(CommonR.string.update_ready_to_install))
                Button(onClick = viewModel::requestInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CommonR.string.update_install_action))
                }
            }
            is UpdateDownloadState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            UpdateDownloadState.Cancelled -> Text(stringResource(CommonR.string.update_download_cancelled))
            UpdateDownloadState.Idle -> Unit
        }
        installMessage?.let { message ->
            val resolved =
                when (message) {
                    is UpdateInstallMessage.Resource -> stringResource(message.resId)
                    is UpdateInstallMessage.Text -> message.text
                }
            Text(resolved, style = MaterialTheme.typography.bodyMedium)
        }
        if (permissionNeeded) {
            FilledTonalButton(onClick = {
                try {
                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } catch (
                    _: Exception,
                ) {
                    viewModel.installMessage(CommonR.string.update_install_settings_unavailable)
                }
            }) { Text(stringResource(CommonR.string.update_allow_installation)) }
        }
        Spacer(Modifier.height(Spacing.sm))
        FilledTonalButton(onClick = viewModel::checkForUpdates, enabled = !checking && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when {
                        checking -> CommonR.string.update_checking_github
                        error != null -> CommonR.string.update_retry_check
                        else -> CommonR.string.update_check_now
                    },
                ),
            )
        }
    }
}

@Composable
private fun rememberLocale(context: android.content.Context) = AppLocaleManager.activeLocale(context)
