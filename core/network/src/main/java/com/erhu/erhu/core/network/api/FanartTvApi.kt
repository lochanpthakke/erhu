package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.FanartArtistResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface FanartTvApi {
    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("v3/music/{mbid}")
    suspend fun getArtistImages(
        @Path("mbid") musicBrainzId: String,
        @Query("api_key") apiKey: String,
    ): FanartArtistResponse
}
