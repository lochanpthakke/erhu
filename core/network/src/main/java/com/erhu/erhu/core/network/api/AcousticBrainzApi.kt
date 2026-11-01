package com.erhu.erhu.core.network.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * AcousticBrainz: audio features (Essentia) computed from real recordings, keyed by MusicBrainz
 * recording id. Collection stopped in 2022 and the dataset is read-only, but the API still serves
 * it (checked 2026-10-15). Only a recording id is sent, never audio.
 */
interface AcousticBrainzApi {
    /** `{ "<mbid>": { "0": { "rhythm": { "bpm": 128.3 } }, "1": … }, "mbid_mapping": {} }`; no key when unknown. */
    @Headers("User-Agent: erhu/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("api/v1/low-level")
    suspend fun lowLevel(
        @Query("recording_ids") recordingIds: String,
        @Query("features") features: String = "rhythm.bpm",
    ): JsonObject
}
