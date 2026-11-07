package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.foundation.Spacing

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ArtworkUpgradeScreen(
    controller: ArtworkUpgradeController,
    kind: ArtworkUpgradeKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    var selected by remember(kind) { mutableStateOf<ArtworkUpgradeResult?>(null) }
    val back = { if (!state.saving) onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(kind) { controller.select(kind) }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    LaunchedEffect(state.results) {
        if (selected !in state.results) selected = null
    }
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(back),
                title = if (kind == ArtworkUpgradeKind.ALBUMS) "Upgrade album covers" else "Upgrade artist photos",
            )
        },
        modifier = modifier,
    ) { padding ->
        val scroll = padding.forScrollingContent(topOverlay = true)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = scroll.contentTop + Spacing.md,
                    bottom = scroll.contentBottom + Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Column(Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "Find a larger version of your existing image. Only likely same-image matches appear; review before replacing. " +
                            "Your artwork stays unchanged until you apply a match. Saved images fit within 1,440 pixels. " +
                            "Only album and artist names are searched; your images are compared on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.scanning) {
                        LinearProgressIndicator(progress = {
                            if (state.total ==
                                0
                            ) {
                                0f
                            } else {
                                state.checked.toFloat() / state.total
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        Text("Checked ${state.checked} of ${state.total}")
                        TextButton(onClick = controller::stop) { Text("Stop search") }
                    } else {
                        Button(onClick = {
                            selected = null
                            controller.search(kind)
                        }, enabled = !state.saving) {
                            Text(if (state.checked > 0 || state.finished) "Search again" else "Find higher-resolution matches")
                        }
                        if (state.finished) Text("Checked ${state.checked} of ${state.total}. ${state.results.size} upgrades to review.")
                    }
                    if (state.skipped >
                        0
                    ) {
                        Text("${state.skipped} images or requests could not be checked.", style = MaterialTheme.typography.bodySmall)
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (state.saving) Text("Saving artwork…")
                }
            }
            item { SectionHeading(title = "Higher-resolution matches") }
            items(state.results, key = { it.target.id }) { result ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .groupSurface()
                        .clickable(enabled = !state.saving, role = Role.Button) {
                            controller.stop()
                            selected = result
                        }.padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    AsyncImage(
                        result.preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.target.name, style = MaterialTheme.typography.titleMedium)
                        Text(result.target.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${result.original.width} × ${result.original.height} → ${result.replacement.width} × ${result.replacement.height}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Likely same image · Review", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (state.finished && state.results.isEmpty()) {
                item { Text("No larger same-image matches to review. Some sources may not have a matching larger image.") }
            }
        }
    }
    selected?.let { result ->
        GlassAlertDialog(
            onDismissRequest = { if (!state.saving) selected = null },
            title = { Text("Review artwork upgrade") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(result.target.name, style = MaterialTheme.typography.titleMedium)
                    Text("Current · ${result.original.width} × ${result.original.height}")
                    AsyncImage(
                        result.preview,
                        "Current artwork",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Replacement · ${result.replacement.width} × ${result.replacement.height} · ${result.source}")
                    AsyncImage(
                        result.file,
                        "Higher-resolution artwork",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                    )
                    Text("Confirm this is the same image before replacing it. Other tags are left unchanged.")
                    state.message?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.apply(result)
                }, enabled = !state.saving && !state.scanning) { Text(if (state.saving) "Saving…" else "Use higher-resolution image") }
            },
            dismissButton = { TextButton(onClick = { selected = null }, enabled = !state.saving) { Text("Keep current") } },
        )
    }
}
