package com.candorfoods.factoryops.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A-185 daily HT Panel / Cold Room / UV readings API. One row per plant + date holds
 * all three sections. Bodies are raw [JsonObject]s so the exact snake_case keys and
 * null semantics are controlled at the call site (see A185ReadingsRepositoryImpl).
 * Auth per-call as "Bearer <token>". The three consumptions and the PF are computed
 * server-side and must NOT be sent.
 */
interface A185ReadingsApi {
    /** Upsert one day's readings (backend keys on plant + reading_date). Returns the stored row. */
    @POST("a185-readings")
    suspend fun submit(
        @Header("Authorization") token: String,
        @Body body: JsonObject,
    ): JsonObject

    /** History, newest first. `plant` defaults to A-185 server-side; from/to inclusive. */
    @GET("a185-readings")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("plant") plant: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<JsonObject>

    /**
     * Opening values for a new day's form: the three Initial fields carried from the
     * previous reading day's Finals + the fixed PF. `date` defaults to today when omitted.
     */
    @GET("a185-readings/prefill")
    suspend fun prefill(
        @Header("Authorization") token: String,
        @Query("plant") plant: String,
        @Query("date") date: String? = null,
    ): JsonObject

    /** Delete a whole day's row. HEAD only — 403 for any other role, 404 if the date has none. */
    @DELETE("a185-readings/{date}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("date") date: String,
        @Query("plant") plant: String,
    ): JsonObject
}
