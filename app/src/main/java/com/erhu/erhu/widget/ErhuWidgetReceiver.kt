package com.erhu.erhu.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.erhu.erhu.BuildConfig

/** Framework/launcher delivery remains non-exported; only system and own explicit actions are accepted. */
abstract class ErhuWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (BuildConfig.DEBUG) Log.d("ErhuWidget", "${javaClass.simpleName}: ${intent.action}")
        super.onReceive(context, intent)
    }
}
