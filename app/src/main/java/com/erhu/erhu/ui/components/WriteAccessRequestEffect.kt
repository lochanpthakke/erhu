package com.erhu.erhu.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.erhu.erhu.core.network.tagger.WriteAuthorization

/**
 * Shows the system UI that lets erhu modify audio files it doesn't own:
 * the MediaStore consent dialog (Android 10+) or the storage permission
 * prompt (Android 9 and older). The outcome is reported through [onResult].
 */
@Composable
fun WriteAccessRequestEffect(
    request: WriteAuthorization?,
    onResult: (granted: Boolean) -> Unit,
) {
    val consentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onResult(result.resultCode == Activity.RESULT_OK)
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onResult(granted)
        }

    LaunchedEffect(request) {
        when (request) {
            is WriteAuthorization.NeedsUserConsent ->
                consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            is WriteAuthorization.NeedsPermission -> permissionLauncher.launch(request.permission)
            WriteAuthorization.Granted, null -> Unit
        }
    }
}
