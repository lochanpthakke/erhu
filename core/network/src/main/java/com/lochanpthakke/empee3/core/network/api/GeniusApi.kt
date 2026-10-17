package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.GeniusSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface GeniusApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    @GET("api/search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
    ): GeniusSearchResponse

    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    @GET("api/search/multi")
    suspend fun searchMulti(
        @Query("q") query: String,
    ): GeniusSearchResponse
}
