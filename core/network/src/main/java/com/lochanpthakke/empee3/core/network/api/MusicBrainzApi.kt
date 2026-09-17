package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface MusicBrainzApi {
    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
    ): MusicBrainzSearchResponse
}
