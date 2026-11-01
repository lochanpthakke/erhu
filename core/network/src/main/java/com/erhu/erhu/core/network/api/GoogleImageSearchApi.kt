package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.GoogleImageSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleImageSearchApi {
    @GET("customsearch/v1")
    suspend fun searchImages(
        @Query("q") query: String,
        @Query("searchType") searchType: String = "image",
        @Query("imgType") imgType: String = "photo",
        @Query("num") num: Int = 10,
        @Query("key") apiKey: String? = null,
        @Query("cx") cx: String? = null,
    ): GoogleImageSearchResponse
}
