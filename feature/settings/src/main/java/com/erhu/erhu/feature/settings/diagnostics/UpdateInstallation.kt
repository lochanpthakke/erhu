package com.erhu.erhu.feature.settings.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.erhu.erhu.core.network.update.UpdateDownloadState

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

internal fun installationResultMessage(code: Int): String =
    when (code) {
        Activity.RESULT_OK -> "Update installed successfully."
        Activity.RESULT_CANCELED -> "Installation cancelled. The verified APK is still ready; tap Install Update to retry."
        else -> "Android could not install the update. Check installation permission and try again."
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
