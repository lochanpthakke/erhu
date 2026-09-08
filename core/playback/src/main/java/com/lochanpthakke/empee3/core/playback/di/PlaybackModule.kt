package com.lochanpthakke.empee3.core.playback.di

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lochanpthakke.empee3.core.playback.PlaybackController
import com.lochanpthakke.empee3.core.playback.PlaybackControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerProviderModule {
    @Provides
    @Singleton
    fun providePlayer(
        @ApplicationContext context: Context,
    ): Player = ExoPlayer.Builder(context).build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackBindModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: PlaybackControllerImpl): PlaybackController
}
