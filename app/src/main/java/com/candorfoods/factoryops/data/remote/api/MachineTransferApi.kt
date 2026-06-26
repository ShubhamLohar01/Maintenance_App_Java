package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MachineTransferListItemDto
import com.candorfoods.factoryops.data.remote.dto.MachineTransferResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MachineTransferApi {
    /**
     * Create a transfer as multipart/form-data (NOT JSON). Text fields are plain
     * parts; the image is an optional file part whose form name is "proof_photo"
     * (set when the [MultipartBody.Part] is built). The backend uploads to S3 and
     * returns the public URL. Content type must be image/jpeg or image/png, ≤ 10 MB.
     */
    @Multipart
    @POST("machine-transfers")
    suspend fun create(
        @Header("Authorization") token: String,
        @Part("from_warehouse") fromWarehouse: RequestBody,
        @Part("to_warehouse") toWarehouse: RequestBody,
        @Part("machine_name") machineName: RequestBody,
        @Part("date") date: RequestBody?,
        @Part("machine_code") machineCode: RequestBody?,
        @Part("condition") condition: RequestBody?,
        @Part("reason") reason: RequestBody?,
        @Part("authorised_person") authorisedPerson: RequestBody?,
        @Part("remarks") remarks: RequestBody?,
        @Part proofPhoto: MultipartBody.Part?,
    ): MachineTransferResponseDto

    @GET("machine-transfers")
    suspend fun list(
        @Header("Authorization") token: String,
    ): List<MachineTransferListItemDto>
}
