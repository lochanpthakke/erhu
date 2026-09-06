package com.lochanpthakke.empee3.widget

import com.lochanpthakke.empee3.core.playback.PlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackControllerEntryPoint {
    fun playbackController(): PlaybackController
}
