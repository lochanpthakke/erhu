package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.GitHubReleaseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

/** Existing official repository; credentials are never required for public releases. */
object UpdateRepositoryConfig {
    const val OWNER = "lochanpthakke"
    const val REPOSITORY = "erhu"
    const val LATEST = "repos/$OWNER/$REPOSITORY/releases/latest"
}

interface GitHubUpdateApi {
    @Headers("User-Agent: erhu-update-checker", "Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    @GET(UpdateRepositoryConfig.LATEST)
    suspend fun getLatestRelease(): Response<GitHubReleaseDto>
}
