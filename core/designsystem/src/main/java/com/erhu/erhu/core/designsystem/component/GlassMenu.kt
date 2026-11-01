package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.theme.LocalPopupGlassInMotion
import com.erhu.erhu.core.designsystem.theme.PopupGlassTheme
import kotlinx.coroutines.flow.first

/**
 * Shared anchored action menu. Place inside the same Box as the trigger.
 * One interruptible surface spring moves every row together; the popup stays mounted for exit.
 * A focusable platform popup owns Back/outside dismissal, clipping and accessibility focus.
 *
 * Popup optics use the theme's demand-driven screen-coordinate backdrop, never the playback palette.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Options",
    dismissOnSelection: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fallbackBounds = rememberMenuAnchorBounds()
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    val dismiss by rememberUpdatedState(onDismissRequest)
    var actionTaken by remember(expanded) { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<(() -> Unit)?>(null) }
    MenuActionAfterExit(visible, pendingSelection) { pendingSelection = null }
    var confirmation by remember { mutableStateOf<PendingMenuConfirmation?>(null) }
    val interaction =
        GlassMenuInteraction(expanded && !actionTaken) { prompt, action ->
            if (expanded && !actionTaken) {
                if (!dismissOnSelection && prompt == null) {
                    action()
                } else {
                    actionTaken = true
                    dismiss()
                    pendingSelection = { if (prompt == null) action() else confirmation = PendingMenuConfirmation(prompt, action) }
                }
            }
        }
    if (visible.currentState || visible.targetState || !visible.isIdle) {
        GlassMenuPopup(visible, { if (expanded) dismiss() }, modifier, label, interaction, fallbackBounds, content)
    }
    confirmation?.let { pending ->
        GlassAlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(pending.prompt.title) },
            text = { Text(pending.prompt.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        pending.action()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(pending.prompt.confirmLabel) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmation = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun rememberMenuAnchorBounds(): androidx.compose.ui.geometry.Rect {
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Box(
        Modifier.onGloballyPositioned {
            bounds = it.parentLayoutCoordinates?.boundsInWindow() ?: it.boundsInWindow()
        },
    )
    return bounds
}

@Composable
private fun MenuActionAfterExit(
    visible: MutableTransitionState<Boolean>,
    pending: (() -> Unit)?,
    consumed: () -> Unit,
) {
    LaunchedEffect(pending) {
        val action = pending ?: return@LaunchedEffect
        snapshotFlow { visible.isIdle && !visible.currentState && !visible.targetState }.first { it }
        consumed()
        action()
    }
}

@Composable
private fun GlassMenuPopup(
    visible: MutableTransitionState<Boolean>,
    dismiss: () -> Unit,
    modifier: Modifier,
    label: String,
    interaction: GlassMenuInteraction,
    fallbackBounds: androidx.compose.ui.geometry.Rect,
    content: @Composable ColumnScope.() -> Unit,
) {
    val anchor = LocalGlassMenuAnchor.current
    DisposableEffect(anchor) {
        anchor?.active = true
        onDispose { anchor?.active = false }
    }
    val transition = rememberTransition(visible, label = "Glass menu")
    val progress =
        transition.animateFloat(
            transitionSpec = { if (targetState) Motion.responsive(visibilityThreshold = 0.001f) else Motion.fade(150) },
            label = "Trigger to menu",
        ) { if (it) 1f else 0f }
    var windowOffset by remember { mutableStateOf(IntOffset.Zero) }
    val position =
        remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: androidx.compose.ui.unit.IntRect,
                    windowSize: androidx.compose.ui.unit.IntSize,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    popupContentSize: androidx.compose.ui.unit.IntSize,
                ) = windowOffset
            }
        }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true, excludeFromSystemGesture = false, clippingEnabled = false),
    ) {
        val focusManager = LocalFocusManager.current
        PopupGlassTheme {
            CompositionLocalProvider(LocalGlassMenuInteraction provides interaction, LocalPopupGlassInMotion provides { !visible.isIdle }) {
                AnchoredMenuLayout(
                    anchor = anchor?.bounds ?: fallbackBounds,
                    progress = progress,
                    onWindowPosition = { windowOffset = it },
                    drawTrigger = anchor != null,
                    dismiss = dismiss,
                    modifier = modifier.semantics { paneTitle = label }.menuKeys(focusManager, dismiss),
                    content = content,
                )
            }
        }
    }
}

private fun Modifier.menuKeys(
    focusManager: FocusManager,
    dismiss: () -> Unit,
): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                Key.Escape -> {
                    dismiss()
                    true
                }
                else -> false
            }
        }
    }

private data class PendingMenuConfirmation(
    val prompt: GlassMenuConfirmation,
    val action: () -> Unit,
)
