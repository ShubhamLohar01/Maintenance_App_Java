package com.candorfoods.factoryops.domain.model

/** A machine transfer record (doc_machine_transfer). `proofPhotoUrl` is the public S3 URL the backend returns. */
data class MachineTransfer(
    val id: Int,
    val fromWarehouse: String = "",
    val toWarehouse: String = "",
    val machineName: String = "",
    val date: String? = null,
    val condition: String? = null,
    val createdAt: String? = null,
    val proofPhotoUrl: String? = null,
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
    val date: String?,
    val machineCode: String?,
    val condition: String?,
    val reason: String?,
    val authorisedPerson: String?,
    val remarks: String?,
    val photoUri: String?,
)
