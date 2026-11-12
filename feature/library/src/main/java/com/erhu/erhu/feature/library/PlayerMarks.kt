package com.erhu.erhu.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.AbLoopEdit
import com.erhu.erhu.core.model.AbLoopState
import com.erhu.erhu.core.model.TrackBookmark
import java.util.Locale
import com.erhu.erhu.core.common.R as CommonR

/** m:ss (h:mm:ss for long tracks). */
internal fun formatTimestamp(
    ms: Long,
    locale: Locale = Locale.getDefault(),
): String = LocaleFormatters.formatDurationClock(ms, locale)

/** User-facing message resource for a loop command that did not apply, or null when it worked. */
@StringRes
internal fun AbLoopEdit.messageRes(): Int? =
    when (this) {
        AbLoopEdit.OK -> null
        AbLoopEdit.NO_TRACK -> CommonR.string.marks_loop_no_track
        AbLoopEdit.A_NOT_BEFORE_B -> CommonR.string.marks_loop_a_not_before_b
        AbLoopEdit.NOT_READY -> CommonR.string.marks_loop_not_ready
        AbLoopEdit.UNSUPPORTED -> CommonR.string.marks_loop_unsupported
    }

/** Everything the marks tab can do; kept apart from the waveform's own drag state. */
internal class MarksActions(
    val onAddBookmark: () -> Unit,
    val onSeekBookmark: (TrackBookmark) -> Unit,
    val onRenameBookmark: (TrackBookmark, String) -> Unit,
    val onDeleteBookmark: (TrackBookmark) -> Unit,
    val onSetA: () -> Unit,
    val onSetB: () -> Unit,
    val onLoopEnabled: (Boolean) -> Unit,
    val onClearLoop: () -> Unit,
)

/** Bookmarks and the A–B loop of the current track. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
internal fun MarksPage(
    hasTrack: Boolean,
    loop: AbLoopState?,
    bookmarks: List<TrackBookmark>,
    position: State<Long>,
    actions: MarksActions,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    var renaming by remember { mutableStateOf<TrackBookmark?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "loop") {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                MarksHeading(stringResource(CommonR.string.marks_ab_loop_title))
                Text(
                    text = stringResource(CommonR.string.marks_ab_loop_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.heightIn(min = Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AssistChip(
                        onClick = actions.onSetA,
                        enabled = hasTrack,
                        label = {
                            Text(
                                loop?.startMs?.let {
                                    stringResource(CommonR.string.marks_point_a_set, formatTimestamp(it, locale))
                                } ?: stringResource(CommonR.string.marks_set_a),
                            )
                        },
                    )
                    AssistChip(
                        onClick = actions.onSetB,
                        enabled = hasTrack,
                        label = {
                            Text(
                                loop?.endMs?.let {
                                    stringResource(CommonR.string.marks_point_b_set, formatTimestamp(it, locale))
                                } ?: stringResource(CommonR.string.marks_set_b),
                            )
                        },
                    )
                    FilterChip(
                        selected = loop?.enabled == true,
                        onClick = { actions.onLoopEnabled(loop?.enabled != true) },
                        enabled = loop?.isComplete == true,
                        label = { Text(stringResource(CommonR.string.marks_loop)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                    AssistChip(
                        onClick = actions.onClearLoop,
                        enabled = loop != null,
                        label = { Text(stringResource(CommonR.string.action_clear)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        item(key = "bookmarksHeader") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarksHeading(stringResource(CommonR.string.marks_bookmarks_title), modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = actions.onAddBookmark,
                    enabled = hasTrack,
                    label = { CurrentPositionLabel(position) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
        if (bookmarks.isEmpty()) {
            item(key = "empty") {
                Text(
                    text =
                        stringResource(
                            if (hasTrack) CommonR.string.marks_bookmarks_empty else CommonR.string.marks_nothing_playing,
                        ),
                    style = TextStyles.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
        }
        items(bookmarks, key = { it.id }) { bookmark ->
            BookmarkRow(
                bookmark = bookmark,
                onSeek = { actions.onSeekBookmark(bookmark) },
                onRename = { renaming = bookmark },
                onDelete = { actions.onDeleteBookmark(bookmark) },
            )
        }
    }
    renaming?.let { bookmark ->
        BookmarkNameDialog(
            initial = bookmark.label,
            onDismiss = { renaming = null },
            onConfirm = { label ->
                actions.onRenameBookmark(bookmark, label)
                renaming = null
            },
        )
    }
}

@Composable
private fun MarksHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
}

/** Reads the position on its own, so only this label recomposes with playback ticks. */
@Composable
private fun CurrentPositionLabel(position: State<Long>) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Text(stringResource(CommonR.string.marks_add_at, formatTimestamp(position.value, locale)))
}

@Composable
private fun BookmarkRow(
    bookmark: TrackBookmark,
    onSeek: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val time = formatTimestamp(bookmark.positionMs, locale)
    val playFromLabel = stringResource(CommonR.string.marks_cd_play_from, time)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = playFromLabel, onClick = onSeek)
                .padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = bookmark.label.ifBlank { stringResource(CommonR.string.marks_default_label) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (bookmark.label.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(CommonR.string.marks_cd_rename_bookmark, time))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(CommonR.string.marks_cd_delete_bookmark, time))
        }
    }
}

@Composable
private fun BookmarkNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.marks_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(BOOKMARK_LABEL_MAX) },
                label = { Text(stringResource(CommonR.string.field_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(CommonR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.action_cancel)) } },
    )
}

private const val BOOKMARK_LABEL_MAX = 60
