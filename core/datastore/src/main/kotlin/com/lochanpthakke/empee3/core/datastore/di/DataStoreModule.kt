package com.lochanpthakke.empee3.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.lochanpthakke.empee3.core.datastore.PlaybackStateProtoSerializer
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private const val PLAYBACK_STATE_DATASTORE_FILE_NAME = "playback_state.pb"

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePlaybackStateDataStore(
        @ApplicationContext context: Context,
    ): DataStore<PlaybackStateProto> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return androidx.datastore.core.DataStoreFactory.create(
            serializer = PlaybackStateProtoSerializer,
            scope = scope,
            produceFile = { context.dataStoreFile(PLAYBACK_STATE_DATASTORE_FILE_NAME) },
        )
    }
}
