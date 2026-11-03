package com.erhu.erhu.onboarding

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ToolGuideContent(
    page: IntroductionPage,
    guide: IntroductionToolGuide,
) {
    var showDetails by rememberSaveable(page.name) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (page) {
                    IntroductionPage.ARTWORK -> Icons.Outlined.ImageSearch
                    IntroductionPage.TAGS -> Icons.Outlined.EditNote
                    else -> Icons.Outlined.Folder
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Where to find it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(guide.entryPoint, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !showDetails, onClick = { showDetails = false }, label = { Text("How to use") })
        FilterChip(selected = showDetails, onClick = { showDetails = true }, label = { Text("Details") })
    }
    val sections = if (showDetails) guide.details else guide.steps
    sections.forEachIndexed { index, (title, explanation) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                if (showDetails) title else "${index + 1}. $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
