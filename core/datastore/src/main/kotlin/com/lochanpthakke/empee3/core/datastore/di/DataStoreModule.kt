package com.lochanpthakke.empee3.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.lochanpthakke.empee3.core.datastore.HiddenTracksPersistence
import com.lochanpthakke.empee3.core.datastore.HiddenTracksPersistenceImpl
import com.lochanpthakke.empee3.core.datastore.PlaybackStatePersistence
import com.lochanpthakke.empee3.core.datastore.PlaybackStatePersistenceImpl
import com.lochanpthakke.empee3.core.datastore.PlaybackStateProtoSerializer
import com.lochanpthakke.empee3.core.datastore.RepairBatchPersistence
import com.lochanpthakke.empee3.core.datastore.RepairBatchPersistenceImpl
import com.lochanpthakke.empee3.core.datastore.SoundSettingsPersistence
import com.lochanpthakke.empee3.core.datastore.SoundSettingsPersistenceImpl
import com.lochanpthakke.empee3.core.datastore.TransitionSettingsPersistence
import com.lochanpthakke.empee3.core.datastore.TransitionSettingsPersistenceImpl
import com.lochanpthakke.empee3.core.datastore.VisualizerSettingsPersistence
import com.lochanpthakke.empee3.core.datastore.VisualizerSettingsPersistenceImpl
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
    // Exception to project-wide DispatcherProvider convention: DataStoreFactory
    // requires a CoroutineScope matching the DataStore's own singleton lifetime,
    // not a general-purpose testable scope. Hardcoded per Deliverable #6 decision.
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

    @Provides
    @Singleton
    fun providePlaybackStatePersistence(dataStore: DataStore<PlaybackStateProto>): PlaybackStatePersistence =
        PlaybackStatePersistenceImpl(dataStore)

    @Provides
    @Singleton
    fun provideSoundSettingsPersistence(dataStore: DataStore<PlaybackStateProto>): SoundSettingsPersistence =
        SoundSettingsPersistenceImpl(dataStore)

    @Provides
    @Singleton
    fun provideRepairBatchPersistence(dataStore: DataStore<PlaybackStateProto>): RepairBatchPersistence =
        RepairBatchPersistenceImpl(dataStore)

    @Provides
    @Singleton
    fun provideVisualizerSettingsPersistence(dataStore: DataStore<PlaybackStateProto>): VisualizerSettingsPersistence =
        VisualizerSettingsPersistenceImpl(dataStore)

    @Provides
    @Singleton
    fun provideTransitionSettingsPersistence(dataStore: DataStore<PlaybackStateProto>): TransitionSettingsPersistence =
        TransitionSettingsPersistenceImpl(dataStore)

    @Provides
    @Singleton
    fun provideHiddenTracksPersistence(dataStore: DataStore<PlaybackStateProto>): HiddenTracksPersistence =
        HiddenTracksPersistenceImpl(dataStore)
}
