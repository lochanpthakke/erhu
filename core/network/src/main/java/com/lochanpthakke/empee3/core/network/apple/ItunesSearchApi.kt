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

    /**
     * The artist's albums (the first result is the artist itself). Search often returns only
     * one edition of an album (e.g. the clean one); every edition is listed here.
     */
    @GET("lookup")
    suspend fun lookupArtistAlbums(
        @Query("id") artistId: Long,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 200,
        @Query("country") country: String = "us"
    ): ItunesSearchResponse
}
