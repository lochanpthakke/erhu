package com.lochanpthakke.empee3.core.designsystem.component

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import java.util.function.Consumer

/**
 * Material [AlertDialog] made of the glass material when the Glass Effect is on.
 *
 * A dialog is its own window, so it cannot sample the app's content like
 * in-window glass does. On Android 12+ it uses the platform's cross-window
 * background blur instead: the window blurs what is behind it, clipped to the
 * dialog's rounded shape, and the dialog surface draws the glass fill, light and
 * rim on top. Where cross-window blur is unavailable (older Android, or the
 * device disables it, e.g. in battery saver) the dialog keeps the theme's
 * frosted dialog colour, exactly as before. Glass off: a plain Material dialog.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val glassOn = LocalGlassTokens.current != null
    val windowGlass = glassOn && rememberCrossWindowBlurEnabled()
    val shape = AlertDialogDefaults.shape
    val solidContainer = AlertDialogDefaults.containerColor
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            // Runs inside the dialog's window, where that window can be reached.
            if (windowGlass) DialogWindowBlur()
            confirmButton()
        },
        // Frosted window: translucent glass. No window blur: the same material on the
        // theme's solid dialog colour, so it stays readable over unblurred content.
        modifier =
            when {
                windowGlass ->
                    modifier.glassPanel(
                        shape,
                        level = GlassLevel.Medium,
                        elevated = false,
                        backdrop = null,
                        windowFrosted = true,
                    )
                glassOn ->
                    modifier.glassPanel(shape, level = GlassLevel.Medium, elevated = false, backdrop = null, fill = solidContainer)
                else -> modifier
            },
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glassOn) Color.Transparent else solidContainer,
    )
}

/** Blur behind the dialog window (radius in dp), clipped to the dialog's corner radius. */
private val DIALOG_BLUR_RADIUS = 24.dp

/** Corner radius of Material dialogs (AlertDialogDefaults.shape, extra-large). */
private val DIALOG_CORNER_RADIUS = 28.dp

@Composable
private fun DialogWindowBlur() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val density = LocalDensity.current
    val blurPx = with(density) { DIALOG_BLUR_RADIUS.roundToPx() }
    val cornerPx = with(density) { DIALOG_CORNER_RADIUS.toPx() }
    DisposableEffect(window, blurPx, cornerPx) {
        window.applyBackgroundBlur(blurPx, cornerPx)
        onDispose { }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.applyBackgroundBlur(
    blurPx: Int,
    cornerPx: Float,
) {
    // The blur region follows the window background's shape: a transparent
    // rounded drawable gives a rounded blur without painting anything itself.
    setBackgroundDrawable(
        GradientDrawable().apply {
            cornerRadius = cornerPx
            setColor(android.graphics.Color.TRANSPARENT)
        },
    )
    setBackgroundBlurRadius(blurPx)
}

/**
 * Whether the platform currently allows cross-window blur (Android 12+, device
 * support, and not disabled e.g. by battery saver). Follows changes live.
 */
@Composable
private fun rememberCrossWindowBlurEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rememberCrossWindowBlurEnabledApi31() else false

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun rememberCrossWindowBlurEnabledApi31(): Boolean {
    val windowManager = LocalContext.current.getSystemService(WindowManager::class.java)
    var enabled by remember(windowManager) { mutableStateOf(windowManager?.isCrossWindowBlurEnabled == true) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled = it }
        windowManager?.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager?.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}
