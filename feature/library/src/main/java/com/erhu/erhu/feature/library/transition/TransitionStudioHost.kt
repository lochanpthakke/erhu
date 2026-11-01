package com.erhu.erhu.feature.library.transition

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erhu.erhu.core.designsystem.component.PopupGlassBackdropHost

/**
 * Shows the Transition Studio for [input] full screen over the current page (its own window, so
 * nothing on the page underneath changes). Closing discards unsaved editor changes and stops any preview.
 */
@Composable
internal fun TransitionStudioHost(
    input: StudioInput,
    viewModel: TransitionStudioViewModel,
    onClose: () -> Unit,
) {
    DisposableEffect(input) {
        viewModel.open(input)
        onDispose { viewModel.close() }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PopupGlassBackdropHost(Modifier.fillMaxSize()) { TransitionStudioScreen(viewModel, onClose) }
    }
}
