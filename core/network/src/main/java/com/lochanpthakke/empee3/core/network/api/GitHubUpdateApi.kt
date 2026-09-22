package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Headers

interface GitHubUpdateApi {
    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("repos/lochanpthakke/empee3/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseDto
}
