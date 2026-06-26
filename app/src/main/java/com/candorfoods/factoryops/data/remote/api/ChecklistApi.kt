package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.BreakdownSheetDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownSubmitResponseDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistRecordDetailDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistRecordSummaryDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistSubmissionDto
import com.candorfoods.factoryops.data.remote.dto.ChecklistSubmitResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ChecklistApi {
    /** Create a PM checklist record (DRAFT or SUBMITTED). Returns the new id. */
    @POST("preventive-maintenance/checklists")
    suspend fun submit(
        @Header("Authorization") token: String,
        @Body body: ChecklistSubmissionDto,
    ): ChecklistSubmitResponseDto

    /** Update an existing record (subsequent draft saves and finalizing). */
    @PUT("preventive-maintenance/checklists/{id}")
    suspend fun update(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body body: ChecklistSubmissionDto,
    ): ChecklistSubmitResponseDto

    /** List stored preventive checklists (newest first). */
    @GET("preventive-maintenance/checklists")
    suspend fun list(
        @Header("Authorization") token: String,
    ): List<ChecklistRecordSummaryDto>

    /** Fetch one stored checklist with all its checkpoint rows. */
    @GET("preventive-maintenance/checklists/{id}")
    suspend fun detail(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): ChecklistRecordDetailDto

    /** Submit a Breakdown Maintenance Record sheet (CFPLA.C4.F.06) — one row per entry. */
    @POST("preventive-maintenance/breakdowns")
    suspend fun submitBreakdown(
        @Header("Authorization") token: String,
        @Body body: BreakdownSheetDto,
    ): BreakdownSubmitResponseDto
}
