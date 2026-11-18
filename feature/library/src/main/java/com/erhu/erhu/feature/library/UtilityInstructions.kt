package com.erhu.erhu.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun UtilityInstructions(tool: UtilityTool) {
    var expanded by rememberSaveable(tool.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.utility_instructions))
        }
        if (expanded) {
            val guide = remember(tool) { tool.toolGuide() }
            ToolGuideContent(tool, guide)
        }
    }
}

@Composable
private fun ToolGuideContent(
    page: UtilityTool,
    guide: UtilityGuide,
) {
    var showDetails by rememberSaveable(page.name) { mutableStateOf(false) }
    ToolGuideHeaderCard(page = page, entryPointRes = guide.entryPointRes)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !showDetails,
            onClick = { showDetails = false },
            label = { Text(stringResource(CommonR.string.onboarding_guide_how_to_use)) },
        )
        FilterChip(
            selected = showDetails,
            onClick = { showDetails = true },
            label = { Text(stringResource(CommonR.string.onboarding_guide_details)) },
        )
    }
    val sections = if (showDetails) guide.details else guide.steps
    sections.forEachIndexed { index, (titleRes, explanationRes) ->
        val title = stringResource(titleRes)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                if (showDetails) {
                    title
                } else {
                    stringResource(CommonR.string.onboarding_guide_step_numbered, index + 1, title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(explanationRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolGuideHeaderCard(
    page: UtilityTool,
    entryPointRes: Int,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (page) {
                    UtilityTool.ARTWORK -> Icons.Outlined.ImageSearch
                    UtilityTool.TAGS -> Icons.Outlined.EditNote
                    else -> Icons.Outlined.Folder
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(CommonR.string.onboarding_guide_where_to_find),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(entryPointRes), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
