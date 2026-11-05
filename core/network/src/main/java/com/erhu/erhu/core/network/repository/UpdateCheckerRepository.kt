package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.update.InstalledAppVersion
import com.erhu.erhu.core.network.update.UpdateCheck

interface UpdateCheckerRepository {
    suspend fun checkForUpdates(current: InstalledAppVersion): AppResult<UpdateCheck>
}
