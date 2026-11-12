/*
 * Copyright (C) 2026 Kshitij H Kekunnaya
 *
 * This file is part of erhu.
 * Licensed under the GNU General Public License v3.0.
 * See the LICENSE file in the project root.
 */

package com.erhu.erhu.feature.library

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.tagger.WriteAuthorization
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The existing write-consent flow, shared by manual edits and the service-owned batch. */
@Singleton
class MetadataWriteAccess
    @Inject
    constructor(
        private val metadata: MetadataRepository,
        @ApplicationContext private val context: Context? = null,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val requestState = MutableStateFlow<WriteAuthorization?>(null)
        val request = requestState.asStateFlow()
        private var pending: Pending? = null
        private var launched = false
        private val mutex = kotlinx.coroutines.sync.Mutex()
        val isPending: Boolean get() = pending != null

        suspend fun withWriteAccess(
            contentUris: List<String>,
            onGranted: suspend () -> Unit,
            onDenied: suspend () -> Unit,
            attempt: Int = 0,
        ): Unit =
            mutex.withLock {
                check(pending == null) { "Another edit is awaiting file access" }
                val authorization = metadata.checkTagWriteAccess(contentUris)
                // Android 10 can require one consent round per file, as in the original implementation.
                if (authorization is WriteAuthorization.Granted || attempt > contentUris.size) {
                    onGranted()
                    return@withLock
                }
                launched = false
                pending = Pending({ withWriteAccess(contentUris, onGranted, onDenied, attempt + 1) }, onDenied)
                requestState.value = authorization
            }

        @Suppress("TooGenericExceptionCaught")
        suspend fun withDeleteAccess(
            contentUris: List<String>,
            resolver: ContentResolver,
            onGranted: suspend () -> Unit,
            onDenied: suspend () -> Unit,
        ): Unit =
            mutex.withLock {
                check(pending == null) { "Another operation is awaiting file access" }
                val uris = contentUris.distinct().map(Uri::parse)
                if (uris.isEmpty()) {
                    onGranted()
                    return@withLock
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        onGranted()
                        return@withLock
                    }
                    val pendingIntent = MediaStore.createDeleteRequest(resolver, uris)
                    launched = false
                    pending = Pending(onGranted, onDenied)
                    requestState.value = WriteAuthorization.NeedsUserConsent(pendingIntent.intentSender)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    var consentNeeded: IntentSender? = null
                    for (uri in uris) {
                        try {
                            resolver.openFileDescriptor(uri, "rw")?.close()
                        } catch (e: RecoverableSecurityException) {
                            consentNeeded = e.userAction.actionIntent.intentSender
                            break
                        } catch (_: Exception) {
                        }
                    }
                    if (consentNeeded != null) {
                        launched = false
                        pending = Pending(onGranted, onDenied)
                        requestState.value = WriteAuthorization.NeedsUserConsent(consentNeeded)
                    } else {
                        onGranted()
                    }
                } else {
                    val appCtx = context
                    val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                    if (appCtx == null || ContextCompat.checkSelfPermission(appCtx, permission) == PackageManager.PERMISSION_GRANTED) {
                        onGranted()
                    } else {
                        launched = false
                        pending = Pending(onGranted, onDenied)
                        requestState.value = WriteAuthorization.NeedsPermission(permission)
                    }
                }
            }

        fun claimLaunch(): Boolean {
            if (pending == null || launched) return false
            launched = true
            return true
        }

        fun onResult(granted: Boolean) {
            requestState.value = null
            val next = pending ?: return
            pending = null
            launched = false
            scope.launch { if (granted) next.granted() else next.denied() }
        }

        private class Pending(
            val granted: suspend () -> Unit,
            val denied: suspend () -> Unit,
        )
    }
