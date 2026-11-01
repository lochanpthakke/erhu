package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.DeezerAlbumDetailDto
import com.erhu.erhu.core.network.model.DeezerArtistSearchResponse
import com.erhu.erhu.core.network.model.DeezerSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DeezerApi {
    @GET("search")
    suspend fun searchTrack(
        @Query("q") query: String,
    ): DeezerSearchResponse

    @GET("search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("limit") limit: Int = ARTIST_SEARCH_LIMIT,
    ): DeezerArtistSearchResponse

    @GET("album/{id}")
    suspend fun getAlbum(
        @Path("id") albumId: Long,
    ): DeezerAlbumDetailDto

    companion object {
        /** Deezer's page maximum: same-name duplicates often rank below the first 25 results. */
        const val ARTIST_SEARCH_LIMIT = 100
    }
}
