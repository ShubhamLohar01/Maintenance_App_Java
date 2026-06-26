package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.FloorReadingsResponseDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitRequestDto
import com.candorfoods.factoryops.data.remote.dto.FloorReadingsSubmitResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Daily Reading screen (technician home). Plant/building scope is taken from the
 * JWT — no building param is sent. GET returns the system-computed kWh per floor
 * (plus any already-saved meter reading); POST upserts the technician's actual
 * meter readings, keyed on building+floor+date (re-submitting overwrites the day).
 */
interface FloorReadingApi {

    @GET("floor-readings/system")
    suspend fun fetchSystem(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null,        // optional "yyyy-MM-dd"; null = today
        @Query("building") building: String? = null, // null for technician/operator (resolved from JWT);
                                                      // set for HEAD/SUPERVISOR (all-plant roles)
    ): FloorReadingsResponseDto

    @POST("floor-readings")
    suspend fun submit(
        @Header("Authorization") token: String,
        @Body body: FloorReadingsSubmitRequestDto,
    ): FloorReadingsSubmitResponseDto
}
