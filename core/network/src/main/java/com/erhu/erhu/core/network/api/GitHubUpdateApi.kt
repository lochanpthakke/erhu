package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.GitHubReleaseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/** Existing official repository; credentials are never required for public releases. */
object UpdateRepositoryConfig {
    const val OWNER = "lochanpthakke"
    const val REPOSITORY = "erhu"
    const val RELEASES = "repos/$OWNER/$REPOSITORY/releases"

    // erhu is currently distributed as a beta. Drafts are never eligible.
    const val INCLUDE_PRERELEASES = true
}

interface GitHubUpdateApi {
    @Headers("User-Agent: erhu-update-checker", "Accept: application/vnd.github+json", "X-GitHub-Api-Version: 2022-11-28")
    @GET(UpdateRepositoryConfig.RELEASES)
    suspend fun getReleases(
        @Query("per_page") limit: Int = 100,
    ): Response<List<GitHubReleaseDto>>
}
