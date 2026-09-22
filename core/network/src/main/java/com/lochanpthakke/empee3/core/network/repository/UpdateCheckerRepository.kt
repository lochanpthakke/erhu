package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.network.model.GitHubReleaseDto

interface UpdateCheckerRepository {
    suspend fun checkForUpdates(currentVersion: String = "1.0.0"): AppResult<GitHubReleaseDto?>
}
