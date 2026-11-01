package com.erhu.erhu.feature.settings.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.logging.CrashLogEntry
import com.erhu.erhu.core.common.logging.LocalCrashLogger
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.model.GitHubReleaseDto
import com.erhu.erhu.core.network.repository.UpdateCheckerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val updateCheckerRepository: UpdateCheckerRepository,
    ) : ViewModel() {
        private val crashLogger = LocalCrashLogger(context)

        private val _crashLogs = MutableStateFlow<List<CrashLogEntry>>(emptyList())
        val crashLogs: StateFlow<List<CrashLogEntry>> = _crashLogs.asStateFlow()

        private val _latestRelease = MutableStateFlow<GitHubReleaseDto?>(null)
        val latestRelease: StateFlow<GitHubReleaseDto?> = _latestRelease.asStateFlow()

        private val _isCheckingUpdate = MutableStateFlow(false)
        val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

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
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Crash Log", entry.content)
            clipboard.setPrimaryClip(clip)
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                _isCheckingUpdate.value = true
                val result = updateCheckerRepository.checkForUpdates("1.0.0")
                if (result is AppResult.Success) {
                    _latestRelease.value = result.data
                } else {
                    _latestRelease.value = null
                }
                _isCheckingUpdate.value = false
            }
        }
    }
