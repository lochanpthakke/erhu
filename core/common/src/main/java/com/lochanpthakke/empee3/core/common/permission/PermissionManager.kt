package com.lochanpthakke.empee3.core.common.permission

interface PermissionManager {
    fun hasAudioPermission(): Boolean

    fun getRequiredAudioPermission(): String
}
