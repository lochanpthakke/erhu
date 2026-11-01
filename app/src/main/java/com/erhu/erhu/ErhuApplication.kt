package com.erhu.erhu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.erhu.erhu.app.theme.CrashThemeHandler
import com.erhu.erhu.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ErhuApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var crashThemeHandler: CrashThemeHandler

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        crashThemeHandler.install()
        widgetUpdater.start()
    }
}
