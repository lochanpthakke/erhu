package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.FanartArtistResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface FanartTvApi {
    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("v3/music/{mbid}")
    suspend fun getArtistImages(
        @Path("mbid") musicBrainzId: String,
        @Query("api_key") apiKey: String = DEFAULT_CLIENT_KEY,
    ): FanartArtistResponse

    companion object {
        // Public Fanart.tv client key for music metadata lookups
        const val DEFAULT_CLIENT_KEY = "5f2c41804c81be6a24eb5ef74a58b29c"
    }
}
