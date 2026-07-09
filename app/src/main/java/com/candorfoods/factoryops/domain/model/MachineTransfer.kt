package com.candorfoods.factoryops.domain.model

/** A machine transfer record (mt_machine_transfer). `proofPhotoUrl` is the public S3 URL
 *  the backend returns. `status` is PENDING until the receiving warehouse acknowledges
 *  receipt (-> APPROVED). `canAcknowledge` is the backend's per-user verdict for this row. */
data class MachineTransfer(
    val id: Int,
    val fromWarehouse: String = "",
    val toWarehouse: String = "",
    val machineName: String = "",
    val date: String? = null,
    val condition: String? = null,
    val machineCode: String? = null,
    val reason: String? = null,
    val authorisedPerson: String? = null,
    val remarks: String? = null,
    val createdAt: String? = null,
    val proofPhotoUrl: String? = null,
    val status: String = "PENDING",
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
    val canAcknowledge: Boolean = false,
    /** Backend verdict: this caller (the creator) may edit/delete this row (PENDING only). */
    val canEdit: Boolean = false,
)

/**
 * Form input for creating a transfer. `photoUri` is the local content:// uri of the
 * picked/captured proof image (optional) — the repository streams its bytes as a
 * multipart file part; the backend owns S3 and returns the resulting URL.
 */
data class NewMachineTransfer(
    val fromWarehouse: String,
    val toWarehouse: String,
    val machineName: String,
    /** asset_id of the picked register row — moves that asset's building server-side.
     *  Null when the name was typed manually (backend falls back to a unique-name match). */
    val assetId: String?,
    val date: String?,
    val machineCode: String?,
    val condition: String?,
    val reason: String?,
    val authorisedPerson: String?,
    val remarks: String?,
    val photoUri: String?,
)
