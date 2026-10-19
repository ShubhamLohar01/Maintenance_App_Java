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
 * W-202 daily Main Meter / Solar readings API. One row per plant + date + kind, where
 * `kind` is "main" or "solar" and selects the table server-side. Bodies are raw
 * [JsonObject]s so the exact snake_case keys and null semantics are controlled at the call
 * site (see W202ReadingsRepositoryImpl). Auth per-call as "Bearer <token>".
 *
 * Both consumptions are computed server-side and must NOT be sent. Solar's power factor is
 * computed too; only Main's is accepted from the client.
 *
 * Mirrors [A185ReadingsApi] deliberately — same shape, same verbs, so the two plant-specific
 * reading features stay recognisable as one pattern.
 */
interface W202ReadingsApi {
    /** Upsert one day's reading for one meter (backend keys on plant + reading_date + kind). */
    @POST("w202-readings")
    suspend fun submit(
        @Header("Authorization") token: String,
        @Body body: JsonObject,
    ): JsonObject

    /** History for one meter, newest first. from/to inclusive ISO dates; null = unbounded. */
    @GET("w202-readings")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("kind") kind: String,
        @Query("plant") plant: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<JsonObject>

    /**
     * Opening values for a new day's form. For kind=main the Initials carry from the previous
     * reading day's Finals; for kind=solar they come back null. `date` defaults to today.
     */
    @GET("w202-readings/prefill")
    suspend fun prefill(
        @Header("Authorization") token: String,
        @Query("kind") kind: String,
        @Query("plant") plant: String,
        @Query("date") date: String? = null,
    ): JsonObject

    /** Delete a whole day's row for one meter. HEAD only — 403 otherwise, 404 if no such date. */
    @DELETE("w202-readings/{date}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("date") date: String,
        @Query("kind") kind: String,
        @Query("plant") plant: String,
    ): JsonObject
}
