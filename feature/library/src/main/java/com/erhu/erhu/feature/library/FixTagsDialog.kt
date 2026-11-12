@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.Song
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

@Composable
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "LongParameterList")
fun FixTagsDialog(
    song: Song,
    candidates: List<MetadataCandidate>,
    onDismiss: () -> Unit,
    onSaveTags: (title: String, artist: String, album: String, year: Int, genre: String, bpm: Int) -> Unit,
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var yearStr by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var bpmStr by remember { mutableStateOf("") }

    val topCandidate = candidates.firstOrNull()
    val topScorePercent = topCandidate?.let { (it.confidenceScore * 100).roundToInt() } ?: 0
    val locale = AppLocaleManager.activeLocale(LocalContext.current)

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CommonR.string.fix_tags_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                if (candidates.isNotEmpty()) {
                    Text(
                        text = stringResource(CommonR.string.fix_tags_candidates_hint),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    if (topScorePercent < 85 && topScorePercent > 0) {
                        Text(
                            text =
                                stringResource(
                                    CommonR.string.fix_tags_low_confidence_warning,
                                    LocaleFormatters.formatInteger(topScorePercent, locale),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }

                    candidates.take(3).forEach { candidate ->
                        val score = (candidate.confidenceScore * 100).roundToInt()
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        title = candidate.title
                                        artist = candidate.artist
                                        album = candidate.album
                                        if (candidate.year in 1900..2099) yearStr = candidate.year.toString()
                                        candidate.genre?.let { genre = it }
                                        candidate.bpm?.let { bpmStr = it.toString() }
                                    },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        stringResource(
                                            CommonR.string.fix_tags_candidate_subtitle,
                                            candidate.artist,
                                            candidate.album,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text =
                                        stringResource(
                                            CommonR.string.fix_tags_candidate_score_source,
                                            LocaleFormatters.formatInteger(score, locale),
                                            candidate.sourceName,
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(CommonR.string.fix_tags_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(CommonR.string.fix_tags_field_artist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(CommonR.string.fix_tags_field_album)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                val keepPlaceholder = stringResource(CommonR.string.fix_tags_keep_current)
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_year)) },
                        placeholder = { Text(keepPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_genre)) },
                        placeholder = { Text(keepPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    OutlinedTextField(
                        value = bpmStr,
                        onValueChange = { bpmStr = it },
                        label = { Text(stringResource(CommonR.string.fix_tags_field_bpm)) },
                        placeholder = { Text(keepPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(CommonR.string.fix_tags_empty_fields_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = (yearStr.toIntOrNull() ?: 0).takeIf { it in 1900..2099 } ?: 0
                    val bpm = (bpmStr.toIntOrNull() ?: 0).takeIf { it in 1..999 } ?: 0
                    onSaveTags(title, artist, album, year, genre, bpm)
                },
                enabled = title.isNotBlank() && artist.isNotBlank(),
            ) {
                Text(stringResource(CommonR.string.fix_tags_save_write))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.action_cancel))
            }
        },
    )
}
