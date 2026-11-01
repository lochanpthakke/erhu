package com.erhu.erhu.core.network.api

import com.erhu.erhu.core.network.model.WikidataEntityResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface WikidataApi {
    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("wiki/Special:EntityData/{qid}.json")
    suspend fun getEntityData(
        @Path("qid") qid: String,
    ): WikidataEntityResponse
}
