package com.erhu.erhu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.erhu.erhu.app.theme.CrashThemeHandler
import com.erhu.erhu.core.common.locale.AppLocaleManager
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

    @Inject lateinit var announcements: com.erhu.erhu.announcements.AnnouncementController

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        AppLocaleManager.restoreLegacyLocaleOnStartup(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        crashThemeHandler.install()
        com.erhu.erhu.announcements.AnnouncementPush
            .initialize(this)
        if (announcements.configured) {
            com.erhu.erhu.announcements.AnnouncementPush
                .periodic(this)
        }
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    announcements.resumed()
                }

                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    announcements.paused()
                }
            },
        )
        widgetUpdater.start()
        com.erhu.erhu.widget.WidgetPreviewWorker
            .enqueue(this)
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "monthly-app-update-check",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work
                .PeriodicWorkRequestBuilder<MonthlyUpdateWorker>(30, java.util.concurrent.TimeUnit.DAYS)
                .setConstraints(
                    androidx.work.Constraints
                        .Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                ).build(),
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocaleManager.notifyLocaleChanged()
    }
}
