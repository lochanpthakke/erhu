package com.erhu.erhu.core.network.apple

import com.erhu.erhu.core.network.apple.model.AppleCatalogResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface AppleMusicCatalogApi {
    @GET("v1/catalog/{storefront}/albums/{id}?extend=editorialVideo&platform=web")
    suspend fun getAlbum(
        @Path("storefront") storefront: String,
        @Path("id") id: String,
        @Header("Authorization") auth: String,
        @Header("Origin") origin: String = "https://music.apple.com",
    ): AppleCatalogResponse
}
