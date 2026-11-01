package com.erhu.erhu.core.common.permission

interface PermissionManager {
    fun hasAudioPermission(): Boolean

    fun getRequiredAudioPermission(): String
}
