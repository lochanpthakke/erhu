package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.TheAudioDbArtistResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface TheAudioDbApi {
    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("api/v1/json/{key}/artist-mb.php")
    suspend fun getArtistByMbid(
        @Path("key") apiKey: String = DEFAULT_API_KEY,
        @Query("i") mbid: String,
    ): TheAudioDbArtistResponse

    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("api/v1/json/{key}/search.php")
    suspend fun searchArtist(
        @Path("key") apiKey: String = DEFAULT_API_KEY,
        @Query("s") artistName: String,
    ): TheAudioDbArtistResponse

    companion object {
        /** Free test key provided by TheAudioDB for non-commercial music applications */
        const val DEFAULT_API_KEY = "2"
    }
}
