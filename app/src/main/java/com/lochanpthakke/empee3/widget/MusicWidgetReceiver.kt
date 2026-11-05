// Published Android/Glance/WorkManager identity retained for upgrade compatibility.
package com.lochanpthakke.empee3.widget

import androidx.glance.appwidget.GlanceAppWidget
import com.erhu.erhu.widget.ErhuWidgetReceiver

class MusicWidgetReceiver : ErhuWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
