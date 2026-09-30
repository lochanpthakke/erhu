package com.lochanpthakke.empee3.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.playback.PlaybackController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget's playback display live.
 *
 * MusicWidget.provideGlance() only reads playbackState once, at render time.
 * This class re-renders the widget when something it displays changes: the
 * song, play/pause status, the song's favorite flag, or the widget theme.
 * Position ticks are deliberately ignored — redrawing a RemoteViews widget
 * several times per second wastes battery for nothing the widget shows.
 *
 * Lives in :app (not :core:playback) — :core:playback must stay unaware
 * Glance/widgets exist.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackController: PlaybackController,
        private val libraryRepository: LibraryRepository,
        private val themeRepository: com.lochanpthakke.empee3.core.datastore.repository.ThemeRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start() {
            val displayedPlayback =
                playbackController.playbackState
                    .map { it.currentSong?.id to it.status }
                    .distinctUntilChanged()

            displayedPlayback
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)

            displayedPlayback
                .map { it.first }
                .distinctUntilChanged()
                .flatMapLatest { songId -> songId?.let { libraryRepository.observeIsFavorite(it) } ?: flowOf(false) }
                .distinctUntilChanged()
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)

            themeRepository.widgetThemeState
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)
        }

        private suspend fun updateAllWidgetInstances() {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(MusicWidget::class.java)
            if (glanceIds.isEmpty()) return
            val widget = MusicWidget()
            glanceIds.forEach { glanceId ->
                widget.update(context, glanceId)
            }
        }
    }
