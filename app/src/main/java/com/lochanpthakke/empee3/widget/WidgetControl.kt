package com.lochanpthakke.empee3.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider

@Composable
internal fun WidgetControl(
    icon: Int,
    label: String,
    action: Action,
    tint: ColorProvider,
    iconSize: Int = 26,
) {
    Box(GlanceModifier.size(48.dp).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), label, GlanceModifier.size(iconSize.dp), colorFilter = ColorFilter.tint(tint))
    }
}
