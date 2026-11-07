package com.erhu.erhu.feature.library.vybe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.vybe.VybeNameMatch

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
            Text(
                match.kind.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() } + " · " + match.songIds.size + " songs" +
                    if (isSelected) " · Selected" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
