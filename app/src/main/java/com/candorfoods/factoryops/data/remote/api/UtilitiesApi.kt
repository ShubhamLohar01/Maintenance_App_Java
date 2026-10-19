package com.candorfoods.factoryops.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Daily Utility Consumption API. One endpoint pair serves all four utilities via the
 * `{util}` path segment (diesel|gas|electricity|water). Bodies are raw [JsonObject]s
 * so each utility's exact snake_case keys and numeric/null types are controlled at the
 * call site (see UtilitiesRepositoryImpl). Auth per-call as "Bearer <token>".
 */
interface UtilitiesApi {
    /** Upsert one day's reading (backend keys on plant + reading_date). Returns the stored row. */
    @POST("utilities/{util}")
    suspend fun submit(
        @Header("Authorization") token: String,
        @Path("util") util: String,
        @Body body: JsonObject,
    ): JsonObject

    /** History for a utility, newest first. Optional plant filter ("A185" / "W202"). */
    @GET("utilities/{util}")
    suspend fun list(
        @Header("Authorization") token: String,
        @Path("util") util: String,
        @Query("plant") plant: String? = null,
    ): List<JsonObject>

    /**
     * Current supervisor-set prices, one row per plant (backend returns an array).
     * Open to any authenticated caller so the technician form can display the
     * read-only rate. Optional plant filter.
     */
    @GET("utilities/rates")
    suspend fun rates(
        @Header("Authorization") token: String,
        @Query("plant") plant: String? = null,
    ): List<JsonObject>

    /**
     * Set one or more rates for a plant (SUPERVISOR/HEAD/ADMIN — 403 otherwise).
     * Partial: only the keys present in [body] change. Returns the stored row.
     */
    @PUT("utilities/rates")
    suspend fun setRates(
        @Header("Authorization") token: String,
        @Body body: JsonObject,
    ): JsonObject

    /**
     * Form pre-fill for a new reading: opening meter fields copied from the most
     * recent earlier row's closing (survives skipped days) + the current rate.
     * `date` defaults to today server-side when omitted.
     */
    @GET("utilities/{util}/prefill")
    suspend fun prefill(
        @Header("Authorization") token: String,
        @Path("util") util: String,
        @Query("plant") plant: String,
        @Query("date") date: String? = null,
    ): JsonObject
}
