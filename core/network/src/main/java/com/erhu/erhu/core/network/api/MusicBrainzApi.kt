package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.MusicBrainzReleaseGroupGenresDto
import com.erhu.erhu.core.network.model.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApi {
    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
    ): MusicBrainzSearchResponse

    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/artist")
    suspend fun searchArtist(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25,
    ): com.erhu.erhu.core.network.model.MusicBrainzArtistSearchResponse

    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/artist/{id}")
    suspend fun lookupArtistUrlRels(
        @Path("id") artistId: String,
        @Query("inc") include: String = "url-rels",
        @Query("fmt") format: String = "json",
    ): com.erhu.erhu.core.network.model.MusicBrainzArtistDto

    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/release-group")
    suspend fun searchReleaseGroup(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
    ): com.erhu.erhu.core.network.model.MusicBrainzReleaseGroupSearchResponse

    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("ws/2/release-group/{id}")
    suspend fun lookupReleaseGroupGenres(
        @Path("id") releaseGroupId: String,
        @Query("inc") include: String = "genres",
        @Query("fmt") format: String = "json",
    ): MusicBrainzReleaseGroupGenresDto
}
