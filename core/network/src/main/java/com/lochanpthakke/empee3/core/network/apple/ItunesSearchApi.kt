package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.network.apple.model.ItunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesSearchApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String = "us"
    ): ItunesSearchResponse
}
