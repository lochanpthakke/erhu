package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.ITunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 10,
    ): ITunesSearchResponse
}
