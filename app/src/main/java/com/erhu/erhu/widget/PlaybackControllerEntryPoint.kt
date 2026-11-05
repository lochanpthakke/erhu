package com.erhu.erhu.widget

import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.playback.PlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackControllerEntryPoint {
    fun playbackController(): PlaybackController

    fun libraryRepository(): LibraryRepository

    fun widgetUpdater(): WidgetUpdater
}
