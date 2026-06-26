package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 201 response from POST /machine-transfers. The backend names the S3 object and returns its public URL. */
@Serializable
data class MachineTransferResponseDto(
    val id: Int,
    @SerialName("proof_photo_url") val proofPhotoUrl: String? = null,
)

/** One row from GET /machine-transfers. `created_at` is an ISO-8601 ...Z string. */
@Serializable
data class MachineTransferListItemDto(
    val id: Int,
    val date: String? = null,
    @SerialName("from_warehouse") val fromWarehouse: String = "",
    @SerialName("to_warehouse") val toWarehouse: String = "",
    @SerialName("machine_name") val machineName: String = "",
    val condition: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("proof_photo_url") val proofPhotoUrl: String? = null,
)
