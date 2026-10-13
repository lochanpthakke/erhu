package com.lochanpthakke.empee3.core.network.tagger

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the user must do before the app may modify a set of audio files. */
sealed interface WriteAuthorization {
    data object Granted : WriteAuthorization

    /** Launch this system consent dialog (Android 10+) and retry if the user allows it. */
    class NeedsUserConsent(
        val intentSender: IntentSender,
    ) : WriteAuthorization

    /** Request this runtime permission (Android 9 and older) and retry if granted. */
    data class NeedsPermission(
        val permission: String,
    ) : WriteAuthorization
}

/**
 * Determines whether the app may overwrite the given MediaStore audio items.
 *
 * - Android 11+: other apps' media is writable only after the user approves a
 *   [MediaStore.createWriteRequest] dialog (one dialog covers many files).
 * - Android 10: there is no batch request; the platform throws a
 *   [RecoverableSecurityException] per file whose action asks for consent.
 * - Android 9 and older: the legacy WRITE_EXTERNAL_STORAGE permission.
 */
@Singleton
class TagWriteAccessChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun check(contentUris: List<String>): WriteAuthorization {
            val uris = contentUris.distinct().map(Uri::parse)
            if (uris.isEmpty()) return WriteAuthorization.Granted
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> checkScopedStorage(uris)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> checkAndroid10(uris)
                else -> checkLegacy()
            }
        }

        private fun checkScopedStorage(uris: List<Uri>): WriteAuthorization {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()) {
                return WriteAuthorization.Granted
            }
            val missing =
                uris.filter { uri ->
                    context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) !=
                        PackageManager.PERMISSION_GRANTED
                }
            if (missing.isEmpty()) return WriteAuthorization.Granted
            return WriteAuthorization.NeedsUserConsent(
                MediaStore.createWriteRequest(context.contentResolver, missing).intentSender,
            )
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun checkAndroid10(uris: List<Uri>): WriteAuthorization {
            for (uri in uris) {
                try {
                    // "rw" (not "w") so probing never truncates the file.
                    context.contentResolver.openFileDescriptor(uri, "rw")?.close()
                } catch (e: RecoverableSecurityException) {
                    return WriteAuthorization.NeedsUserConsent(e.userAction.actionIntent.intentSender)
                } catch (e: Exception) {
                    // Missing file etc.: let the write itself report the precise error.
                }
            }
            return WriteAuthorization.Granted
        }

        private fun checkLegacy(): WriteAuthorization {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                WriteAuthorization.Granted
            } else {
                WriteAuthorization.NeedsPermission(permission)
            }
        }
    }
