package com.erhu.erhu.announcements

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.erhu.erhu.core.designsystem.theme.PopupGlassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class AnnouncementsViewModel
    @Inject
    constructor(
        val controller: AnnouncementController,
        val notifications: AnnouncementNotifications,
    ) : ViewModel()

/** One overlay at a time; reopening the inbox is explicit and does not reset read/dismissed state. */
@Composable
fun AnnouncementHost(
    viewModel: AnnouncementsViewModel = hiltViewModel(),
    content: @Composable (() -> Unit) -> Unit,
) {
    val controller = viewModel.controller
    val memory by controller.memory.collectAsState()
    val foreground by controller.foreground.collectAsState()
    val fresh by controller.fresh.collectAsState()
    val requested by controller.requested.collectAsState()
    val error by controller.error.collectAsState()
    val refreshing by controller.refreshing.collectAsState()
    val context = LocalContext.current
    var history by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var automaticShown by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            permissionMessage =
                if (allowed) "Announcement notifications enabled." else "Notifications are disabled. In-app announcements still work."
        }
    LaunchedEffect(foreground) {
        if (!foreground) automaticShown = false
        while (foreground) {
            now = System.currentTimeMillis()
            delay(30000)
        }
    }
    val active =
        memory.items.filter { it.eligible(controller.installed, now) }.sortedWith(
            compareByDescending<com.erhu.erhu.core.network.announcements.Announcement> {
                it.type ==
                    "important_update"
            }.thenByDescending { it.publishedAt },
        )
    val selected = active.firstOrNull { it.id == selectedId }
    LaunchedEffect(foreground, fresh, requested, selectedId, history, memory.items, memory.read, memory.dismissed) {
        if (!foreground || !fresh || history || selectedId != null) return@LaunchedEffect
        val next =
            if (requested !=
                null
            ) {
                active.firstOrNull { it.id == requested }
            } else if (!automaticShown) {
                active.firstOrNull {
                    it.id !in memory.read &&
                        it.id !in memory.dismissed
                }
            } else {
                null
            }
        if (requested != null) {
            controller.requested.value = null
            if (next == null) {
                history = true
                return@LaunchedEffect
            }
        }
        if (next != null) {
            automaticShown = true
            selectedId = next.id
            controller.read(next.id)
            viewModel.notifications.cancel(next.id)
        }
    }
    LaunchedEffect(memory.items, fresh) {
        if (fresh && selectedId != null && selected == null) {
            viewModel.notifications.cancel(selectedId!!)
            selectedId = null
        }
    }
    LaunchedEffect(selected?.expiresAt) {
        selected?.let { item ->
            delay(
                (
                    java.time.Instant
                        .parse(item.expiresAt)
                        .toEpochMilli() - System.currentTimeMillis()
                ).coerceAtLeast(0),
            )
            now =
                System.currentTimeMillis()
            selectedId = null
        }
    }
    content {
        history = true
        controller.retry()
    }
    val dismiss = {
        selectedId?.let {
            controller.dismiss(it)
            viewModel.notifications.cancel(it)
        }
        selectedId = null
        actionError = null
    }
    if (selected != null && fresh) {
        AnnouncementPanel(onDismiss = dismiss) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (selected.type) {
                        "feature" -> "NEW FEATURE"
                        "important_update" -> "IMPORTANT UPDATE"
                        "maintenance" -> "MAINTENANCE"
                        else -> "ERHU NEWS"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xffc8ced8),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = dismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss announcement", tint = Color.White) }
            }
            Text(selected.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (selected.imageUrl !=
                null
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(selected.imageUrl)
                            .size(720, 480)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Text(selected.body, style = MaterialTheme.typography.bodyLarge, color = Color(0xffd7dbe2))
            actionError?.let { Text(it, color = Color(0xffffb4ab)) }
            if (selected.actionUrl != null) {
                Button(onClick = {
                    if (selected.eligible(controller.installed, System.currentTimeMillis())) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.actionUrl))) }
                            .onSuccess { dismiss() }
                            .onFailure { actionError = "This link could not be opened. Try again." }
                    } else {
                        dismiss()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(selected.actionLabel) }
            }
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
        }
    } else if (history) {
        AnnouncementPanel(onDismiss = {
            history = false
            selectedId = null
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Announcements", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    history =
                        false
                }) { Icon(Icons.Default.Close, contentDescription = "Close announcements", tint = Color.White) }
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xffd7dbe2)) }
            if (active.isEmpty() && !refreshing) Text("No active announcements.", color = Color(0xffc8ced8))
            active.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xff242933))
                        .clickable {
                            selectedId = item.id
                            controller.read(item.id)
                            viewModel.notifications.cancel(item.id)
                        }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(item.body.take(160), style = MaterialTheme.typography.bodyMedium, color = Color(0xffc8ced8))
                    if (item.id in memory.read) Text("Read", style = MaterialTheme.typography.labelSmall, color = Color(0xffa3adbc))
                }
            }
            TextButton(onClick = controller::retry, enabled = controller.configured && !refreshing) { Text("Refresh announcements") }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                TextButton(
                    onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                ) { Text("Enable announcement notifications") }
            }
            permissionMessage?.let { Text(it, color = Color(0xffc8ced8)) }
        }
    }
}

@Composable
private fun AnnouncementPanel(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        PopupGlassSurface(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xf016191f)),
        ) {
            Column(
                Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
