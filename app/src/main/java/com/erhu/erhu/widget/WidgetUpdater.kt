package com.erhu.erhu.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.playback.PlaybackController
import com.lochanpthakke.empee3.widget.GlassMusicWidget
import com.lochanpthakke.empee3.widget.MusicWidget
import com.lochanpthakke.empee3.widget.VinylMusicWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps home-screen widgets (MusicWidget, GlassMusicWidget & VinylMusicWidget) live.
 *
 * Glance sessions expire; explicit updates also keep inactive instances synchronized.
 * This class re-renders all instances of widgets when state changes:
 * song, play/pause status, favorite flag, or theme.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackController: PlaybackController,
        private val libraryRepository: LibraryRepository,
        private val themeRepository: com.erhu.erhu.core.datastore.repository.ThemeRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val started =
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        private val requests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        fun requestUpdate() {
            requests.tryEmit(Unit)
        }

        @OptIn(kotlinx.coroutines.FlowPreview::class)
        fun start() {
            if (!started.compareAndSet(false, true)) return
            val displayedPlayback =
                playbackController.playbackState
                    .map {
                        WidgetDisplayState(it.currentSong, it.status, it.shuffleModeEnabled, it.positionMs / 15_000L, it.durationMs)
                    }.distinctUntilChanged()
            val favorite =
                playbackController.playbackState
                    .map { it.currentSong?.id }
                    .distinctUntilChanged()
                    .flatMapLatest { id -> id?.let { libraryRepository.observeIsFavorite(it) } ?: flowOf(false) }
                    .distinctUntilChanged()
            kotlinx.coroutines.flow
                .merge(
                    kotlinx.coroutines.flow.combine(displayedPlayback, favorite, themeRepository.widgetThemeState) { _, _, _ -> Unit },
                    requests,
                ).debounce(150L)
                .onEach { updateAllWidgetInstances() }
                .launchIn(scope)
        }

        private suspend fun updateAllWidgetInstances() {
            val manager = GlanceAppWidgetManager(context)
            for (widget in listOf(MusicWidget(), GlassMusicWidget(), VinylMusicWidget())) {
                try {
                    for (id in manager.getGlanceIds(widget.javaClass)) {
                        try {
                            widget.update(context, id)
                            if (com.erhu.erhu.BuildConfig.DEBUG) {
                                android.util.Log.d(
                                    "ErhuWidget",
                                    "Updated ${widget.javaClass.simpleName}: $id",
                                )
                            }
                        } catch (
                            cancelled: kotlinx.coroutines.CancellationException,
                        ) {
                            throw cancelled
                        } catch (
                            error: Exception,
                        ) {
                            android.util.Log.e("ErhuWidget", "Update failed: ${widget.javaClass.simpleName}", error)
                        }
                    }
                } catch (
                    cancelled: kotlinx.coroutines.CancellationException,
                ) {
                    throw cancelled
                } catch (
                    error: Exception,
                ) {
                    android.util.Log.e("ErhuWidget", "Provider lookup failed: ${widget.javaClass.simpleName}", error)
                }
            }
        }
    }

internal data class WidgetDisplayState(
    val song: com.erhu.erhu.core.model.Song?,
    val status: com.erhu.erhu.core.model.PlaybackStatus,
    val shuffle: Boolean,
    val positionBucket: Long,
    val durationMs: Long,
)
