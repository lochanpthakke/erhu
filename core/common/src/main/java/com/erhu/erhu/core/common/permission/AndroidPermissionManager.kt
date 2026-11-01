package com.erhu.erhu.core.common.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionManager {
        override fun hasAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                getRequiredAudioPermission(),
            ) == PackageManager.PERMISSION_GRANTED

        override fun getRequiredAudioPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
    }
