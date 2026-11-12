package com.erhu.erhu.feature.settings.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.logging.CrashLogEntry
import com.erhu.erhu.core.common.logging.LocalCrashLogger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.repository.UpdateCheckerRepository
import com.erhu.erhu.core.network.update.ApkUpdateValidator
import com.erhu.erhu.core.network.update.AutomaticUpdateStatus
import com.erhu.erhu.core.network.update.InstalledAppVersion
import com.erhu.erhu.core.network.update.UpdateCheck
import com.erhu.erhu.core.network.update.UpdateDownloadManager
import com.erhu.erhu.core.network.update.UpdateDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.erhu.erhu.core.common.R as CommonR

sealed interface UpdateInstallMessage {
    data class Resource(
        @StringRes val resId: Int,
    ) : UpdateInstallMessage

    data class Text(
        val text: String,
    ) : UpdateInstallMessage
}

@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val updateCheckerRepository: UpdateCheckerRepository,
        private val downloads: UpdateDownloadManager,
        private val validator: ApkUpdateValidator,
        automaticStatus: AutomaticUpdateStatus,
    ) : ViewModel() {
        private val crashLogger = LocalCrashLogger(context)

        private val _crashLogs = MutableStateFlow<List<CrashLogEntry>>(emptyList())
        val crashLogs: StateFlow<List<CrashLogEntry>> = _crashLogs.asStateFlow()

        private var installPending = false
        val installedVersion = InstalledAppVersion.read(context)
        private val _update = MutableStateFlow<UpdateCheck?>(null)
        val update = _update.asStateFlow()
        private val _updateError = MutableStateFlow<String?>(null)
        val updateError = _updateError.asStateFlow()
        private val _isCheckingUpdate = MutableStateFlow(false)
        val isCheckingUpdate = _isCheckingUpdate.asStateFlow()
        val downloadState = downloads.state
        val automaticCheckSummary = automaticStatus.summary
        val automaticCheckRecord = automaticStatus.record
        private val _installationMessage = MutableStateFlow<UpdateInstallMessage?>(null)
        val installationMessage = _installationMessage.asStateFlow()
        private val _launchInstaller = MutableStateFlow<UpdateDownloadState.Ready?>(null)
        val launchInstaller = _launchInstaller.asStateFlow()

        init {
            loadLogs()
        }

        fun loadLogs() {
            _crashLogs.value = crashLogger.getCrashLogs()
        }

        fun clearLogs() {
            crashLogger.clearAllLogs()
            loadLogs()
        }

        fun copyLogToClipboard(entry: CrashLogEntry) {
            val localized = AppLocaleManager.localizedContext(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(localized.getString(CommonR.string.diagnostics_crash_log_clip_label), entry.content)
            clipboard.setPrimaryClip(clip)
        }

        fun checkForUpdates() {
            if (_isCheckingUpdate.value ||
                downloads.state.value is UpdateDownloadState.Downloading ||
                downloads.state.value == UpdateDownloadState.Verifying
            ) {
                return
            }
            downloads.reset()
            _installationMessage.value = null
            _launchInstaller.value = null
            _isCheckingUpdate.value = true
            _updateError.value = null
            _update.value = null
            viewModelScope.launch {
                try {
                    when (val result = updateCheckerRepository.checkForUpdates(InstalledAppVersion.read(context))) {
                        is AppResult.Success -> _update.value = result.data
                        is AppResult.Error -> _updateError.value = result.error.message
                    }
                } finally {
                    _isCheckingUpdate.value = false
                }
            }
        }

        fun downloadUpdate() {
            val candidate = _update.value as? UpdateCheck.Available ?: return
            _installationMessage.value = null
            downloads.start(candidate)
        }

        fun cancelDownload() = downloads.cancel()

        /** Revalidate against the installed package each time; never launch from a stale download. */
        fun requestInstall() {
            if (installPending) return
            val ready = downloads.state.value as? UpdateDownloadState.Ready ?: return
            installPending = true
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { validator.validate(ready.file, ready.update) }
                    _installationMessage.value = null
                    _launchInstaller.value = ready
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    installPending = false
                    downloads.reset()
                    _installationMessage.value =
                        e.message?.let(UpdateInstallMessage::Text)
                            ?: UpdateInstallMessage.Resource(CommonR.string.update_install_missing_file)
                }
            }
        }

        fun installerLaunched() {
            _launchInstaller.value = null
        }

        fun installMessage(
            @StringRes resId: Int,
        ) {
            installPending = false
            _installationMessage.value = UpdateInstallMessage.Resource(resId)
        }
    }
