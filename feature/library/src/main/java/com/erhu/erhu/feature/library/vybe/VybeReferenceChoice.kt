package com.erhu.erhu.feature.library.vybe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.vybe.EntityKind
import com.erhu.erhu.core.vybe.VybeNameMatch
import com.erhu.erhu.feature.library.songCountText
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun VybeReferenceChoice(
    match: VybeNameMatch,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenGutter)
                .semantics { selected = isSelected },
    ) {
        Column(Modifier.weight(1f)) {
            Text(match.label)
            val kindLabel =
                stringResource(
                    when (match.kind) {
                        EntityKind.ARTIST -> CommonR.string.vybe_kind_artist
                        EntityKind.ALBUM -> CommonR.string.vybe_kind_album
                        EntityKind.TITLE -> CommonR.string.vybe_kind_title
                    },
                )
            val songsCount = songCountText(match.songIds.size)
            val subtitle =
                if (isSelected) {
                    stringResource(
                        CommonR.string.vybe_ref_choice_subtitle_selected,
                        kindLabel,
                        songsCount,
                        stringResource(CommonR.string.transition_cd_selected),
                    )
                } else {
                    stringResource(
                        CommonR.string.vybe_ref_choice_subtitle,
                        kindLabel,
                        songsCount,
                    )
                }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
