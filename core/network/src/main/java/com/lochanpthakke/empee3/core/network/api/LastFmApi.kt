package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.LastFmSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApi {
    @GET("2.0/")
    suspend fun searchTrack(
        @Query("track") track: String,
        @Query("artist") artist: String?,
        @Query("api_key") apiKey: String,
        @Query("method") method: String = "track.search",
        @Query("format") format: String = "json",
    ): LastFmSearchResponse
}
