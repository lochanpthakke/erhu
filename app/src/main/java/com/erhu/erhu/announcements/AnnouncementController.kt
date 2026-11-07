package com.erhu.erhu.announcements

import android.content.Context
import com.erhu.erhu.core.network.announcements.Announcement
import com.erhu.erhu.core.network.announcements.AnnouncementClient
import com.erhu.erhu.core.network.announcements.validAnnouncementId
import com.erhu.erhu.core.network.update.InstalledAppVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: AnnouncementStore,
        private val client: AnnouncementClient,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val gate = Mutex()
        val installed = InstalledAppVersion.read(context)
        val configured get() = client.configured
        val foreground = MutableStateFlow(false)
        val requested = MutableStateFlow<String?>(null)
        val fresh = MutableStateFlow(false)
        val refreshing = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)
        val memory: StateFlow<AnnouncementMemory> =
            store.data
                .catch {
                    error.value = "Saved announcements are unavailable."
                    emit(AnnouncementMemory())
                }.stateIn(scope, SharingStarted.Eagerly, AnnouncementMemory())

        fun resumed() {
            foreground.value = true
            // Opening the app must discover new notices even when a push was missed.
            // The periodic/background refresh can still reuse the cached feed.
            scope.launch { refresh(force = true) }
        }

        fun paused() {
            foreground.value = false
        }

        fun requestFromNotification(id: String?) {
            if (id == null || !validAnnouncementId(id)) return
            scope.launch {
                refresh(id, true)
                requested.value = id
            }
        }

        fun retry() {
            scope.launch { refresh(force = true) }
        }

        suspend fun refresh(
            id: String? = null,
            force: Boolean = false,
        ): Boolean =
            gate.withLock {
                if (!configured) {
                    error.value = "Announcements are not available in this build."
                    return@withLock false
                }
                refreshing.value = true
                try {
                    val before = store.data.first()
                    if (!force && id == null && System.currentTimeMillis() - before.lastSync in 0..899999) {
                        fresh.value = true
                        return@withLock true
                    }
                    val found = client.fetch(id).filter { it.eligible(installed, System.currentTimeMillis()) }
                    store.update { current ->
                        current.copy(
                            items = if (id == null) found else (current.items.filterNot { it.id == id } + found).takeLast(100),
                            lastSync = if (id == null) System.currentTimeMillis() else current.lastSync,
                        )
                    }
                    val retained =
                        store.data
                            .first()
                            .items
                            .map { it.id }
                            .toSet()
                    val notifications = context.getSystemService(android.app.NotificationManager::class.java)
                    before.items.filter { it.id !in retained }.forEach { notifications.cancel(it.id, 0) }
                    fresh.value = true
                    error.value = null
                    true
                } catch (
                    cancelled: CancellationException,
                ) {
                    throw cancelled
                } catch (
                    _: Exception,
                ) {
                    error.value = "Announcements could not be refreshed. Check your connection and retry."
                    false
                } finally {
                    refreshing.value = false
                }
            }

        suspend fun current(id: String): Announcement? =
            store.data.first().items.firstOrNull {
                it.id == id &&
                    it.eligible(installed, System.currentTimeMillis())
            }

        suspend fun unseen(id: String): Boolean = store.data.first().let { id !in it.read && id !in it.dismissed && id !in it.notified }

        fun read(id: String) {
            scope.launch { store.update { it.copy(read = it.read + id) } }
        }

        fun dismiss(id: String) {
            scope.launch { store.update { it.copy(read = it.read + id, dismissed = it.dismissed + id) } }
        }

        suspend fun notified(id: String) {
            store.update { it.copy(notified = it.notified + id) }
        }
    }
