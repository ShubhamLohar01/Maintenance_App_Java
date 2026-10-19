package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.BreakdownFlagResponseDto
import com.candorfoods.factoryops.data.remote.dto.BreakdownHistoryDto
import com.candorfoods.factoryops.data.remote.dto.OpenBreakdownDto
import com.candorfoods.factoryops.data.remote.dto.QcAcknowledgeRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcDecisionRequestDto
import com.candorfoods.factoryops.data.remote.dto.QcUpdateResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface BreakdownApi {
    /** Cross-device read: open breakdowns (not yet CLOSED) for a plant.
     * plant_id accepts "W202" / "A185" / "both" (any spelling). */
    @GET("breakdowns/open")
    suspend fun listOpen(
        @Header("Authorization") token: String,
        @Query("plant_id") plantId: String = "both"
    ): List<OpenBreakdownDto>

    /**
     * Operator raises a breakdown as multipart/form-data (NOT JSON). Text fields are
     * plain parts; the operator's before-photo is an optional file part named
     * "before_photo" — the backend uploads it to S3 and stores the URL in
     * before_photo_url. Content type must be image/jpeg or image/png, ≤ 10 MB.
     */
    @Multipart
    @POST("breakdowns/flag")
    suspend fun raiseFlag(
        @Header("Authorization") token: String,
        @Part("machine_id") machineId: RequestBody,
        @Part("operator_id") operatorId: RequestBody,
        @Part("severity") severity: RequestBody,
        @Part("description") description: RequestBody,
        @Part("raised_at") raisedAt: RequestBody,
        @Part beforePhoto: MultipartBody.Part?,
    ): BreakdownFlagResponseDto

    @POST("breakdowns/{id}/qc/acknowledge")
    suspend fun qcAcknowledge(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcAcknowledgeRequestDto
    ): QcUpdateResponseDto

    /**
     * Technician submits the completed repair as multipart/form-data -> PENDING_QC.
     * The after-repair photo is an optional file part named "after_photo"; the backend
     * uploads it to S3 and stores the URL in photo_url (NOT a device-local path).
     */
    @Multipart
    @POST("breakdowns/{id}/work-done")
    suspend fun workDone(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Part("user_id") userId: RequestBody,
        @Part("user_name") userName: RequestBody,
        @Part("work_done") workDone: RequestBody,
        // JSON array of the spares consumed. The server decrements each one's
        // stock and logs it as 'Breakdown'. An empty array means none were used.
        @Part("spares") spares: RequestBody,
        @Part("done_at") doneAt: RequestBody,
        @Part afterPhoto: MultipartBody.Part?,
    ): QcUpdateResponseDto

    @POST("breakdowns/{id}/qc/approve")
    suspend fun qcApprove(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcDecisionRequestDto
    ): QcUpdateResponseDto

    @POST("breakdowns/{id}/qc/disapprove")
    suspend fun qcDisapprove(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: QcDecisionRequestDto
    ): QcUpdateResponseDto

    /** Head Reports — every breakdown (open + closed) in range. status: OPEN/CLOSED/null=all. */
    @GET("breakdowns/history")
    suspend fun breakdownHistory(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("plant_id") plantId: String = "both",
        @Query("status") status: String? = null,
    ): List<BreakdownHistoryDto>

    /** Head Reports — every breakdown with a QC decision in range (latest decision only). */
    @GET("qc/history")
    suspend fun qcActivityHistory(
        @Header("Authorization") token: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("plant_id") plantId: String = "both",
    ): List<BreakdownHistoryDto>
}
