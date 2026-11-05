package com.erhu.erhu.announcements

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.erhu.erhu.core.network.announcements.Announcement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AnnouncementMemory(
    val items: List<Announcement> = emptyList(),
    val read: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet(),
    val notified: Set<String> = emptySet(),
    val lastSync: Long = 0,
)

@Singleton
class AnnouncementStore internal constructor(
    private val preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("announcements") })

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("state-v1")

    private fun decode(value: String?) = value?.let { json.decodeFromString<AnnouncementMemory>(it) } ?: AnnouncementMemory()

    val data = preferences.data.map { decode(it[key]) }

    suspend fun update(transform: (AnnouncementMemory) -> AnnouncementMemory) {
        preferences.edit { it[key] = json.encodeToString(transform(decode(it[key]))) }
    }
}
