package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.lochanpthakke.empee3.core.playback.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget's playback display live.
 *
 * MusicWidget.provideGlance() only reads playbackState once, at render time
 * (app open, screen unlock, periodic widget-refresh). Without an active
 * subscriber, a song change while the widget sits untouched on the home
 * screen goes unreflected until something else triggers a redraw.
 *
 * This class is that subscriber: it collects PlaybackController.playbackState
 * for the process lifetime and calls MusicWidget().update() on every
 * emission, pushing a redraw instead of waiting for one.
 *
 * Lives in :app (not :core:playback) — MusicPlaybackService cannot own this
 * itself since :app depends on :core:playback, not the reverse; :core:playback
 * must stay unaware Glance/widgets exist.
 */
@Singleton
class WidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackController: PlaybackController,
        private val themeRepository: com.lochanpthakke.empee3.core.datastore.repository.ThemeRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start() {
            playbackController.playbackState
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)

            themeRepository.widgetThemeState
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)
        }

        private suspend fun updateAllWidgetInstances() {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(MusicWidget::class.java)
            val widget = MusicWidget()
            glanceIds.forEach { glanceId ->
                widget.update(context, glanceId)
            }
        }
    }
