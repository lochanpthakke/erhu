@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.network.api.GitHubUpdateApi
import com.lochanpthakke.empee3.core.network.model.GitHubReleaseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class UpdateCheckerRepositoryImpl
    @Inject
    constructor(
        private val updateApi: GitHubUpdateApi,
        private val logger: Logger,
    ) : UpdateCheckerRepository {
        override suspend fun checkForUpdates(currentVersion: String): AppResult<GitHubReleaseDto?> =
            appResultOf {
                withContext(Dispatchers.IO) {
                    try {
                        val release = updateApi.getLatestRelease()
                        val latestTag = release.tagName.removePrefix("v").trim()
                        if (isVersionNewer(latestTag, currentVersion)) {
                            logger.i(TAG, "New update available: $latestTag (current: $currentVersion)")
                            release
                        } else {
                            logger.i(TAG, "App is up to date: $currentVersion")
                            null
                        }
                    } catch (e: Exception) {
                        logger.w(TAG, "Update check failed: ${e.message}", e)
                        null
                    }
                }
            }

        private fun isVersionNewer(
            latestTag: String,
            currentVersion: String,
        ): Boolean {
            val latestParts = latestTag.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }

        companion object {
            private const val TAG = "UpdateCheckerRepo"
        }
    }
