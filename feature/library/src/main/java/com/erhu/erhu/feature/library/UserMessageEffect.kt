package com.erhu.erhu.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow

/** Shows each one-off ViewModel message once as a snackbar, then marks it consumed. */
@Composable
fun UserMessageEffect(
    message: StateFlow<String?>,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val current by message.collectAsState()
    LaunchedEffect(current) {
        val text = current ?: return@LaunchedEffect
        onConsumed()
        snackbarHostState.showSnackbar(text)
    }
}
