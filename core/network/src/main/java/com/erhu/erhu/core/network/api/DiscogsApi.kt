package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.DiscogsArtistDetailDto
import com.erhu.erhu.core.network.model.DiscogsSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscogsApi {
    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("database/search")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("type") type: String = "artist",
        @Query("per_page") perPage: Int = 10,
    ): DiscogsSearchResponse

    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("artists/{id}")
    suspend fun getArtistDetail(
        @Path("id") artistId: Long,
    ): DiscogsArtistDetailDto
}
