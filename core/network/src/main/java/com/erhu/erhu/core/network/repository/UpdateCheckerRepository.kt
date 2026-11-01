package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.model.GitHubReleaseDto

interface UpdateCheckerRepository {
    suspend fun checkForUpdates(currentVersion: String = "1.0.0"): AppResult<GitHubReleaseDto?>
}
