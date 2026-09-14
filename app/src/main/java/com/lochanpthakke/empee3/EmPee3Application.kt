package com.lochanpthakke.empee3

import android.app.Application
import com.lochanpthakke.empee3.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EmPee3Application : Application() {
    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        widgetUpdater.start()
    }
}
