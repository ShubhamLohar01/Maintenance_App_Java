package com.candorfoods.factoryops.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 201 response from POST /machine-transfers. The backend names the S3 object and returns its public URL. */
@Serializable
data class MachineTransferResponseDto(
    val id: Int,
    @SerialName("proof_photo_url") val proofPhotoUrl: String? = null,
)

/** One row from GET /machine-transfers (and the ack response). `created_at` is an
 * ISO-8601 ...Z string. `status` is PENDING|APPROVED; `canAcknowledge` is computed by
 * the backend for the calling user (destination-warehouse technician or any supervisor). */
@Serializable
data class MachineTransferListItemDto(
    val id: Int,
    val date: String? = null,
    @SerialName("from_warehouse") val fromWarehouse: String = "",
    @SerialName("to_warehouse") val toWarehouse: String = "",
    @SerialName("machine_name") val machineName: String = "",
    val condition: String? = null,
    @SerialName("machine_code") val machineCode: String? = null,
    val reason: String? = null,
    @SerialName("authorised_person") val authorisedPerson: String? = null,
    val remarks: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("proof_photo_url") val proofPhotoUrl: String? = null,
    val status: String = "PENDING",
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("can_acknowledge") val canAcknowledge: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
)

/** JSON body for PUT /machine-transfers/{id} — edit a still-PENDING transfer (creator
 *  only). Null fields are left unchanged server-side; the proof photo isn't edited here. */
@Serializable
data class MachineTransferEditDto(
    @SerialName("from_warehouse") val fromWarehouse: String? = null,
    @SerialName("to_warehouse") val toWarehouse: String? = null,
    @SerialName("machine_name") val machineName: String? = null,
    @SerialName("asset_id") val assetId: String? = null,
    val date: String? = null,
    @SerialName("machine_code") val machineCode: String? = null,
    val condition: String? = null,
    val reason: String? = null,
    @SerialName("authorised_person") val authorisedPerson: String? = null,
    val remarks: String? = null,
)
