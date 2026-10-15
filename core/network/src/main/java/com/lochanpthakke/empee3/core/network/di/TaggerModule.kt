package com.lochanpthakke.empee3.core.network.di

import android.content.Context
import com.lochanpthakke.empee3.core.network.bpm.AudioPcmDecoder
import com.lochanpthakke.empee3.core.network.bpm.PcmSource
import com.lochanpthakke.empee3.core.network.tagger.AudioContentStore
import com.lochanpthakke.empee3.core.network.tagger.ContentResolverAudioContentStore
import com.lochanpthakke.empee3.core.network.tagger.MediaRescanner
import com.lochanpthakke.empee3.core.network.tagger.MediaScannerRescanner
import com.lochanpthakke.empee3.core.network.tagger.TagEditWorkDir
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
