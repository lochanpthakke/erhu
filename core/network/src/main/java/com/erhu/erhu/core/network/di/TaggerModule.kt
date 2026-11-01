package com.erhu.erhu.core.network.di

import android.content.Context
import com.erhu.erhu.core.network.bpm.AudioPcmDecoder
import com.erhu.erhu.core.network.bpm.PcmSource
import com.erhu.erhu.core.network.tagger.AudioContentStore
import com.erhu.erhu.core.network.tagger.ContentResolverAudioContentStore
import com.erhu.erhu.core.network.tagger.MediaRescanner
import com.erhu.erhu.core.network.tagger.MediaScannerRescanner
import com.erhu.erhu.core.network.tagger.TagEditWorkDir
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
interface TaggerModule {
    @Binds
    fun bindAudioContentStore(impl: ContentResolverAudioContentStore): AudioContentStore

    @Binds
    fun bindMediaRescanner(impl: MediaScannerRescanner): MediaRescanner

    @Binds
    fun bindPcmSource(impl: AudioPcmDecoder): PcmSource

    companion object {
        @Provides
        fun provideTagEditWorkDir(
            @ApplicationContext context: Context,
        ): TagEditWorkDir = TagEditWorkDir { File(context.cacheDir, "tag-edit") }
    }
}
