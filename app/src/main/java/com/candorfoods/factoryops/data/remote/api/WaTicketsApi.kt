package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.WaTicketDto
import com.candorfoods.factoryops.data.remote.dto.WaTicketResolveRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Raised Tickets — WhatsApp-raised maintenance complaints worked by an
 * it_technician.
 *
 * Reads are open to any authenticated caller (a supervisor can watch the queue);
 * acknowledge and resolve are technician-only and answer 403 otherwise.
 */
interface WaTicketsApi {

    /** `scope` is "live" (open + acknowledged, oldest first) or "history" (resolved). */
    @GET("wa-tickets")
    suspend fun list(
        @Header("Authorization") token: String,
        @Query("scope") scope: String,
        @Query("limit") limit: Int = 100,
    ): List<WaTicketDto>

    /** Same shape as the list plus `recipients` — the acknowledgment trail. */
    @GET("wa-tickets/{id}")
    suspend fun get(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): WaTicketDto

    /** Take the ticket. 409 when someone else already has it. */
    @POST("wa-tickets/{id}/acknowledge")
    suspend fun acknowledge(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): WaTicketDto

    /** Close it. `corrective_action` is required; a blank one is rejected with 400. */
    @POST("wa-tickets/{id}/resolve")
    suspend fun resolve(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: WaTicketResolveRequestDto,
    ): WaTicketDto
}
