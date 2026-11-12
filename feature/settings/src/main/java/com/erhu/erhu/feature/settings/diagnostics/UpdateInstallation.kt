package com.erhu.erhu.feature.settings.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.erhu.erhu.core.network.update.UpdateDownloadState
import com.erhu.erhu.core.common.R as CommonR

internal enum class InstallPreparation { MISSING_FILE, PERMISSION_REQUIRED, READY }

internal fun prepareUpdateInstall(
    fileExists: Boolean,
    allowed: Boolean,
): InstallPreparation =
    when {
        !fileExists -> InstallPreparation.MISSING_FILE
        !allowed -> InstallPreparation.PERMISSION_REQUIRED
        else -> InstallPreparation.READY
    }

@StringRes
internal fun installationResultMessageRes(code: Int): Int =
    when (code) {
        Activity.RESULT_OK -> CommonR.string.update_install_success
        Activity.RESULT_CANCELED -> CommonR.string.update_install_cancelled
        else -> CommonR.string.update_install_failed
    }

@Suppress("DEPRECATION")
internal fun updateInstallerIntent(
    context: Context,
    ready: UpdateDownloadState.Ready,
): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
    return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("erhu update", uri)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
